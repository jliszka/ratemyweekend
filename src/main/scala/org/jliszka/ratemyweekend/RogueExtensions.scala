package org.jliszka.ratemyweekend

import com.foursquare.spindle.Record
import com.foursquare.rogue.spindle.{SpindleRogue, SpindleRogueWriteSerializer}
import com.foursquare.rogue.{BSONType, Rogue}

trait RogueExtensions {
}

object RogueImplicits extends Rogue with SpindleRogue with RogueExtensions