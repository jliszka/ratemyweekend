package org.jliszka.ratemyweekend

import com.foursquare.fhttp._
import com.foursquare.fhttp.FHttpRequest._
import com.foursquare.rogue.Rogue._
import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.gen.AccessTokenResponse
import org.jliszka.ratemyweekend.gen.ModelTypedefs.UserId

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
      .retries(0)))

  class ThrinatraController extends Controller {

    class ThrinatraMustache(val items: Seq[Item]) extends View {
      val template = "ratemyweekend.mustache"
    }

    get("/") { request =>
      val items = Nil
      val template = new ThrinatraMustache(items)
      render.view(template).toFuture
    }

    get("/authenticate") { request =>
      val uri = s"https://foursquare.com/oauth2/authenticate?client_id=$ClientId&response_type=code&redirect_uri=$OAuthCallback"
      redirect(uri, permanent = false).toFuture
    }

    get("/oauth_callback") { request =>
      val code = request.params.getOrElse("code", ???)
      val accessTokenF = FS("/oauth2/access_token")
        .params(
          "client_id" -> ClientId,
          "client_secret" -> ClientSecret,
          "grant_type" -> "authorization_code",
          "redirect_uri" -> OAuthCallback,
          "code" -> code)
        .postFuture()

      def userF(token: AccessTokenResponse) = {
        FS("/users/self")
          .params("access_token" -> token.access_token)
          .getFuture()
      }

      for {
        tokenJson <- accessTokenF
        token = Json.parse(tokenJson, AccessTokenResponse)
        fsUserJson <- userF(token)
      } yield {
        val fsUser = Json.parse(fsUserJson, UserResponse)
        val user = User.createRecord.id(UserId(new ObjectId))
          .accessToken(token.access_token)
          .fsId(user.id)
          .result
        db.save(user)
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
