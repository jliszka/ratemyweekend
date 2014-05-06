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

    def groupByDay(weekend: Weekend): Seq[WeekendDay] = {
      val week = Week(weekend.week)
      val checkins = weekend.checkins
      val dayMap = checkins
        .groupBy(c => Util.apiToDate(c.createdAt, user.tzOption).minusHours(4).getDayOfWeek)
      Seq(5, 6, 7).map(d => WeekendDay(week, d, dayMap.getOrElse(d, Seq.empty)))
    }
  }

  class Home(val user: User, val toRate: Seq[(Rating, User, Weekend)], val myRatings: Seq[Rating])
      extends FixedView with ViewUtil with WeekendUtil {
    val template = "home.html"
    val needToRate = toRate.exists(_._1.scoreOption.isEmpty)
    val checkinsByDayByUser = for {
      (rating, user, weekend) <- toRate
      week = Week(weekend.week)
    } yield UserWeekendDay(rating, user, week, groupByDay(weekend))
    val ratingOptions = 1 to 10

    case class UserWeekendDay(rating: Rating, user: User, week: Week, checkinsByDay: Seq[WeekendDay])
  }

  class Leaderboard(val user: User, scores: Seq[(User, Double)]) extends FixedView {
    val template = "leaderboard.html"
    case class UserScore(user: User, score: String)
    val leaderboard = scores.sortBy(_._2).reverse.map{ case (u, s) => UserScore(u, "%.1f".format(s)) }
  }

  class Profile(val me: User, val user: User, weekends: Seq[Weekend]) extends FixedView with WeekendUtil {
    val template = "profile.html"
    case class CheckinsByDay(checkinsByDay: Seq[WeekendDay])
    val checkinsByDayByWeek = weekends.map(weekend => CheckinsByDay(groupByDay(weekend)))
  }
}

