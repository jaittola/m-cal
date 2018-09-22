
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
       id SERIAL PRIMARY KEY,
       secret_id UUID NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
       username VARCHAR,
       yachtname VARCHAR,
       email VARCHAR
);

CREATE TABLE IF NOT EXISTS booking (
       id SERIAL PRIMARY KEY,
       booked_date DATE UNIQUE NOT NULL,
       timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
       users_id INTEGER NOT NULL REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS booking_log (
       id SERIAL PRIMARY KEY,
       booking_id INTEGER NOT NULL,
       users_id INTEGER NOT NULL REFERENCES users(id),
       booked_date DATE NOT NULL,
       timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
       booking_or_release SMALLINT NOT NULL -- 1 = booking, 2 = release
              CHECK (booking_or_release IN (1, 2))
);
