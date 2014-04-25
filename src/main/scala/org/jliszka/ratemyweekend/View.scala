package org.jliszka.ratemyweekend

import com.google.common.base.{Function => GFunction}
import com.twitter.finatra._
import org.jliszka.ratemyweekend.json.gen.CheckinJson
import org.jliszka.ratemyweekend.model.gen.{Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId, WeekendId}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object View {
  def fn(f: String => String): GFunction[String, String] = new GFunction[String, String] {
    def apply(s: String) = f(s)
  }

  private val dateTimeFmt = DateTimeFormat.forPattern("EEEE, MMMM d, yyyy 'at' h:mm aa z")
  private val dateFmt = DateTimeFormat.forPattern("EEEE, MMMM d")
  private val timeFmt = DateTimeFormat.forPattern("h:mm aa")

  class Home(val userOpt: Option[User]) extends View {
    val template = "home.mustache"
  }

  class Checkins(val user: User, weekend: Weekend) extends View {
    val template = "checkins.mustache"
    val friendlyTime = fn(s => timeFmt.print(Util.apiToDate(s.toLong, user.tzOption)))

    val friday = new DateTime(weekend.year, 1, 1, 0, 0, 0, 0).withWeekOfWeekyear(weekend.week).withDayOfWeek(5)

    case class WeekendDay(dayOfWeek: Int, checkins: Seq[CheckinJson]) {
      val date = dateFmt.print(friday.withDayOfWeek(dayOfWeek))
    }

    def groupByDay(checkins: Seq[CheckinJson]): Seq[WeekendDay] = {
      checkins
        .groupBy(c => Util.apiToDate(c.createdAt, user.tzOption).minusHours(4).getDayOfWeek)
        .toSeq
        .map{ case (dayOfWeek, checkins) => WeekendDay(dayOfWeek, checkins) }
        .sortBy(_.dayOfWeek)
    }

    val checkinsByDay = groupByDay(weekend.checkins)
  }
}