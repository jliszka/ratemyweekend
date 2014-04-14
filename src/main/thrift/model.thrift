namespace scala org.jliszka.ratemyweekend.model.gen

include "types.thrift"

typedef string UserId (new_type="true")
typedef types.ObjectId SessionId (new_type="true")

struct User {
  1: required UserId id (wire_name="_id")
  2: string accessToken (wire_name="at")
} (
  mongo_collection="users"
  mongo_identifier="ratemyweekend"
)

struct Session {
  1: required SessionId id (wire_name="_id")
  2: required types.DateTime lastUsed (wire_name="lu")
  3: required UserId uid (wire_name="u")
} (
  mongo_collection="sessions"
  mongo_identifier="ratemyweekend"
)