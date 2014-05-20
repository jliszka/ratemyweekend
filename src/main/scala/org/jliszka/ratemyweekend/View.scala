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

  trait WeekendUtil {
    class CheckinProxy(override val underlying: CheckinJson) extends CheckinJsonProxy {
      val withFriends: Seq[UserJson] = {
        val mentioned: Seq[UserJson] = underlying.withOption.getOrElse(Seq.empty)
        val overlaps: Seq[UserJson] = underlying.overlapsOption.map(os => os.items.flatMap(_.userOption)).getOrElse(Seq.empty)
        (mentioned ++ overlaps).groupBy(_.id).toSeq.map(_._2).flatMap(_.headOption)
      }
      val timeStr = Util.timeFmt.print(Util.apiToDate(underlying.createdAt, underlying.timeZoneOffset))
      val hasLikesOrComments = underlying.likesOption.exists(_.count > 0) || underlying.commentsOption.exists(_.count > 0)
    }

    case class WeekendDay(week: Week, dayOfWeek: Int, cs: Seq[CheckinJson]) {
      val date = Util.dateFmt.print(week.friday5pm.withDayOfWeek(dayOfWeek))
      val checkins = cs.map(c => new CheckinProxy(c))
    }

    def groupByDay(weekend: Weekend): Seq[WeekendDay] = {
      val week = Week(weekend.week)
      val checkins = weekend.checkins
      val dayMap = checkins
        .groupBy(c => Util.apiToDate(c.createdAt, c.timeZoneOffset).minusHours(4).getDayOfWeek)
      Seq(5, 6, 7).map(d => WeekendDay(week, d, dayMap.getOrElse(d, Seq.empty)))
    }
  }

  class Rate(val user: User, toRate: Seq[WeekendRating], howManyOpt: Option[Int] = None)
      extends FixedView with WeekendUtil {
    val template = "rate.html"
    val checkinsByDayByUser = (for {
      r <- howManyOpt.map(howMany => toRate.take(howMany)).getOrElse(toRate)
    } yield UserWeekendDay(r.rating, r.user, Week(r.weekend.week), groupByDay(r.weekend)))

    val ratingOptions = 1 to 10

    case class UserWeekendDay(rating: Rating, user: User, week: Week, checkinsByDay: Seq[WeekendDay])
  }

  class Leaderboard(val user: User, userScores: UserScores, weekendScores: WeekendScores) extends FixedView {
    val template = "leaderboard.html"
    case class UserScoreRank(rank: Int, userScore: UserScore)
    case class WeekendScoreRank(rank: Int, weekendScore: WeekendScore)

    val userLeaderboard = userScores.scores
      .sortBy(_.score.average).reverse.take(10)
      .zipWithIndex.map{ case (userScore, idx) => UserScoreRank(idx+1, userScore) }
    val allTimeLeaderboard = weekendScores.scores
      .sortBy(_.score.average).reverse.take(10)
      .zipWithIndex.map{ case (weekendScore, idx) => WeekendScoreRank(idx+1, weekendScore) }
    val thisWeekLeaderboard = weekendScores.scores
      .filter(_.week.week == Week.thisWeek.week)
      .sortBy(_.score.average).reverse.take(10)
      .zipWithIndex.map{ case (weekendScore, idx) => WeekendScoreRank(idx+1, weekendScore) }
  }

  class Profile(val me: User, val user: User, weekends: Seq[(Weekend, Score)]) extends FixedView with WeekendUtil {
    val template = "profile.html"
    case class CheckinsByDay(weekend: Weekend, week: Week, score: Score, checkinsByDay: Seq[WeekendDay])
    val checkinsByDayByWeek = for {
      (weekend, score) <- weekends
    } yield CheckinsByDay(weekend, Week(weekend.week), score, groupByDay(weekend))
  }

  class ThisWeek(val user: User, val week: Week, weekends: Seq[(User, Weekend, Score)]) extends FixedView with WeekendUtil {
    val template = "thisweek.html"
    case class WeekendsByUser(user: User, weekend: Weekend, score: Score, checkinsByDay: Seq[WeekendDay])
    val checkinsByDayByUser = for {
      (user, weekend, score) <- weekends
    } yield WeekendsByUser(user, weekend, score, groupByDay(weekend))
  }

  class UserNotFound(val userId: UserId) extends FixedView {
    val template = "usernotfound.html"
  }
}

