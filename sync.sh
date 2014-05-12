#!/bin/sh

BIN=/Users/jliszka/foursquare.web/dependencies/mongodb/bin
HOST="oceanic.mongohq.com"
PORT=10070
DB="app24091486"
USER="heroku"
PASS="GJ9OIjVGesSMF4ulg2xMDgS1UZqzjdWZr10eXtEDKipCqOt4vRmtEwLBbV3vcBBo-tXAgjLEpbnmdLDUFtwYKQ"

$BIN/mongodump --host $HOST:$PORT --db $DB -u $USER -p$PASS -o mongohq.dmp

$BIN/mongorestore --host localhost:27017 --db ratemyweekend --drop mongohq.dmp/$DB
