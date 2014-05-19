package org.jliszka.ratemyweekend

import com.foursquare.rogue.{BSONType, QueryField}
import com.foursquare.rogue.spindle.{SpindleQuery => Q}
import com.foursquare.spindle.{HasMetaPrimaryKey, HasPrimaryKey, MetaRecord, Record}
import com.twitter.finatra.config
import com.twitter.util.{Future, FuturePool}
import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.Executors
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object Util {
  val tz = DateTimeZone.forID("America/New_York")
  val dateTimeFmt = DateTimeFormat.forPattern("EEEE, MMMM d, yyyy 'at' h:mm aa z")
  val dateFmt = DateTimeFormat.forPattern("EEEE, MMMM d")
  val timeFmt = DateTimeFormat.forPattern("h:mm aa")

  def isDevelopment = "development".equals(config.env())
  def isProduction = !isDevelopment

  def dateToApi(d: DateTime) = d.getMillis / 1000
  def apiToDate(s: Long, offset: Int) = {
    new DateTime(s * 1000).plusMinutes(offset)
  }

  def flatten2[A, B](xys: Seq[(Option[A], Option[B])]): Seq[(A, B)] = {
    for {
      (xOpt, yOpt) <- xys
      x <- xOpt
      y <- yOpt
    } yield (x, y)
  }

  def getStackTrace(e: Exception): String = {
    val writer = new StringWriter
    val printWriter = new PrintWriter(writer)
    e.printStackTrace(printWriter)
    printWriter.flush()
    writer.toString
  }

  def idMap[
    K: BSONType,
    R <: Record[R] with HasPrimaryKey[K, R],
    M <: MetaRecord[R] with HasMetaPrimaryKey[K, R]
  ](
    meta: M with MetaRecord[R],
    ids: Seq[K]
  ): Map[K, R] = {
    db.fetch(Q(meta).where(m => new QueryField(m.primaryKey).in(ids)))
      .map(rec => (rec.primaryKey, rec))
      .toMap
  }
}

object future {
  private val pool = FuturePool(Executors.newFixedThreadPool(10))

  def apply[A](a: => A): Future[A] = pool(a)

  def groupedCollect[A, B](xs: Seq[A], par: Int)(f: A => Future[B]): Future[Seq[B]] = {
    val bsF: Future[Seq[B]] = Future.value(Seq.empty[B])
    xs.grouped(par).foldLeft(bsF){ case (bsF, group) => {
      for {
        bs <- bsF
        xs <- Future.collect(group.map(f))
      } yield bs ++ xs
    }}
  }
}

class Week(d: DateTime) {
  val friday5pm = d.withZone(Util.tz).minusHours(4).withDayOfWeek(1).minusDays(3).withTime(17, 0, 0, 0)
  val monday4am = friday5pm.plusDays(3).withTime(4, 0, 0, 0)
  val week = (friday5pm.getYear - 2014) * 100 + friday5pm.getWeekOfWeekyear
  val dateStr = Util.dateFmt.print(friday5pm)
}

object Week {
  val thisWeek = new Week(DateTime.now)
  def apply(week: Int): Week = {
    apply(week / 100 + 2014, week % 100)
  }
  def apply(year: Int, week: Int): Week = {
    val d = new DateTime(year, 1, 1, 0, 0, 0, 0)
      .withWeekOfWeekyear(week)
      .withDayOfWeek(1)
      .plusDays(10)
    new Week(d)
  }
  def weekAgo(n: Int) = new Week(DateTime.now.minusWeeks(n))
}

