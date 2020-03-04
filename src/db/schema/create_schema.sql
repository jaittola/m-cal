
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
       id SERIAL PRIMARY KEY,
       secret_id UUID NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
       username VARCHAR,
       yachtname VARCHAR,
       email VARCHAR,
       phone VARCHAR
);

CREATE TABLE IF NOT EXISTS user_booking_selections (
       user_booking_selections_id SERIAL PRIMARY KEY,
       timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
       users_id INTEGER NOT NULL REFERENCES users (id),
       number_of_paid_bookings INTEGER NOT NULL
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
              CONSTRAINT booking_log_operation_check CHECK (operation IN (1, 2, 3, 4, 5, 6, 7)),
       user_data JSONB,
       user_login_id INTEGER
);

CREATE TABLE IF NOT EXISTS email_confirmation_queue (
       id SERIAL PRIMARY KEY,
       users_id INTEGER UNIQUE REFERENCES users(id),
       timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_login (
       user_login_id SERIAL PRIMARY KEY,
       user_login_username VARCHAR NOT NULL UNIQUE,
       user_login_password VARCHAR NOT NULL,
       user_login_realname VARCHAR NOT NULL,
       user_login_role VARCHAR NOT NULL,
       user_login_created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS session (
       session_token VARCHAR NOT NULL PRIMARY KEY,
       user_login_id INTEGER REFERENCES user_login(user_login_id),
       session_expires TIMESTAMP NOT NULL,
       session_created TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION create_user(username VARCHAR,
       password VARCHAR,
       realname VARCHAR,
       role VARCHAR) RETURNS INT AS $$
  DECLARE enc_password VARCHAR;
  DECLARE id INTEGER;
  BEGIN
        enc_password := crypt(password, gen_salt('bf'));
        INSERT INTO user_login (user_login_username,
                                user_login_password,
                                user_login_realname,
                                user_login_role)
                    VALUES (username,
                           enc_password,
                           realname,
                           role)
                    ON CONFLICT DO NOTHING
                    RETURNING user_login_id INTO id;
        RETURN id;
  END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION check_password(username VARCHAR,
                                          password VARCHAR)
    RETURNS TABLE (login_id INTEGER,
                   realname VARCHAR,
                   role VARCHAR) AS $$
    SELECT user_login_id,
           user_login_realname,
           user_login_role
    FROM user_login
    WHERE user_login_username = username
    AND user_login_password = crypt(password, user_login_password);
$$ LANGUAGE sql;
