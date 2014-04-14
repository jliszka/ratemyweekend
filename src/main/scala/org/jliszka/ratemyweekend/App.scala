package org.jliszka.ratemyweekend

import com.foursquare.fhttp._
import com.foursquare.fhttp.FHttpRequest._
import com.foursquare.rogue.Rogue._
import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import java.io.{PrintWriter, StringWriter}
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.FSApi
import org.jliszka.ratemyweekend.model.gen.{Session, User}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId}
import org.joda.time.DateTime

object App extends FinatraServer {

  class ThrinatraController extends Controller {

    class HomeView(val userOpt: Option[User]) extends View {
      val template = "home.mustache"
    }

    def loggedInUser(request: Request): Option[User] = {
      for {
        sessionId <- request.cookies.get("sessionid").map(c => SessionId(new ObjectId(c.value)))
        session <- db.findAndUpdateOne(Q(Session).where(_.id eqs sessionId).findAndModify(_.lastUsed setTo DateTime.now))
        user <- db.fetchOne(Q(User).where(_.id eqs session.uid))
      } yield user
    }

    get("/") { request =>
      val userOpt = loggedInUser(request)
      val template = new HomeView(userOpt)
      render.view(template).toFuture
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


  val app = new ThrinatraController
  register(app)
  val oauth = new OAuthController
  register(oauth)
}
