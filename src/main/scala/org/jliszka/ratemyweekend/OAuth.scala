package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.{FS, FSApi}
import org.jliszka.ratemyweekend.json.gen.{AccessTokenResponse, FriendsResponseWrapper, UserJson, UserResponseWrapper}
import org.jliszka.ratemyweekend.model.gen.{Friend, Session, User}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{FriendId, SessionId, UserId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

class OAuthController extends Controller {

  val ClientId = "YW2OX3IMFPZ1RNZZC5QSHCFNQYKHXQTJKMHNTSK32USWXSQU"
  val ClientSecret = "01EBTETQJJMWI2W4OZWGKNGHICOTWMW1OSSTDJ3RYEGQ3RTG"
  def OAuthCallback = {
    if (!"development".equals(config.env()))
      "http://ratemyweekend.herokuapp.com/oauth_callback"
    else
      "http://localhost.com:7070/oauth_callback"
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

    for {
      token <- accessTokenF
      user <- Actions.createUser(token.access_token)
      (_, session) <- future.join(
        Actions.syncUser(user),
        Actions.createSession(user))
    } yield {
      redirect("/", permanent = true).cookie("sessionid", session.id.toString)
    }
  }
}
