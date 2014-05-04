package org.jliszka.ratemyweekend

import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.twitter.util.Future
import com.twitter.finatra._
import com.twitter.finatra.ContentType._
import org.bson.types.ObjectId
import org.jliszka.ratemyweekend.Http.FSApi
import org.jliszka.ratemyweekend.json.gen.{CheckinsResponseWrapper, CheckinJson}
import org.jliszka.ratemyweekend.model.gen.{Session, User, Weekend}
import org.jliszka.ratemyweekend.model.gen.ModelTypedefs.{SessionId, UserId, WeekendId}
import org.jliszka.ratemyweekend.RogueImplicits._
import org.joda.time.DateTime

object App extends FinatraServer {

  class ThrinatraController extends Controller {

    get("/") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => render.view(new View.Index).toFuture
        case Some(user) => {
          val toRateF = Actions.weekendsToRate(user)
          val myRatingsF = Actions.myRatings(user)
          for {
            toRate <- toRateF
            myRatings <- myRatingsF
            template = new View.Home(user, toRate, myRatings)
            r <- render.view(template).toFuture
          } yield r
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

    get("/myweek") { request =>
      loggedInUser(request).flatMap(userOpt => userOpt match {
        case None => redirect("/").toFuture
        case Some(user) => future {

          val weekendOpt = {
            db.fetchOne(Q(Weekend)
              .where(_.uid eqs user.id)
              .orderDesc(_.id))
          }

          weekendOpt match {
            case None => redirect("/")
            case Some(weekend) => {
              val template = new View.MyWeek(user, weekend)
              render.view(template)
            }
          }
        }
      })
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
