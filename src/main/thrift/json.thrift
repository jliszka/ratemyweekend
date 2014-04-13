namespace scala org.jliszka.ratemyweekend.gen

include "types.thrift"

struct AccessTokenResponse {
  1: required string access_token
}

struct UserResponse {
  1: required string id
}