package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.model.gen.{Friend, Photo, Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{FriendId, SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object Batch {
  def resetDB() {
    db.bulkDelete_!!(Q(Rating))
    db.bulkDelete_!!(Q(Weekend))
    db.bulkDelete_!!(Q(Friend))
    val uid = UserId("364701")
    Actions.makeFriends(uid, uid)
    val u = db.fetchOne(Q(User).where(_.id eqs uid)).get
    (3 to 0 by -1).foreach(n => {
      val week = Week.weekAgo(n)
      Actions.syncCheckins(u, List(u.id), week)()
      Actions.collectRatings(u, List(u.id), week)()
    })
  }

  def fixWeekField() {
    for {
      r <- db.fetch(Q(Rating))
      w <- db.fetchOne(Q(Weekend).where(_.id eqs r.weekend))  
    } {
      db.updateOne(Q(Rating).where(_.id eqs r.id).modify(_.week setTo w.week))
      db.updateOne(Q(Weekend).where(_.id eqs w.id).modify(_.year unset))
    }
  }

  def updateSchema(version: Int) {
    if (version >= 1) {
      fixWeekField()
    }
  }
}