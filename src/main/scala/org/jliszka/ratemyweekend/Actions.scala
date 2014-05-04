package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.{FS, FSApi}
import org.jliszka.ratemyweekend.json.gen.{CheckinJson, CheckinsResponseWrapper, FriendsResponseWrapper, UserJson, UserResponseWrapper}
import org.jliszka.ratemyweekend.model.gen.{Friend, Photo, Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{FriendId, SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object Actions {

  def createUser(token: String): Future[User] = {
    val fsUserF: Future[UserJson] = {
      FSApi("/v2/users/self")
        .params("oauth_token" -> token, "v" -> "20140101")
        .getFuture()
        .map(Json.parse(_, UserResponseWrapper).response.user)
    }

    def userF(fsUser: UserJson): Future[User] = future {
      db.findAndUpsertOne(Q(User)
        .where(_.id eqs UserId(fsUser.id))
        .findAndModify(_.accessToken setTo token)
        .andOpt(fsUser.firstNameOption)(_.firstName setTo _)
        .andOpt(fsUser.lastNameOption)(_.lastName setTo _)
        .andOpt(fsUser.photoOption.map(p => Photo(p.prefix, p.suffix)))(_.photo setTo _),
      returnNew = true).get
    }

    for {
      fsUser <- fsUserF
      user <- userF(fsUser)
      _ <- syncUser(user)
    } yield user
  }

  def syncUser(user: User): Future[Unit] = {
    for {
      friendIds <- syncFriends(user)
      (weekendId, _) <- future.join(
        syncCheckins(user, friendIds, Week.thisWeek),
        collectRatings(user, friendIds, Week.thisWeek))
    } yield ()
  }

  def resetDB() {
    db.bulkDelete_!!(Q(Rating))
    db.bulkDelete_!!(Q(Weekend))
    db.bulkDelete_!!(Q(Friend))
    val uid = UserId("364701")
    makeFriends(uid, uid)
    val u = db.fetchOne(Q(User).where(_.id eqs uid)).get
    (3 to 0 by -1).foreach(n => {
      val week = Week.weekAgo(n)
      syncCheckins(u, List(u.id), week)()
      collectRatings(u, List(u.id), week)()
    })
  }

  def syncFriends(user: User): Future[Seq[UserId]] = {

    val fsFriendIdsF: Future[Seq[UserId]] = {
      FSApi(s"/v2/users/${user.id}/friends")
        .params("oauth_token" -> user.accessToken, "v" -> "20140101")
        .getFuture()
        .map(Json.parse(_, FriendsResponseWrapper).response.friends.items.map(f => UserId(f.id)))
    }

    def saveFriends(friends: Seq[UserId]): Future[Unit] = future {
      val dbFriendIds = db.fetch(Q(Friend).where(_.self eqs user.id)).map(_.other).toSet
      val fsFriendIds = db.fetch(Q(User).where(_.id in friends).select(_.id)).flatten.toSet

      val toAdd = fsFriendIds -- dbFriendIds
      val toRemove = dbFriendIds -- fsFriendIds

      toAdd.foreach(fid => makeFriends(user.id, fid))

      db.bulkDelete_!!(Q(Friend)
        .where(_.self eqs user.id)
        .and(_.other in toRemove))

      db.bulkDelete_!!(Q(Friend)
        .where(_.self in toRemove)
        .and(_.other eqs user.id))
    }

    for {
      fsFriendIds <- fsFriendIdsF
      _ <- saveFriends(fsFriendIds)
    } yield fsFriendIds
  }

  def makeFriends(u1: UserId, u2: UserId) {
    db.insert(Friend.newBuilder
      .id(FriendId(new ObjectId))
      .self(u1)
      .other(u2)
      .result)
    db.insert(Friend.newBuilder
      .id(FriendId(new ObjectId))
      .self(u2)
      .other(u1)
      .result)
  }

  def syncCheckins(week: Week): Future[Unit] = {

    val doneUsersF: Future[Set[UserId]] = future {
      db.fetch(Q(Weekend)
        .where(_.year eqs week.year)
        .and(_.week eqs week.week)
      .select(_.uid))
      .flatten.toSet
    }

    val usersF: Future[Seq[User]] = future {
      db.fetch(Q(User))
    }

    def syncUsers(users: Seq[User]): Future[Seq[WeekendId]] = {
      future.groupedCollect(users, 5)(user => {
        val friendIds = db.fetch(Q(Friend).where(_.self eqs user.id).select(_.other)).flatten
        Actions.syncCheckins(user, friendIds, week)
      })
    }

    for {
      (doneUsers, users) <- future.join(doneUsersF, usersF)
      toSync = users.filterNot(u => doneUsers(u.id))
      _ <- syncUsers(users)
    } yield ()
  }

  def syncCheckins(user: User, friendIds: Seq[UserId], week: Week): Future[WeekendId] = {

    val apiCheckinsF: Future[Seq[CheckinJson]] = FSApi(s"/v2/users/self/checkins")
      .params("oauth_token" -> user.accessToken, "v" -> "20140101")
      .params(
        "sort" -> "oldestfirst",
        "afterTimestamp" -> Util.dateToApi(week.friday5pm).toString,
        "beforeTimestamp" -> Util.dateToApi(week.monday4am).toString)
      .getFuture()
      .map(Json.parse(_, CheckinsResponseWrapper).response.checkins.items)

    def setCheckins(apiCheckins: Seq[CheckinJson]): Future[WeekendId] = future {
      db.findAndUpsertOne(Q(Weekend)
        .where(_.uid eqs user.id)
        .and(_.year eqs week.year)
        .and(_.week eqs week.week)
        .findAndModify(_.checkins setTo apiCheckins), returnNew = true)
      .get.id
    }

    def createRatings(weekendId: WeekendId): Future[Unit] = future {
      future.groupedCollect(friendIds, 5)(friendId =>
        createRating(ratee = user.id, rater = friendId, weekendId = weekendId))
    }

    for {
      apiCheckins <- apiCheckinsF
      weekendId <- setCheckins(apiCheckins)
      _ <- createRatings(weekendId)
    } yield weekendId
  }

  def createRating(ratee: UserId, rater: UserId, weekendId: WeekendId): Future[Unit] = future {
    db.upsertOne(Q(Rating)
      .where(_.rater eqs rater)
      .and(_.ratee eqs ratee)
      .and(_.weekend eqs weekendId)
      .modify(_.weekend setTo weekendId))
  }

  def collectRatings(user: User, friendIds: Seq[UserId], week: Week): Future[Unit] = {
    val weekendsF: Future[Seq[(WeekendId, UserId)]] = future(Util.flatten2(db.fetch(Q(Weekend)
      .where(_.uid in friendIds)
      .and(_.year eqs week.year)
      .and(_.week eqs week.week)
      .select(_.id, _.uid))))

    def createRatings(weekends: Seq[(WeekendId, UserId)]): Future[Seq[Unit]] = {
      future.groupedCollect(weekends, 5)(weekend => {
        val (weekendId, friendId) = weekend
        createRating(ratee = friendId, rater = user.id, weekendId = weekendId)
      })
    }

    for {
      weekends <- weekendsF
      _ <- createRatings(weekends)
    } yield ()
  }

  def weekendsToRate(user: User): Future[Seq[(User, Weekend)]] = future {
    val ratings = db.fetch(Q(Rating)
      .where(_.rater eqs user.id)
      .and(_.score exists false))

    val userMap = Util.idMap(User, ratings.map(_.ratee))
    val weekendMap = Util.idMap(Weekend, ratings.map(_.weekend))

    for {
      rating <- ratings
      user <- userMap.get(rating.ratee)
      weekend <- weekendMap.get(rating.weekend)
    } yield (user, weekend)
  }

  def myRatings(user: User): Future[Seq[Rating]] = future {
    db.fetch(Q(Rating)
      .where(_.ratee eqs user.id)
      .and(_.score exists true))
  }

  def createSession(user: User): Future[Session] = future {
    db.save(Session.newBuilder
      .id(SessionId(new ObjectId))
      .lastUsed(DateTime.now)
      .uid(user.id)
      .result)
  }

}