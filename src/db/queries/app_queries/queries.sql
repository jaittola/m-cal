
-- :name db-list-all-bookings :? :*
-- :doc List all bookings
SELECT
b.id,
TO_CHAR(b.booked_date, 'YYYY-MM-DD') as booked_date,
b.users_id as user_id,
u.username as name,
u.yachtname as yacht_name
FROM booking b
JOIN users u ON u.id = b.users_id
ORDER BY booked_date;

-- :name db-list-all-bookings-for-admin :? :*
-- :doc List all bookings
SELECT
b.id,
TO_CHAR(b.booked_date, 'YYYY-MM-DD') as booked_date,
b.users_id as user_id,
u.username as name,
u.yachtname as yacht_name,
u.email,
u.phone,
u.secret_id
FROM booking b
JOIN users u ON u.id = b.users_id
ORDER BY booked_date;

-- :name db-insert-user :<!
-- :doc Insert a user into the database. Return data compatible with UserIDs records.
INSERT INTO users (username, yachtname, phone, email)
VALUES (:name, :yacht_name, :phone, :email)
RETURNING id, secret_id;

-- :name db-insert-booking :<!
-- :doc Insert a booking into the database
INSERT INTO booking (booked_date,  users_id)
VALUES (TO_DATE(:booked_date, 'YYYY-MM-DD'), :users_id)
RETURNING id AS booking_id, TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date;

-- :name db-insert-booking-log :<!
-- :doc Insert a booking log entry into the database
INSERT INTO booking_log (
booked_date,
users_id,
booking_id,
operation,
user_data,
user_login_id)
VALUES (
TO_DATE(:booked_date, 'YYYY-MM-DD'),
:users_id,
:booking_id,
:operation,
:user_data,
:user_login_id)
RETURNING id;

-- :name db-query-eventlog :? :*
-- :doc List all events in the log
SELECT
u.id AS user_id,
u.secret_id AS user_secret_id,
log.id AS log_id,
log.booking_id,
TO_CHAR(log.booked_date, 'YYYY-MM-DD') AS booked_date,
TO_CHAR(log.timestamp, 'YYYY-MM-DD"T"HH24:MI:SS') AS event_timestamp,
log.operation,
log.user_data,
log.user_login_id,
ul.user_login_username,
ul.user_login_realname
FROM booking_log log
JOIN users u ON log.users_id = u.id
LEFT JOIN user_login ul ON log.user_login_id = ul.user_login_id
ORDER BY log.id DESC;

-- :name db-update-user :! :n
-- :doc Update a user's details in the database
UPDATE users
SET
username = :name,
yachtname = :yacht_name,
phone = :phone,
email = :email
WHERE id = :id;

-- :name db-insert-booking-selections :insert
-- :doc Insert user's booking parameters into database
INSERT INTO user_booking_selections (
users_id, number_of_paid_bookings)
VALUES (:user_id, :number_of_paid_bookings);

-- :name db-update-booking-selections-for-admin :! :n
-- :doc Update user's booking selections by administrator.
UPDATE user_booking_selections
SET
number_of_paid_bookings = :number_of_paid_bookings,
timestamp = NOW()
WHERE users_id = :user_id;

-- :name db-upsert-booking-selections-for-admin :! :n
-- :doc Insert or update user's booking selections. Meant for admins.
INSERT INTO user_booking_selections
(users_id, number_of_paid_bookings)
VALUES (:id, :number_of_paid_bookings)
ON CONFLICT (users_id) DO UPDATE SET
number_of_paid_bookings = :number_of_paid_bookings,
timestamp = NOW();

-- :name db-select-user-bookings-for-update :? :*
-- :doc Find user's bookings for updating.
SELECT
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date
FROM booking
WHERE users_id = :user_id
FOR UPDATE;

-- :name db-select-user-bookings :? :*
-- :doc Find user's bookings.
SELECT
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date
FROM booking
WHERE users_id = :user_id;

-- :name db-find-user-by-secret-id :query :one
-- :doc Find user by secret id. Return data compatible with UserWithIDs records.
SELECT
u.id,
u.secret_id,
u.username AS name,
u.yachtname AS yacht_name,
u.email,
u.phone,
ubs.number_of_paid_bookings
FROM users u
LEFT JOIN user_booking_selections ubs ON ubs.users_id = u.id
WHERE u.secret_id = :secret_id;

