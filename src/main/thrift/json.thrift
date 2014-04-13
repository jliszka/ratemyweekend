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

struct UserJson {
  1: required string id
}