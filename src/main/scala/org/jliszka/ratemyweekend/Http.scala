package org.jliszka.ratemyweekend

import com.foursquare.fhttp._
import com.foursquare.fhttp.FHttpRequest._
import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{Http => FHttp}

object Http {
  val FS = new FHttpClient("foursquare", "foursquare.com:443",
     ClientBuilder()
      .codec(FHttp())
      .tls("foursquare.com")
      .tcpConnectTimeout(1.second)
      .hostConnectionLimit(1)
      .retries(0))

  val FSApi = new FHttpClient("foursquare-api", "api.foursquare.com:443",
     ClientBuilder()
      .codec(FHttp())
      .tls("api.foursquare.com")
      .tcpConnectTimeout(1.second)
      .hostConnectionLimit(1)
      .retries(0))    
}