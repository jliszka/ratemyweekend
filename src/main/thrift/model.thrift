namespace scala org.jliszka.ratemyweekend.gen

include "types.thrift"

typedef types.ObjectId UserId (new_type="true")

struct User {
  1: required UserId id (wire_name="_id")
  2: string accessToken (wire_name="at")
  3: i64 fsId (wire_name="fsid")
} (
  mongo_collection="users"
  mongo_identifier="ratemyweekend"
)
