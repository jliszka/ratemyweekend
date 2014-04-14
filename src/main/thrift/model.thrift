namespace scala org.jliszka.ratemyweekend.model.gen

include "types.thrift"
include "json.thrift"

typedef string UserId (new_type="true")
typedef types.ObjectId SessionId (new_type="true")
typedef types.ObjectId WeekendId (new_type="true")
typedef types.ObjectId RatingId (new_type="true")
typedef types.ObjectId FriendId (new_type="true")

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

struct Weekend {
  1: required WeekendId id (wire_name="_id")
  2: required UserId uid (wire_name="u")
  3: required i32 year (wire_name="y")
  4: required i32 week (wire_name="w")
  5: required list<json.CheckinJson> checkins (wire_name="c")
} (
  mongo_collection="weekends"
  mongo_identifier="ratemyweekend"
)

struct Rating {
  1: required RatingId id (wire_name="_id")
  2: required UserId rater (wire_name="r")
  3: required UserId ratee (wire_name="e")
  4: required WeekendId weekend (wire_name="w")
  5: required i32 score (wire_name="s")
} (
  mongo_collection="ratings"
  mongo_identifier="ratemyweekend"
)

struct Friend {
  1: required FriendId id (wire_name="_id")
  2: required UserId self (wire_name="s")
  3: required UserId other (wire_name="o")
} (
  mongo_collection="friendrs"
  mongo_identifier="ratemyweekend"
)