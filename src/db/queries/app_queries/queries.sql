
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

-- :name db-insert-user :<!
-- :doc Insert a user into the database. Return data compatible with UserIDs records.
INSERT INTO users (username, yachtname, phone, email)
VALUES (:name, :yacht_name, :phone, :email)
RETURNING id, secret_id;

-- :name db-insert-booking :<!
-- :doc Insert a booking into the database
INSERT INTO booking (booked_date,  users_id)
VALUES (TO_DATE(:booked_date, 'YYYY-MM-DD'), :users_id)
RETURNING id;

-- :name db-insert-booking-log :<!
-- :doc Insert a booking log entry into the database
INSERT INTO booking_log (booked_date,  users_id, booking_id, operation)
VALUES (TO_DATE(:booked_date, 'YYYY-MM-DD'), :users_id, :booking_id, :operation)
RETURNING id;

-- :name db-update-user :! :n
-- :doc Update a user's details in the database
UPDATE users
SET
username = :name,
yachtname = :yacht_name,
phone = :phone,
email = :email
WHERE id = :id;

-- :name db-select-user-bookings-for-update :? :*
-- :doc Find user's bookings
SELECT
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date
FROM booking
WHERE users_id = :user_id
FOR UPDATE;

-- :name db-select-user-bookings :? :*
-- :doc Find user's bookings for updating.
SELECT
id AS booking_id,
TO_CHAR(booked_date, 'YYYY-MM-DD') AS booked_date
FROM booking
WHERE users_id = :user_id;

-- :name db-find-user-by-secret-id :? :*
-- :doc Find user by secret id. Return data compatible with UserWithIDs records.
SELECT
id,
secret_id,
username AS name,
yachtname AS yacht_name,
email,
phone
FROM users u
WHERE secret_id = :secret_id;

-- :name db-delete-booking :! :n
-- :doc Delete bookings from database
DELETE FROM booking
WHERE id in (:v*:ids);

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
u.username,
u.yachtname AS yacht_name,
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

-- :name db-get-all-users :? :*
-- :doc List all users. For testing purposes.
SELECT *
FROM users;
