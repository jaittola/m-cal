#!/bin/bash

export DATABASE_URL="postgresql://mcal@localhost/mcaldb_test"
export FIRST_BOOKING_DATE="2018-09-01"
export LAST_BOOKING_DATE="2018-12-31"
export REQUIRED_DAYS=2
export BASE_URI_FOR_UPDATES="https://example.com/bookings/index"

export TESTING=true
export TESTING_DATE="2018-10-15"

lein ring server-headless