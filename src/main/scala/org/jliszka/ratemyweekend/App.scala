package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.google.common.base.{Function => GFunction}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import java.io.{PrintWriter, StringWriter}
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.FSApi
import org.jliszka.ratemyweekend.json.gen.{CheckinsResponseWrapper, CheckinJson}
import org.jliszka.ratemyweekend.model.gen.{Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object App extends FinatraServer {

  class ThrinatraController extends Controller {

    val tz = DateTimeZone.forID("America/New_York")

    class HomeView(val userOpt: Option[User]) extends View {
      val template = "home.mustache"
    }

    private val dateTimeFmt = DateTimeFormat.forPattern("EEEE, MMMM d, yyyy 'at' h:mm aa z")
    private val dateFmt = DateTimeFormat.forPattern("EEEE, MMMM d")
    private val timeFmt = DateTimeFormat.forPattern("h:mm aa")

    def fn(f: String => String): GFunction[String, String] = new GFunction[String, String] {
      def apply(s: String) = f(s)
    }

    class CheckinsView(val user: User, weekend: Weekend) extends View {
      val template = "checkins.mustache"
      val friendlyTime = fn(s => timeFmt.print(apiToDate(s.toLong)))

      val friday = new DateTime(weekend.year, 1, 1, 0, 0, 0, 0).withWeekOfWeekyear(weekend.week).withDayOfWeek(5)

      case class WeekendDay(dayOfWeek: Int, checkins: Seq[CheckinJson]) {
        val date = dateFmt.print(friday.withDayOfWeek(dayOfWeek))
      }

      def groupByDay(checkins: Seq[CheckinJson]): Seq[WeekendDay] = {
        checkins
          .groupBy(c => apiToDate(c.createdAt).minusHours(4).getDayOfWeek)
          .toSeq
          .map{ case (dayOfWeek, checkins) => WeekendDay(dayOfWeek, checkins) }
          .sortBy(_.dayOfWeek)
      }

      val checkinsByDay = groupByDay(weekend.checkins)
    }

    def loggedInUser(request: Request): Option[User] = {
      for {
        sessionId <- request.cookies.get("sessionid").map(c => SessionId(new ObjectId(c.value)))
        session <- db.findAndUpdateOne(Q(Session).where(_.id eqs sessionId).findAndModify(_.lastUsed setTo DateTime.now))
        user <- db.fetchOne(Q(User).where(_.id eqs session.uid))
      } yield user
    }

    def dateToApi(d: DateTime) = d.getMillis / 1000
    def apiToDate(s: Long) = new DateTime(s * 1000).withZone(tz)

    get("/") { request =>
      val userOpt = loggedInUser(request)
      val template = new HomeView(userOpt)
      render.view(template).toFuture
    }

    get("/sync") { request =>
      val friday5pm = DateTime.now.withZone(tz).minusHours(4).withDayOfWeek(1).minusDays(3).withTime(17, 0, 0, 0)
      val monday4am = friday5pm.plusDays(3).withTime(4, 0, 0, 0)

      val year = friday5pm.getYear
      val week = friday5pm.getWeekOfWeekyear

      val weekendQuery = Q(Weekend)
        .where(_.year eqs year)
        .and(_.week eqs week)

      val doneUsers: Set[UserId] = db.fetch(weekendQuery.select(_.uid)).flatten.toSet

      db.fetch(Q(User)).filterNot(u => doneUsers(u.id)).foreach(user => {
        val query = weekendQuery.where(_.uid eqs user.id)

        val apiCheckinsF: Future[Seq[CheckinJson]] = FSApi(s"/v2/users/self/checkins")
          .params("oauth_token" -> user.accessToken, "v" -> "20140101")
          .params(
            "sort" -> "oldestfirst",
            "afterTimestamp" -> dateToApi(friday5pm).toString,
            "beforeTimestamp" -> dateToApi(monday4am).toString)
          .getFuture()
          .map(Json.parse(_, CheckinsResponseWrapper).response.checkins.items)

        for {
          apiCheckins <- apiCheckinsF
        } {
          db.upsertOne(query.modify(_.checkins setTo apiCheckins))
        }
      })

      render.status(200).plain("ok").toFuture
    }

    get("/checkins") { request =>
      loggedInUser(request) match {
        case None => redirect("/").toFuture
        case Some(user) => Future {

          val weekendOpt = {
            db.fetchOne(Q(Weekend)
              .where(_.uid eqs user.id)
              .orderDesc(_.id))
          }

          weekendOpt match {
            case None => redirect("/")
            case Some(weekend) => {
              val template = new CheckinsView(user, weekend)
              render.view(template)
            }
          }
        }
      }
    }

    error { request =>
      request.error match {
        case Some(e: Exception) => {
          val writer = new StringWriter
          val printWriter = new PrintWriter(writer)
          e.printStackTrace(printWriter)
          printWriter.flush()
          val stackTrace = writer.toString
          render.status(500).plain(e.getMessage + "\n" + stackTrace).toFuture
        }
        case _ => render.status(500).plain("Something went wrong!").toFuture
      }
    }

    notFound { request =>
      render.status(404).plain("not found yo").toFuture
    }
  }


  register(new ThrinatraController)
  register(new OAuthController)
}
