package org.jliszka.ratemyweekend

import com.foursquare.rogue.Rogue._
import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import java.io.{PrintWriter, StringWriter}
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.FSApi
import org.jliszka.ratemyweekend.json.gen.{CheckinsResponseWrapper, CheckinJson}
import org.jliszka.ratemyweekend.model.gen.{Session, User}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId}
import org.joda.time.{DateTime, DateTimeZone}

object App extends FinatraServer {

  class ThrinatraController extends Controller {

    class HomeView(val userOpt: Option[User]) extends View {
      val template = "home.mustache"
    }

    class CheckinsView(val user: User, val checkins: Seq[CheckinJson]) extends View {
      val template = "checkins.mustache"
    }

    def loggedInUser(request: Request): Option[User] = {
      for {
        sessionId <- request.cookies.get("sessionid").map(c => SessionId(new ObjectId(c.value)))
        session <- db.findAndUpdateOne(Q(Session).where(_.id eqs sessionId).findAndModify(_.lastUsed setTo DateTime.now))
        user <- db.fetchOne(Q(User).where(_.id eqs session.uid))
      } yield user
    }

    def dateToApi(d: DateTime) = d.getMillis / 1000
    def apiToDate(s: Long) = new DateTime(s * 1000)

    get("/") { request =>
      val userOpt = loggedInUser(request)
      val template = new HomeView(userOpt)
      render.view(template).toFuture
    }

    get("/checkins") { request =>
      loggedInUser(request) match {
        case None => redirect("/").toFuture
        case Some(user) => {

          val tz = DateTimeZone.forID("America/New_York")
          val friday5pm = DateTime.now.withZone(tz).minusHours(4).withDayOfWeek(1).minusDays(3).withTime(17, 0, 0, 0)
          val monday4am = friday5pm.plusDays(3).withTime(4, 0, 0, 0)

          val checkinsF: Future[Seq[CheckinJson]] = FSApi(s"/v2/users/self/checkins")
            .params("oauth_token" -> user.accessToken, "v" -> "20140101")
            .params(
              "sort" -> "oldestfirst",
              "afterTimestamp" -> dateToApi(friday5pm).toString,
              "beforeTimestamp" -> dateToApi(monday4am).toString)
            .getFuture()
            .map(Json.parse(_, CheckinsResponseWrapper).response.checkins.items)

          for {
            checkins <- checkinsF
          } yield {
            val template = new CheckinsView(user, checkins)
            render.view(template)
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
