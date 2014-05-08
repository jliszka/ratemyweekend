package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import com.twitter.finagle.http.Cookie
import com.twitter.util.Duration
import java.util.concurrent.TimeUnit
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.FSApi
import org.jliszka.ratemyweekend.json.gen.{CheckinsResponseWrapper, CheckinJson}
import org.jliszka.ratemyweekend.model.gen.{Rating, Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{RatingId, SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object App extends FinatraServer {

  class ThrinatraController extends Controller {

    get("/") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => render.view(new View.Index).toFuture
        case Some(user) => {
          Actions.hasWeekendsToRate(user).flatMap(needsToRate => {
            if (needsToRate) {
              // Render weekends to rate
              for {
                toRate <- Actions.getWeekendsToRate(user)
                template = new View.Rate(user, toRate)
                r <- render.view(template).toFuture
              } yield r
            } else {
              // Render leaderboard
              for {
                (userScores, weekendScores) <- Actions.getFriendScores(user)
                template = new View.Leaderboard(user, userScores, weekendScores)
                r <- render.view(template).toFuture
              } yield r
            }
          })
        }
      })
    }

    get("/sync") { request =>
      val week = Week.thisWeek
      for {
        _ <- Actions.syncCheckins(week)
        r <- render.status(200).plain("ok").toFuture
      } yield r
    }

    get("/schema") { request => future {
      Batch.updateSchema(request.params.getOrElse("v", ???).toInt)
      render.status(200).plain("ok")
    }}

    get("/profile") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => redirect("/").toFuture
        case Some(user) => redirect(s"/user/${user.id}").toFuture
      })
    }

    get("/user/:id") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => redirect("/").toFuture
        case Some(me) => {
          val userId = UserId(request.routeParams.getOrElse("id", ???))
          val userOptF = future(db.fetchOne(Q(User).where(_.id eqs userId)))
          userOptF.flatMap(userOpt => userOpt match {
            case None => render.view(new View.UserNotFound(userId)).toFuture
            case Some(user) => {
              for {
                weekends <- Actions.weekendsForFriend(me, user)
                template = new View.Profile(me, user, weekends)
                r <- render.view(template).toFuture
              } yield r
            }
          })
        }
      })
    }

    post("/rate/:rid") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => render.status(400).plain("not logged in").toFuture
        case Some(user) => future {
          val ratingId = RatingId(new ObjectId(request.routeParams.getOrElse("rid", ???)))
          val score = Integer.parseInt(request.params.getOrElse("score", ???))
          db.updateOne(Q(Rating)
            .where(_.id eqs ratingId)
            .modify(_.score setTo score))
          render.status(200).plain("ok")
        }
      })
    }

    get("/thisweek") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => redirect("/").toFuture
        case Some(user) => {
          for {
            weekends <- Actions.weekendForFriends(user, Week.thisWeek)
            template = new View.ThisWeek(user, Week.thisWeek, weekends)
            r <- render.view(template).toFuture
          } yield r
        }
      })
    }

    get("/logout") { request =>
      val c = new Cookie("sessionid", "")
      c.maxAge = Duration(-1, TimeUnit.DAYS)
      redirect("/", permanent = false).cookie(c).toFuture
    }

    error { request =>
      request.error match {
        case Some(e: Exception) => {
          val stackTrace = Util.getStackTrace(e)
          render.status(500).plain(e.getMessage + "\n" + stackTrace).toFuture
        }
        case _ => render.status(500).plain("Something went wrong!").toFuture
      }
    }

    notFound { request =>
      render.status(404).plain("not found yo").toFuture
    }

    def loggedInUser(request: Request): Future[Option[User]] = future {
      for {
        sessionId <- request.cookies.get("sessionid").map(c => SessionId(new ObjectId(c.value)))
        session <- db.findAndUpdateOne(Q(Session).where(_.id eqs sessionId).findAndModify(_.lastUsed setTo DateTime.now))
        user <- db.fetchOne(Q(User).where(_.id eqs session.uid))
      } yield user
    }
  }


  register(new ThrinatraController)
  register(new OAuthController)
}
