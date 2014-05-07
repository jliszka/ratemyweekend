package org.jliszka.ratemyweekend

import com.foursquare.rogue.Query
import com.foursquare.rogue.spindle.{SpindleDBCollectionFactory, SpindleDatabaseService, SpindleQuery => Q}
import com.foursquare.spindle.{Record, MetaRecord, UntypedMetaRecord}
import com.mongodb.{BasicDBObjectBuilder, DB, Mongo, MongoClient, MongoURI}

object ConcreteDBCollectionFactory extends SpindleDBCollectionFactory {
  lazy val db: DB = {
    val mongoUrl = System.getenv("MONGOHQ_URL")
    if (mongoUrl == null) {
      // TODO(dan): Support a flag override
      new MongoClient("localhost", 27017).getDB("ratemyweekend")
    } else {
      val mongoURI = new MongoURI(mongoUrl)
      val mongo = mongoURI.connectDB
      if (mongoURI.getUsername != null && mongoURI.getUsername.nonEmpty) {
        mongo.authenticate(mongoURI.getUsername, mongoURI.getPassword)
      }
      mongo
    }
  }
  override def getPrimaryDB(meta: UntypedMetaRecord) = db
  override def indexCache = None
}

object db extends SpindleDatabaseService(ConcreteDBCollectionFactory) {
  def ensureIndexes[M <: UntypedMetaRecord](query: Query[M, _, _]) {
    val coll = dbCollectionFactory.getPrimaryDBCollection(query.meta)
    for {
      indexes <- dbCollectionFactory.getIndexes(query)
      index <- indexes
    } {
      val dbo = BasicDBObjectBuilder.start
      for {
        (field, typ) <- index.asListMap
      } {
        dbo.add(field, typ.toString.toInt)
      }
      coll.createIndex(dbo.get)
    }
  }
}
