package org.jliszka.ratemyweekend

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.twitter.finatra.test._

class AppTest extends FlatSpecHelper {

  val server = App
  val app = new App.ThrinatraController

  "GET index page" should "respond" in {
    get("/")
    response.code should equal (200)
  }

  /*

  "PUT item" should "create an item" in {
    post("/item/put", params=Map("text" -> "foo"))
    response.code should equal (302)
  }

  "DELETE item" should "delete the item" in {
  }

  */
}
