package org.jliszka.ratemyweekend

import com.foursquare.fhttp._
import com.foursquare.fhttp.FHttpRequest._
import com.foursquare.rogue.Rogue._
import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.conversions.time._
import com.twitter.util.Future
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.json.gen.{AccessTokenResponse, UserJson, UserResponseWrapper}
import org.jliszka.ratemyweekend.model.gen.{Session, User}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId}
import org.joda.time.DateTime

object App extends FinatraServer {
  val ClientId = "YW2OX3IMFPZ1RNZZC5QSHCFNQYKHXQTJKMHNTSK32USWXSQU"
  val ClientSecret = "01EBTETQJJMWI2W4OZWGKNGHICOTWMW1OSSTDJ3RYEGQ3RTG"
  //val OAuthCallback = "http://ratemyweekend.herokuapp.com/oauth_callback"
  val OAuthCallback = "http://localhost.com:7070/oauth_callback"

  val FS = new FHttpClient("foursquare", "foursquare.com:443",
     ClientBuilder()
      .codec(Http())
      .tls("foursquare.com")
      .tcpConnectTimeout(1.second)
      .hostConnectionLimit(1)
      .retries(0))

  val FSApi = new FHttpClient("foursquare-api", "api.foursquare.com:443",
     ClientBuilder()
      .codec(Http())
      .tls("api.foursquare.com")
      .tcpConnectTimeout(1.second)
      .hostConnectionLimit(1)
      .retries(0))


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

    get("/authenticate") { request =>
      val uri = s"https://foursquare.com/oauth2/authenticate?client_id=$ClientId&response_type=code&redirect_uri=$OAuthCallback"
      redirect(uri, permanent = false).toFuture
    }

    get("/oauth_callback") { request =>
      val code = request.params.getOrElse("code", ???)

      val accessTokenF: Future[AccessTokenResponse] = {
        FS("/oauth2/access_token")
          .params(
            "client_id" -> ClientId,
            "client_secret" -> ClientSecret,
            "grant_type" -> "authorization_code",
            "redirect_uri" -> OAuthCallback,
            "code" -> code)
          .postFuture()
          .map(Json.parse(_, AccessTokenResponse))
      }

      def fsUserF(token: AccessTokenResponse): Future[UserJson] = {
        FSApi("/v2/users/self")
          .params("oauth_token" -> token.access_token, "v" -> "20140101")
          .getFuture()
          .map(Json.parse(_, UserResponseWrapper).response.user)
      }

      def userF(token: AccessTokenResponse, fsUser: UserJson): Future[User] = Future {
        db.findAndUpsertOne(Q(User)
          .where(_.id eqs UserId(fsUser.id))
          .findAndModify(_.accessToken setTo token.access_token),
        returnNew = true).get
      }

      def sessionF(user: User): Future[Session] = Future {
        db.save(Session.newBuilder
          .id(SessionId(new ObjectId))
          .lastUsed(DateTime.now)
          .uid(user.id)
          .result)
      }

      for {
        token <- accessTokenF
        fsUser <- fsUserF(token)
        user <- userF(token, fsUser)
        session <- sessionF(user)
      } yield {
        redirect("/", permanent = true).cookie("sessionid", session.id.toString)
      }
    }

    error { request =>
      request.error match {
        case Some(e: Exception) => render.status(500).plain(e.getMessage + "\n" + e.getStackTrace).toFuture
        case _ => render.status(500).plain("Something went wrong!").toFuture
      }
    }

    notFound { request =>
      render.status(404).plain("not found yo").toFuture
    }
  }


  val app = new ThrinatraController
  register(app)
}
