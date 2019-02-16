#!/bin/bash

export DATABASE_URL="${DATABASE_URL-postgresql://mcal@localhost/mcaldb_test}"
export FIRST_BOOKING_DATE="2019-01-01"
export LAST_BOOKING_DATE="2019-15-31"
export REQUIRED_DAYS=2
export BASE_URI_FOR_UPDATES="https://example.com/bookings/index"
export DEFAULT_USER=the-user

export TESTING=true
export TESTING_DATE="2019-03-02"

lein ring server-headless