-- :name db-find-user-by-id :query :one
-- :doc "Find user by sequential id. Return data compatiable with UserWithIDs records.
SELECT
u.id,
u.secret_id,
u.username AS name,
u.yachtname AS yacht_name,
u.email,
u.phone,
ubs.number_of_paid_bookings
FROM users u
LEFT JOIN user_booking_selections ubs ON ubs.users_id = u.id
WHERE u.id = :id;

-- :name db-list-all-users :? :*
-- :doc List all users, including booking selections
SELECT
u.id,
u.secret_id,
u.username AS name,
u.yachtname AS yacht_name,
u.email,
u.phone,
ubs.number_of_paid_bookings,
COALESCE(json_agg(b.booked_date) FILTER (WHERE b.booked_date IS NOT NULL),
         '[]'::json) AS selected_dates
FROM users u
LEFT JOIN user_booking_selections ubs ON ubs.users_id = u.id
LEFT JOIN booking b ON u.id = b.users_id
GROUP BY 1, 2, 3, 4, 5, 6, 7
ORDER BY name;

-- :name db-delete-booking :! :n
-- :doc Delete bookings from database
DELETE FROM booking
WHERE id in (:v*:ids);

-- :name db-find-booking-by-id-for-update :? :*
-- :doc Find booking by id
SELECT
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date,
users_id
FROM booking
WHERE id = :id
FOR UPDATE;

-- :name db-add-to-confirmation-queue :!
-- :doc Add an entry for sending an e-mail confirmation.
INSERT INTO email_confirmation_queue (users_id)
VALUES (:id)
ON CONFLICT (users_id) DO UPDATE SET timestamp = NOW();

-- :name db-get-email-confirmation-queue-next-entry :? :*
-- :doc Get next entry from email confirmation queue
WITH queued_emails AS (
SELECT
id AS email_confirmation_queue_id,
users_id
FROM email_confirmation_queue
WHERE timestamp < NOW() - interval '2 minutes'
ORDER BY email_confirmation_queue_id
LIMIT 10
FOR UPDATE SKIP LOCKED),

user_bookings AS (
SELECT
users_id,
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date
from booking
ORDER BY booked_date)

SELECT
q.email_confirmation_queue_id AS queue_id,
u.id AS users_id,
u.secret_id,
u.email,
u.username AS name,
u.yachtname AS yacht_name,
u.phone,
array_agg(ub.booked_date) as booked_dates,
array_agg(ub.booking_id) as booking_ids
FROM users u
JOIN user_bookings ub ON u.id = ub.users_id
JOIN queued_emails q ON q.users_id = u.id
GROUP BY u.id, q.email_confirmation_queue_id;

-- :name db-delete-email-confirmation-queue-entry :! :n
-- :doc Remove entry from email configuration queue
DELETE FROM
email_confirmation_queue
WHERE id = :id;

-- :name db-reset-everything-dangerously :!
-- :doc Delete users and their bookings. Used for testing purposes only.
TRUNCATE TABLE
email_confirmation_queue;
TRUNCATE TABLE
booking_log;
TRUNCATE TABLE
booking;
TRUNCATE TABLE
users CASCADE;
TRUNCATE TABLE
session;

-- :name db-get-all-users :? :*
-- :doc List all users. For testing purposes.
SELECT *
FROM users;

-- :name db-check-user-credentials :? :*
-- :doc Check the username and password of a user and return id, real name and role
SELECT
login_id,
realname,
role
FROM
check_password(:username, :password);

-- :name db-save-token :!
-- :doc Save a login token into database
INSERT INTO session (session_token, user_login_id, session_expires)
VALUES (:token, :user_login_id, now() + CAST(:session_duration AS INTERVAL));

-- :name db-wipe-token :!
-- :doc Delete token from database
DELETE FROM session
WHERE session_token = :token;

-- :name db-fetch-user-for-token :? :*
-- :doc Fetch user details from database for a valid token
SELECT
ul.user_login_id,
ul.user_login_realname,
ul.user_login_role
FROM user_login ul
NATURAL JOIN session s
WHERE s.session_token = :token
AND s.session_expires > now();

-- :name db-clean-up-old-tokens :!
-- :doc Delete expired tokens from database
DELETE FROM session
WHERE session_expires < now();
