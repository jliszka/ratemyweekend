package org.jliszka.ratemyweekend

import com.foursquare.spindle.Record
import com.foursquare.rogue.spindle.{SpindleRogue, SpindleRogueWriteSerializer}
import com.foursquare.rogue.{BSONType, Rogue}

object RogueExtensions extends Rogue with SpindleRogue {

  class SpindleRecordIsBSONType[R <: Record[R]] extends BSONType[R] {
    private val serializer = new SpindleRogueWriteSerializer
    override def asBSONObject(v: R): AnyRef = serializer.toDBObject(v)
  }

  object _SpindleRecordIsBSONType extends SpindleRecordIsBSONType[Nothing]

  implicit def SpindleRecordIsBSONType[R <: Record[R]]: BSONType[R] = _SpindleRecordIsBSONType.asInstanceOf[BSONType[R]]
}