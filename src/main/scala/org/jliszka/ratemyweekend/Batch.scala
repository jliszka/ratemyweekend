package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.finatra._
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.model.gen.{Friend, Photo, Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{FriendId, SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object Batch {

  def resetRatings() {
    if ("development".equals(config.env())) {
      db.updateMulti(Q(Rating).where(_.rater eqs UserId("364701")).modify(_.score unset))
    }
  }

  def fixWeekField() {
    for {
      r <- db.fetch(Q(Rating))
      w <- db.fetchOne(Q(Weekend).where(_.id eqs r.weekend))  
    } {
      db.updateOne(Q(Rating).where(_.id eqs r.id).modify(_.week setTo w.week))
    }
  }

  def fixCheckinDetails() {
    for {
      u <- db.fetch(Q(User))
      w <- db.fetch(Q(Weekend).where(_.uid eqs u.id))
      if !w.hasDetails
    } {
      Actions.syncCheckinDetails(u, w)()
    }
  }

  def ensureIndexes() {
    db.ensureIndexes(Q(Session))
    db.ensureIndexes(Q(Weekend))
    db.ensureIndexes(Q(Rating))
    db.ensureIndexes(Q(Friend))
  }

  def updateSchema(version: Int) {
    version match {
      case 1 => fixWeekField()
      case 2 => fixCheckinDetails()
      case 3 => ensureIndexes()
    }
  }
}