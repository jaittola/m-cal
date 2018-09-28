
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
-- :doc Insert a user into the database
INSERT INTO users (username, yachtname, email)
VALUES (:name, :yacht_name, :email)
RETURNING id, secret_id;

-- :name db-insert-booking :<!
-- :doc Insert a booking into the database
INSERT INTO booking (booked_date,  users_id)
VALUES (TO_DATE(:booked_date, 'YYYY-MM-DD'), :users_id)
RETURNING id;

-- :name db-insert-booking-log :<!
-- :doc Insert a booking log entry into the database
INSERT INTO booking_log (booked_date,  users_id, booking_id, booking_or_release)
VALUES (TO_DATE(:booked_date, 'YYYY-MM-DD'), :users_id, :booking_id, :booking_or_release)
RETURNING id;

-- :name db-update-user :! :n
-- :doc Update a user's details in the database
UPDATE users
SET
username = :name,
yachtname = :yacht_name,
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

-- :name db-find-user-by-secret-id :? :*
-- :doc Find user by secret id
SELECT id, secret_id
FROM users u
WHERE secret_id = :user_secret_id;

-- :name db-delete-booking :! :n
-- :doc Delete bookings from database
DELETE FROM booking
WHERE id in (:v*:ids);
