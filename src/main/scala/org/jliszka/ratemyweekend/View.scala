package org.jliszka.ratemyweekend

import com.google.common.base.{Function => GFunction}
import com.twitter.finatra._
import org.jliszka.ratemyweekend.json.gen.{CheckinJson, CheckinJsonProxy, UserJson}
import org.jliszka.ratemyweekend.model.gen.{Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId, WeekendId}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object View {
  private def fn(f: String => String): GFunction[String, String] = new GFunction[String, String] {
    def apply(s: String) = f(s)
  }

  class Index extends FixedView {
    val template = "index.html"
  }

  trait ViewUtil {
    val user: User
    val friendlyTime = fn(s => Util.timeFmt.print(Util.apiToDate(s.toLong, user.tzOption)))
  }

  trait WeekendUtil {
    val user: User


    class CheckinProxy(override val underlying: CheckinJson) extends CheckinJsonProxy {
      val withFriends: Seq[UserJson] = {
        val mentioned: Seq[UserJson] = underlying.withOption.getOrElse(Seq.empty)
        val overlaps: Seq[UserJson] = underlying.overlapsOption.map(os => os.items.flatMap(_.userOption)).getOrElse(Seq.empty)
        (mentioned ++ overlaps).groupBy(_.id).toSeq.map(_._2).flatMap(_.headOption)
      }
    }

    case class WeekendDay(week: Week, dayOfWeek: Int, cs: Seq[CheckinJson]) {
      val date = Util.dateFmt.print(week.friday5pm.withDayOfWeek(dayOfWeek))
      val checkins = cs.map(c => new CheckinProxy(c))
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

  class Leaderboard(val user: User, userScores: UserScores, weekendScores: WeekendScores) extends FixedView {
    val template = "leaderboard.html"
    case class UserScore(rank: Int, user: User, score: String)
    case class WeekendScore(rank: Int, user: User, week: Week, weekendId: WeekendId, score: String)

    val userLeaderboard = userScores.scores
      .sortBy(_._2).reverse
      .zipWithIndex.map{ case ((user, score), idx) => UserScore(idx+1, user, "%.1f".format(score)) }
    val weekendLeaderboard = weekendScores.scores
      .sortBy(_._4).reverse.take(10)
      .zipWithIndex.map{ case ((user, week, weekend, score), idx) => WeekendScore(idx+1, user, week, weekend, "%.1f".format(score)) }
  }

  class Profile(val me: User, val user: User, weekends: Seq[(Weekend, Double)]) extends FixedView with WeekendUtil {
    val template = "profile.html"
    case class CheckinsByDay(weekend: Weekend, week: Week, score: String, checkinsByDay: Seq[WeekendDay])
    val checkinsByDayByWeek = for {
      (weekend, score) <- weekends
    } yield CheckinsByDay(weekend, Week(weekend.week), "%.1f".format(score), groupByDay(weekend))
  }
}

