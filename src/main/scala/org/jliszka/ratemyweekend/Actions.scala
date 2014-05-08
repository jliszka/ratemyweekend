package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.{FS, FSApi}
import org.jliszka.ratemyweekend.json.gen.{CheckinJson, CheckinResponseWrapper, CheckinsResponseWrapper,
  FriendsResponseWrapper, UserJson, UserResponseWrapper}
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

  def syncFriends(user: User): Future[Seq[UserId]] = {

    val fsFriendIdsF: Future[Seq[UserId]] = {
      FSApi(s"/v2/users/${user.id}/friends")
        .params("oauth_token" -> user.accessToken, "v" -> "20140101")
        .getFuture()
        .map(Json.parse(_, FriendsResponseWrapper).response.friends.items.map(f => UserId(f.id)))
    }

    def saveFriends(friends: Seq[UserId]): Future[Seq[UserId]] = future {
      val dbFriendIds: Set[UserId] = getFriendIds(user.id).toSet
      val fsFriendIds: Set[UserId] = db.fetch(Q(User).where(_.id in friends).select(_.id)).flatten.toSet

      val toAdd = fsFriendIds -- dbFriendIds
      val toRemove = dbFriendIds -- fsFriendIds

      toAdd.foreach(fid => makeFriends(user.id, fid))

      db.bulkDelete_!!(Q(Friend)
        .where(_.self eqs user.id)
        .and(_.other in toRemove))

      db.bulkDelete_!!(Q(Friend)
        .where(_.self in toRemove)
        .and(_.other eqs user.id))

      fsFriendIds.toSeq
    }

    for {
      fsFriendIds <- fsFriendIdsF
      dbFriendIds <- saveFriends(fsFriendIds)
    } yield dbFriendIds
  }

  def makeFriends(u1: UserId, u2: UserId) {
    db.upsertOne(Q(Friend)
      .where(_.self eqs u1)
      .and(_.other eqs u2)
      .modify(_.other setTo u2))
    db.upsertOne(Q(Friend)
      .where(_.self eqs u2)
      .and(_.other eqs u1)
      .modify(_.other setTo u1))
  }

  def getFriendIds(userId: UserId): Seq[UserId] = {
    db.fetch(Q(Friend).where(_.self eqs userId).select(_.other)).flatten
  }

  def getFriends(userId: UserId): Seq[User] = {
    val friendIds = getFriendIds(userId)
    db.fetch(Q(User).where(_.id in friendIds))
  }

  def syncCheckins(week: Week): Future[Unit] = {

    val doneUsersF: Future[Set[UserId]] = future {
      db.fetch(Q(Weekend)
        .where(_.week eqs week.week)
      .select(_.uid))
      .flatten.toSet
    }

    val usersF: Future[Seq[User]] = future {
      db.fetch(Q(User))
    }

    def syncUsers(users: Seq[User]): Future[Seq[Weekend]] = {
      future.groupedCollect(users, 5)(user => {
        val friendIds = getFriendIds(user.id)
        Actions.syncCheckins(user, friendIds, week)
      })
    }

    for {
      (doneUsers, users) <- future.join(doneUsersF, usersF)
      toSync = users.filterNot(u => doneUsers(u.id))
      _ <- syncUsers(users)
    } yield ()
  }

  def syncCheckins(user: User, friendIds: Seq[UserId], week: Week): Future[Weekend] = {

    val apiCheckinsF: Future[Seq[CheckinJson]] = FSApi(s"/v2/users/self/checkins")
      .params("oauth_token" -> user.accessToken, "v" -> "20140101")
      .params(
        "sort" -> "oldestfirst",
        "afterTimestamp" -> Util.dateToApi(week.friday5pm).toString,
        "beforeTimestamp" -> Util.dateToApi(week.monday4am).toString)
      .getFuture()
      .map(Json.parse(_, CheckinsResponseWrapper).response.checkins.items)

    def setCheckins(apiCheckins: Seq[CheckinJson]): Future[Weekend] = future {
      db.findAndUpsertOne(Q(Weekend)
        .where(_.uid eqs user.id)
        .and(_.week eqs week.week)
        .findAndModify(_.checkins setTo apiCheckins), returnNew = true)
      .get
    }

    def createRatings(weekendId: WeekendId): Future[Unit] = future {
      future.groupedCollect(friendIds, 5)(friendId =>
        createRating(ratee = user.id, rater = friendId, weekendId = weekendId, week = week))
    }

    for {
      apiCheckins <- apiCheckinsF
      weekend <- setCheckins(apiCheckins)
      _ <- syncCheckinDetails(user, weekend)
      _ <- createRatings(weekend.id)
    } yield weekend
  }

  def syncCheckinDetails(user: User, weekend: Weekend): Future[Weekend] = {
    def getCheckinDetails(checkin: CheckinJson): Future[CheckinJson] = {
      FSApi(s"/v2/checkins/${checkin.id}")
        .params("oauth_token" -> user.accessToken, "v" -> "20140101")
        .getFuture()
        .map(Json.parse(_, CheckinResponseWrapper).response.checkin)
    }

    val checkinDetailsF = future.groupedCollect(weekend.checkins, 5)(getCheckinDetails)

    def setCheckins(apiCheckins: Seq[CheckinJson]): Future[Weekend] = future {
      db.findAndUpdateOne(Q(Weekend)
        .where(_.id eqs weekend.id)
        .findAndModify(_.checkins setTo apiCheckins)
        .and(_.hasDetails setTo true), returnNew = true)
      .get
    }

    for {
      checkinDetails <- checkinDetailsF
      weekend <- setCheckins(checkinDetails)
    } yield weekend
  }

  def createRating(ratee: UserId, rater: UserId, weekendId: WeekendId, week: Week): Future[Unit] = future {
    db.upsertOne(Q(Rating)
      .where(_.rater eqs rater)
      .and(_.ratee eqs ratee)
      .and(_.weekend eqs weekendId)
      .and(_.week eqs week.week)
      .modify(_.weekend setTo weekendId))
  }

  def collectRatings(user: User, friendIds: Seq[UserId], week: Week): Future[Unit] = {
    val weekendsF: Future[Seq[(WeekendId, UserId)]] = future(Util.flatten2(db.fetch(Q(Weekend)
      .where(_.uid in friendIds)
      .and(_.week eqs week.week)
      .select(_.id, _.uid))))

    def createRatings(weekends: Seq[(WeekendId, UserId)]): Future[Seq[Unit]] = {
      future.groupedCollect(weekends, 5)(weekend => {
        val (weekendId, friendId) = weekend
        createRating(ratee = friendId, rater = user.id, weekendId = weekendId, week = week)
      })
    }

    for {
      weekends <- weekendsF
      _ <- createRatings(weekends)
    } yield ()
  }

  def weekendsToRate(user: User): Future[Seq[WeekendRating]] = future {
    val myRatings = {
      val allRatings = db.fetch(Q(Rating).where(_.rater eqs user.id).and(_.score exists false))
      if (allRatings.isEmpty) {
        db.fetch(Q(Rating).where(_.rater eqs user.id).and(_.week eqs Week.thisWeek.week))
      } else {
        allRatings
      }
    }

    val userMap = Util.idMap(User, myRatings.map(_.ratee))
    val weekendMap = Util.idMap(Weekend, myRatings.map(_.weekend))

    val ratingsMap = {
      val weekendIds = weekendMap.values.map(_.id)
      db.fetch(Q(Rating).where(_.weekend in weekendIds))
        .groupBy(_.weekend)
    }

    for {
      rating <- myRatings
      user <- userMap.get(rating.ratee)
      weekend <- weekendMap.get(rating.weekend)
      otherRatings <- ratingsMap.get(weekend.id)
    } yield WeekendRating(user, weekend, rating, computeScore(otherRatings))
  }

  def myRatings(user: User): Future[Seq[Rating]] = future {
    db.fetch(Q(Rating)
      .where(_.ratee eqs user.id)
      .and(_.score exists true))
  }

  def computeScore(ratings: Seq[Rating]): Double = {
    val scores = ratings.flatMap(_.scoreOption)
    if (scores.isEmpty) 0.0
    else scores.sum.toDouble / scores.size
  }

  def weekendsForFriend(me: User, user: User): Future[Seq[(Weekend, Double)]] = future {
    val friendIds = getFriendIds(me.id).toSet
    if (me.id == user.id || friendIds(user.id)) {
      val weekends = db.fetch(Q(Weekend).where(_.uid eqs user.id).orderDesc(_.week))
      val ratingsByWeekend = db.fetch(Q(Rating).where(_.weekend in weekends.map(_.id))).groupBy(_.weekend)
      for {
        weekend <- weekends
        ratings <- ratingsByWeekend.get(weekend.id)
      } yield (weekend, computeScore(ratings))
    } else Seq.empty
  }

  def getFriendScores(user: User): Future[(UserScores, WeekendScores)] = future {
    val friends = user +: getFriends(user.id)
    val userMap = friends.map(u => u.id -> u).toMap
    val week = Week.weekAgo(10)

    val ratings = db.fetch(Q(Rating)
      .where(_.ratee in friends.map(_.id))
      .and(_.week >= week.week)
      .and(_.score exists true))

    val userScores = UserScores(for {
      (userId, ratings) <- ratings.groupBy(_.ratee).toSeq
      user <- userMap.get(userId)
    } yield (user, computeScore(ratings)))

    val weekendScores = WeekendScores(for {
      (weekendId, ratings) <- ratings.groupBy(_.weekend).toSeq
      user <- userMap.get(ratings.head.ratee)
    } yield (user, Week(ratings.head.week), weekendId, computeScore(ratings)))

    (userScores, weekendScores)
  }

  def createSession(user: User): Future[Session] = future {
    db.save(Session.newBuilder
      .id(SessionId(new ObjectId))
      .lastUsed(DateTime.now)
      .uid(user.id)
      .result)
  }

}

case class UserScores(scores: Seq[(User, Double)])
case class WeekendScores(scores: Seq[(User, Week, WeekendId, Double)])
case class WeekendRating(user: User, weekend: Weekend, rating: Rating, score: Double)
