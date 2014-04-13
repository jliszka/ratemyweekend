package org.jliszka.ratemyweekend

import org.apache.thrift.TBase
import org.apache.thrift.transport.TMemoryInputTransport
import com.foursquare.spindle.{Record, MetaRecord}
import com.foursquare.common.thrift.json.TReadableJSONProtocol

object Json {
  def parse[R <: Record[R] with TBase[R, _]](s: String, recMeta: MetaRecord[R]): R = {
    val buf = s.getBytes("UTF-8")
    val trans = new TMemoryInputTransport(buf)
    val iprot = new TReadableJSONProtocol(trans, null)
    val rec = recMeta.createRawRecord
    rec.read(iprot)
    rec
  }
}