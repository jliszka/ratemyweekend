package org.jliszka.ratemyweekend

import com.foursquare.field.Field
import com.foursquare.spindle.Record
import com.foursquare.rogue.spindle.{SpindleRogue, SpindleRogueWriteSerializer}
import com.foursquare.rogue.{BSONType, ModifyField, Rogue}

trait RogueExtensions {
  implicit def stringFieldToModifyField[M, F <: String](f: Field[F, M]): ModifyField[F, M] = new ModifyField(f)
}

object RogueImplicits extends Rogue with SpindleRogue with RogueExtensions