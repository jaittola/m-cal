
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
       id SERIAL PRIMARY KEY,
       secret_id UUID NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
       username VARCHAR,
       yachtname VARCHAR,
       email VARCHAR,
       phone VARCHAR
);

CREATE TABLE IF NOT EXISTS booking (
       id SERIAL PRIMARY KEY,
       booked_date DATE UNIQUE NOT NULL,
       timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
       users_id INTEGER NOT NULL REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS booking_log (
       id SERIAL PRIMARY KEY,
       booking_id INTEGER,
       users_id INTEGER NOT NULL REFERENCES users(id),
       booked_date DATE,
       timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
       operation SMALLINT NOT NULL
              CONSTRAINT booking_log_operation_check CHECK (operation IN (1, 2, 3, 4, 5)),
       user_data JSONB
);

CREATE TABLE IF NOT EXISTS email_confirmation_queue (
       id SERIAL PRIMARY KEY,
       users_id INTEGER UNIQUE REFERENCES users(id),
       timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);
