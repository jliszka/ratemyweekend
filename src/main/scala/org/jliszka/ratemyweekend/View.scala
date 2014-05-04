package org.jliszka.ratemyweekend

import com.google.common.base.{Function => GFunction}
import com.twitter.finatra._
import org.jliszka.ratemyweekend.json.gen.CheckinJson
import org.jliszka.ratemyweekend.model.gen.{Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId, WeekendId}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object View {
  private def fn(f: String => String): GFunction[String, String] = new GFunction[String, String] {
    def apply(s: String) = f(s)
  }

  private val dateTimeFmt = DateTimeFormat.forPattern("EEEE, MMMM d, yyyy 'at' h:mm aa z")
  private val dateFmt = DateTimeFormat.forPattern("EEEE, MMMM d")
  private val timeFmt = DateTimeFormat.forPattern("h:mm aa")

  class Index extends FixedView {
    val template = "index.html"
  }

  trait ViewUtil {
    val user: User
    val friendlyTime = fn(s => timeFmt.print(Util.apiToDate(s.toLong, user.tzOption)))
  }

  trait WeekendUtil {
    val user: User

    case class WeekendDay(week: Week, dayOfWeek: Int, checkins: Seq[CheckinJson]) {
      val date = dateFmt.print(week.friday5pm.withDayOfWeek(dayOfWeek))
    }

    def groupByDay(week: Week, checkins: Seq[CheckinJson]): Seq[WeekendDay] = {
      val dayMap = checkins
        .groupBy(c => Util.apiToDate(c.createdAt, user.tzOption).minusHours(4).getDayOfWeek)
      Seq(5, 6, 7).map(d => WeekendDay(week, d, dayMap.getOrElse(d, Seq.empty)))
    }
  }

  class Home(val user: User, val toRate: Seq[(User, Weekend)], val myRatings: Seq[Rating])
      extends FixedView with ViewUtil with WeekendUtil {
    val template = "home.html"
    val needToRate = toRate.nonEmpty
    val checkinsByDayByUser = for {
      (user, weekend) <- toRate
      week = Week(weekend.year, weekend.week)
    } yield UserWeekendDay(user, week, groupByDay(week, weekend.checkins))

    case class UserWeekendDay(user: User, week: Week, checkinsByDay: Seq[WeekendDay])
  }

  class MyWeek(val user: User, weekend: Weekend) extends FixedView with ViewUtil with WeekendUtil {
    val template = "checkins.html"
    val checkinsByDay = groupByDay(Week(weekend.year, weekend.week), weekend.checkins)
  }
}

