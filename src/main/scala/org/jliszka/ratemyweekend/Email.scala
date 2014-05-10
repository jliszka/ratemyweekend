package org.jliszka.ratemyweekend

import java.net.URL
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail}

object Email {
  val host = "smtp.sendgrid.net"
  val port = 587
  def username = Option(System.getenv("SENDGRID_USERNAME")).getOrElse("app24091486@heroku.com")
  def password = Option(System.getenv("SENDGRID_PASSWORD")).getOrElse("t6z8wsuq")

  def send(to: Seq[String], subject: String, message: String, embed: Map[String, String] = Map.empty) {
    val email = new HtmlEmail
    email.setHostName(host)
    email.setSmtpPort(port)
    email.setAuthenticator(new DefaultAuthenticator(username, password))
    email.setSSLOnConnect(true)
    email.setFrom("no-reply@ratemyweekend.herokuapp.com", "Rate My Weekend")
    email.setSubject(subject)

    val replacements = for {
      (id, url) <- embed
    } yield {
      val cid = email.embed(new URL(url), id)
      ("\\$"+id, s"""<img src="cid:$cid"/>""")
    }

    val msg = replacements.foldLeft(message){ case (m, (id, r)) => m.replaceAll(id, r) }

    email.setHtmlMsg(msg)
    if (Util.isDevelopment) {
      email.addTo("jliszka@gmail.com")
    } else {
      to.foreach(addr => email.addTo(addr))
    }

    email.send()
  }
}