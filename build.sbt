import com.typesafe.sbt.SbtStartScript

name := "ratemyweekend"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers += "Twitter's Repository" at "http://maven.twttr.com/"

libraryDependencies += "com.twitter" %% "finatra" % "1.5.3"

libraryDependencies += "com.foursquare" %% "rogue-field" % "2.2.1" intransitive()

libraryDependencies += "com.foursquare" %% "rogue-index" % "3.0.0-beta5" intransitive()

libraryDependencies += "com.foursquare" %% "rogue-core" % "3.0.0-beta5" intransitive()

libraryDependencies += "com.foursquare" %% "rogue-spindle" % "3.0.0-beta5" intransitive()

libraryDependencies += "org.mongodb" % "mongo-java-driver" % "2.12.1"

libraryDependencies += "com.foursquare" % "common-thrift-bson" % "1.8.4"

libraryDependencies += "com.foursquare" %% "foursquare-fhttp" % "0.1.11"

libraryDependencies += "org.apache.commons" % "commons-email" % "1.3"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.5" % "test"

seq(thriftSettings: _*)

seq(SbtStartScript.startScriptForJarSettings: _*)

initialCommands := """
                |import com.foursquare.rogue.spindle.{SpindleQuery => Q}
                |import org.jliszka.ratemyweekend.RogueImplicits._
                |import org.jliszka.ratemyweekend.model.gen._
                |import org.jliszka.ratemyweekend.model.gen.ModelTypedefs._
                |import org.jliszka.ratemyweekend._
                |import org.bson.types.ObjectId
                |import org.joda.time.DateTime""".stripMargin('|')