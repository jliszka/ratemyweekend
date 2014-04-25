package org.jliszka.ratemyweekend

import com.twitter.util.{Future, FuturePool}
import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor, TimeUnit}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object Util {
  val tz = DateTimeZone.forID("America/New_York")
  def dateToApi(d: DateTime) = d.getMillis / 1000
  def apiToDate(s: Long) = new DateTime(s * 1000).withZone(tz)
}

object future {
  private val executor = new ThreadPoolExecutor(
    10, 50,
    60L, TimeUnit.SECONDS,
    new SynchronousQueue())

  private val pool = FuturePool(executor)

  def apply[A](a: => A): Future[A] = pool(a)

  def groupedCollect[A, B](xs: Seq[A], par: Int)(f: A => Future[B]): Future[Seq[B]] = {
    if (xs.size < par * 3) {
      Future.collect(xs.map(f))
    }
    else {
      val groupSize = xs.size / par
      Future.collect(xs.grouped(groupSize).toSeq.map(xs => xs.foldLeft(Future.value(Seq.empty[B])){ case (bsF, x) => {
        for {
          bs <- bsF
          b <- f(x)
          bbs <- Future.value(b +: bs)
        } yield bbs
      }})).map(_.map(_.reverse).flatten)
    }
  }
}

class Week(d: DateTime) {
  val friday5pm = d.withZone(Util.tz).minusHours(4).withDayOfWeek(1).minusDays(3).withTime(17, 0, 0, 0)
  val monday4am = friday5pm.plusDays(3).withTime(4, 0, 0, 0)
  val year = friday5pm.getYear
  val week = friday5pm.getWeekOfWeekyear
}

object Week {
  val thisWeek = new Week(DateTime.now)
}