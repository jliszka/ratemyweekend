namespace scala org.jliszka.ratemyweekend.json.gen

include "types.thrift"

struct AccessTokenResponse {
  1: required string access_token
}

struct UserResponseWrapper {
  1: required UserResponse response
}
struct UserResponse {
  1: required UserJson user
}

struct FriendsResponseWrapper {
  1: required FriendsResponse response
}
struct FriendsResponse {
  1: required FriendsJson friends
}

struct FriendsJson {
  1: required i32 count
  2: optional list<UserJson> items
}

struct UserJson {
  1: required string id
  2: optional string firstName
  3: optional string lastName
  4: optional PhotoJson photo
  5: optional string gender
}

struct PhotoJson {
  1: required string prefix
  2: required string suffix
  3: optional i32 width
  4: optional i32 height
}

struct CheckinsResponseWrapper {
  1: required CheckinsResponse response
}

struct CheckinsResponse {
  1: required CheckinsJson checkins
}

struct CheckinResponseWrapper {
  1: required CheckinResponse response
}

struct CheckinResponse {
  1: required CheckinJson checkin
}

struct CheckinsJson {
  1: required i32 count
  2: optional list<CheckinJson> items
}

struct CheckinJson {
  1: required string id
  2: required i32 createdAt
  3: optional string shout
  4: optional list<UserJson> with
  5: optional VenueJson venue
  6: optional PhotosJson photos
  7: optional LikeGroupsJson likes
  8: optional CommentsJson comments
  9: optional i32 timeZoneOffset
  10: optional OverlapsJson overlaps
  11: optional EventJson event
}

struct EventJson {
  1: optional string name
}

struct OverlapsJson {
  1: required i32 count
  2: optional list<OverlapJson> items
}

struct OverlapJson {
  1: optional UserJson user
}

struct CommentsJson {
  1: required i32 count
  2: optional list<CommentJson> comments
}

struct CommentJson {
  1: required UserJson user
  2: required string text
}

struct LikeGroupsJson {
  1: required i32 count
  2: optional list<LikeGroupJson> likes
  3: optional string summary
}

struct LikeGroupJson {
  1: required i32 count
  2: optional list<UserJson> items
}

struct PhotosJson {
  1: required i32 count
  2: optional list<PhotoJson> items
}

struct VenueJson {
  1: required string name
  2: required LocationJson location
  3: required list<CategoryJson> categories
}

struct LocationJson {
  1: optional string city
  2: optional string state
  3: optional string country
  4: optional string cc
}

struct CategoryJson {
  1: required string name
  2: required PhotoJson icon
  3: optional bool primary
}