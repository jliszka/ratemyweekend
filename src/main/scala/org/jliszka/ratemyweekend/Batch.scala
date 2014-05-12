package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.model.gen.{Friend, Photo, Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{FriendId, SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object Batch {

  def userStats() {
    val ratingsMap = db.fetch(Q(Rating)).groupBy(_.rater)
    val users = db.fetch(Q(User))
    for {
      user <- users
      firstName <- user.firstNameOption
      ratings <- ratingsMap.get(user.id)
    } {
      val (rated, notrated) = ratings.partition(_.scoreIsSet)
      println(s"$firstName\t${rated.size}\t${notrated.size}")
    }
  }

  def resetRatings() {
    if (Util.isDevelopment) {
      db.updateMulti(Q(Rating)
        .where(_.rater eqs UserId("364701"))
        .and(_.week eqs Week.thisWeek.week)
        .modify(_.score unset))
    }
  }

  def syncCheckinDetails() {
    for {
      u <- db.fetch(Q(User))
      w <- db.fetch(Q(Weekend).where(_.uid eqs u.id))
      if !w.hasDetails
    } {
      Actions.syncCheckinDetails(u, w)()
    }
  }

  def updateUserDetails() {
    db.fetch(Q(User)).foreach(user => Actions.createUser(user.accessToken)())
  }

  def ensureIndexes() {
    db.ensureIndexes(Q(User))
    db.ensureIndexes(Q(Session))
    db.ensureIndexes(Q(Weekend))
    db.ensureIndexes(Q(Rating))
    db.ensureIndexes(Q(Friend))
  }

  def updateSchema(version: Int) {
    version match {
      case 2 => syncCheckinDetails()
      case 3 => ensureIndexes()
      case 4 => updateUserDetails()
    }
  }
}