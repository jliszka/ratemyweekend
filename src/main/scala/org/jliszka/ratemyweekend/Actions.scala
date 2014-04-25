package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.{FS, FSApi}
import org.jliszka.ratemyweekend.json.gen.{CheckinJson, CheckinsResponseWrapper, FriendsResponseWrapper, UserJson, UserResponseWrapper}
import org.jliszka.ratemyweekend.model.gen.{Friend, Rating, Session, User, Weekend}
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
        .andOpt(fsUser.photoOption.map(p => p.prefix + "100x100" + p.suffix))(_.photo setTo _),
      returnNew = true).get
    }

    for {
      fsUser <- fsUserF
      user <- userF(fsUser)
      _ <- syncFriends(user)
    } yield user
  }

  def syncFriends(user: User): Future[Unit] = {

    val fsFriendsF: Future[Seq[UserId]] = {
      FSApi(s"/v2/users/${user.id}/friends")
        .params("oauth_token" -> user.accessToken, "v" -> "20140101")
        .getFuture()
        .map(Json.parse(_, FriendsResponseWrapper).response.friends.friends.map(f => UserId(f.id)))
    }

    def setFriends(friends: Seq[UserId]): Future[Unit] = future {
      val dbFriendIds = db.fetch(Q(Friend).where(_.self eqs user.id)).map(_.other).toSet
      val fsFriendIds = friends.toSet

      val toAdd = fsFriendIds -- dbFriendIds
      val toRemove = dbFriendIds -- fsFriendIds

      toAdd.foreach(fid => {
        db.save(Friend.newBuilder
          .id(FriendId(new ObjectId))
          .self(user.id)
          .other(fid)
          .result)
        db.save(Friend.newBuilder
          .id(FriendId(new ObjectId))
          .self(fid)
          .other(user.id)
          .result)
      })

      db.bulkDelete_!!(Q(Friend)
        .where(_.self eqs user.id)
        .and(_.other in toRemove))

      db.bulkDelete_!!(Q(Friend)
        .where(_.self in toRemove)
        .and(_.other eqs user.id))
    }

    for {
      fsFriends <- fsFriendsF
      _ <- setFriends(fsFriends)
    } yield ()
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

    def syncUsers(users: Seq[User]): Future[Seq[Unit]] = {
      future.groupedCollect(users, 5)(user => Actions.syncCheckins(user, week))
    }

    for {
      doneUsers <- doneUsersF
      users <- usersF
      toSync = users.filterNot(u => doneUsers(u.id))
      _ <- syncUsers(users)
    } yield ()
  }

  def syncCheckins(user: User, week: Week): Future[Unit] = {

    val apiCheckinsF: Future[Seq[CheckinJson]] = FSApi(s"/v2/users/self/checkins")
      .params("oauth_token" -> user.accessToken, "v" -> "20140101")
      .params(
        "sort" -> "oldestfirst",
        "afterTimestamp" -> Util.dateToApi(week.friday5pm).toString,
        "beforeTimestamp" -> Util.dateToApi(week.monday4am).toString)
      .getFuture()
      .map(Json.parse(_, CheckinsResponseWrapper).response.checkins.items)

    def setCheckins(apiCheckins: Seq[CheckinJson]): Future[Option[WeekendId]] = future {
      db.findAndUpsertOne(Q(Weekend)
        .where(_.uid eqs user.id)
        .and(_.year eqs week.year)
        .and(_.week eqs week.week)
        .findAndModify(_.checkins setTo apiCheckins), returnNew = true)
      .map(_.id)
    }

    def createRatings(weekendIdOpt: Option[WeekendId]): Future[Unit] = future {
      weekendIdOpt.foreach(weekendId => {
        val friendIds = db.fetch(Q(Friend).where(_.self eqs user.id).select(_.other)).flatten
        friendIds.foreach(friendId => {
          db.upsertOne(Q(Rating)
            .where(_.rater eqs friendId)
            .and(_.ratee eqs user.id)
            .and(_.weekend eqs weekendId)
            .modify(_.weekend setTo weekendId))
        })
      })
    }

    for {
      apiCheckins <- apiCheckinsF
      weekendIdOpt <- setCheckins(apiCheckins)
      _ <- createRatings(weekendIdOpt)
    } yield ()
  }

  def createSession(user: User): Future[Session] = future {
    db.save(Session.newBuilder
      .id(SessionId(new ObjectId))
      .lastUsed(DateTime.now)
      .uid(user.id)
      .result)
  }

}