package org.jliszka.ratemyweekend

import java.net.URL
import org.apache.commons.mail.{DefaultAuthenticator, ImageHtmlEmail}
import org.apache.commons.mail.resolver.DataSourceUrlResolver

object Email {
  val host = "smtp.sendgrid.net"
  val port = 587
  def username = Option(System.getenv("SENDGRID_USERNAME")).getOrElse("app24091486@heroku.com")
  def password = Option(System.getenv("SENDGRID_PASSWORD")).getOrElse("t6z8wsuq")

  def send(to: Seq[String], subject: String, message: String) {
    val email = new ImageHtmlEmail
    email.setHostName(host)
    email.setSmtpPort(port)
    email.setAuthenticator(new DefaultAuthenticator(username, password))
    email.setDataSourceResolver(new DataSourceUrlResolver(new URL("http://ratemyweekend.herokuapp.com")))
    email.setSSLOnConnect(true)
    email.setFrom("no-reply@ratemyweekend.herokuapp.com", "Rate My Weekend")
    email.setSubject(subject)
    email.setHtmlMsg(message)

    if (Util.isDevelopment) {
      email.addTo("jliszka@gmail.com")
    } else {
      to.foreach(addr => email.addTo(addr))
    }

    email.send()
  }
}