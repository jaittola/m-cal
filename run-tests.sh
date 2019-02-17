#!/bin/bash

export DATABASE_URL=${DATABASE_URL-postgresql://mcal@localhost/mcaldb_test}
export FIRST_BOOKING_DATE=2019-01-01
export LAST_BOOKING_DATE=2019-05-31
export REQUIRED_DAYS=2
export BASE_URI_FOR_UPDATES=https://example.com
export DEFAULT_USER=the-user

export TESTING_DATE=2019-03-02
export TESTING=true

lein test
