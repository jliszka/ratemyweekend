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
import org.jliszka.ratemyweekend.model.gen.User
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.UserId

object App extends FinatraServer {
  val ClientId = "YW2OX3IMFPZ1RNZZC5QSHCFNQYKHXQTJKMHNTSK32USWXSQU"
  val ClientSecret = "01EBTETQJJMWI2W4OZWGKNGHICOTWMW1OSSTDJ3RYEGQ3RTG"
  val OAuthCallback = "http://ratemyweekend.herokuapp.com/oauth_callback"

  val FS = new FHttpClient("foursquare", "foursquare.com:443",
     ClientBuilder()
      .codec(Http())
      .tls("foursquare.com")
      .tcpConnectTimeout(1.second)
      .hostConnectionLimit(1)
      .retries(0))

  class ThrinatraController extends Controller {

    class HomeView(val userOpt: Option[User]) extends View {
      val template = "home.mustache"
    }

    get("/") { request =>
      // TODO: read user from session
      val template = new HomeView(None)
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
        FS("/users/self")
          .params("access_token" -> token.access_token)
          .getFuture()
          .map(Json.parse(_, UserResponseWrapper).response.user)
      }


      def userF(token: AccessTokenResponse, fsUser: UserJson): Future[User] = {
        val user = User.newBuilder
          .id(UserId(new ObjectId))
          .accessToken(token.access_token)
          .fsId(fsUser.id)
          .result
        Future(db.save(user))
      }

      for {
        token <- accessTokenF
        fsUser <- fsUserF(token)
        user <- userF(token, fsUser)
      } yield {
        redirect("/", permanent = true)
      }
    }

    notFound { request =>
      render.status(404).plain("not found yo").toFuture
    }
  }


  val app = new ThrinatraController
  register(app)
}
