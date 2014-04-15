package org.jliszka.ratemyweekend

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat

object Util {
  val tz = DateTimeZone.forID("America/New_York")
  def dateToApi(d: DateTime) = d.getMillis / 1000
  def apiToDate(s: Long) = new DateTime(s * 1000).withZone(tz)
}