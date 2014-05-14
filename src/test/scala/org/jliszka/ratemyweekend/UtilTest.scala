package org.jliszka.ratemyweekend

import org.joda.time.DateTime
import org.scalatest._

class UtilTest extends FlatSpec with Matchers {

  "Week" should "round trip" in {
    val start = new DateTime(2014, 2, 1, 0, 0, 0, 0)
    for (i <- 0 to (4 * 365 * 2)) {
      val d = start.plusHours(i)
      val week1 = new Week(d)
      val week2 = Week(week1.week)
      week1.week should equal (week2.week)
    }
  }

  "groupedCollect" should "work" in {
    val xs: Seq[Int] = future.groupedCollect(1 to 101, 6)(i => future(i+1))()
    xs should equal (2 to 102)
  }

  "groupedCollect" should "handle empty list" in {
    val xs: Seq[Int] = future.groupedCollect(Seq.empty[Int], 6)(i => future(i+1))()
    xs should equal (Seq.empty)
  }

  "groupedCollect" should "handle small lists" in {
    val xs: Seq[Int] = future.groupedCollect(1 to 3, 6)(i => future(i+1))()
    xs should equal (2 to 4)
  }
}
