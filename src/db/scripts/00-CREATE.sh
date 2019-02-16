#!/bin/bash -x

dir=`pwd`
cd src/db/scripts

db_user=mcal
db_name=mcaldb
db_connstring="postgresql://$db_user@localhost/$db_name"

test_db_name=mcaldb_test
test_db_connstring="postgresql://$db_user@localhost/$test_db_name"

killall postgres
rm -rf "$dir/local-database" "$dir/psql.log"
./create_postgresql_instance.sh "$dir/local-database/"
./start_db.sh "$dir/local-database/" "$dir/psql.log"
./setup_database_instance.sh "$db_name" "$db_user"
./create_schema.sh "$db_connstring"

./setup_database_instance.sh "$test_db_name" "$db_user"
./create_schema.sh "$test_db_connstring"
./create_users_for_tests.sh "$test_db_connstring"
