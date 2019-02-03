# m-cal

This application provides a simple booking service for a yacht club's
night watch duties. Every yacht owner must be a night watch for a
couple of times during the summer.

## Environment variables

To configure the application, you must set the following environment
variables, for which example values are given below:

```
export DATABASE_URL="postgresql://mcal@localhost/mcaldb"  # URL of the PostgreSQL database
export FIRST_BOOKING_DATE="2018-09-01"     # First available booking date
export LAST_BOOKING_DATE="2018-12-31"      # Last available booking date
export REQUIRED_DAYS=2                     # Number of bookings required
export BASE_URI_FOR_UPDATES="https://example.com/" # URL of the application for update links
export DEFAULT_USER=the-user               # Username for bookings without specific identity (see User accounts below)
```

Section "Setting up a development database" in this document
gives instructions on setting up a database for development work. You
probably want something more permanent for production use; services
like Heroku Postgres or Amazon RDS are good choices. If you want to
set up and manage the database yourself, have a look at PostgreSQL's
official documentation.

## User accounts

The user interface is protected with usernames and passwords. The
usernames and passwords are stored in the PostgreSQL database.

The application uses two roles for users:

 - `user` for ordinary users
 - `admin` for accessing the administrator views at `/booking-admin`

The adminstrator views have a login UI with a username-password
pair. Hence, you can have several different admin users.

The view for ordinary users, however, obtains the username from the
DEFAULT_USER environment variable of the server. Hence, all ordinary
users that make bookings share the same user account. It would not be
difficult to change this behavior so that every user would have a
separate account, but at the current stage it has been considered
simpler to handle the bookings in this way.

The database set-up scripts do not create the users automatically. To
create an user, log into your PostgreSQL database in which you have
set up the schema (see 'Setting up a development database' below), and
issue a SQL statement as follows:

```
SELECT create_user('[username]', '[password]', '[user real name]', '[role]');
```

where `[role]` is either `user` or `admin`.

To use this application, you have to create

 - a user with the `user` role and the same username that is configured into the `DEFAULT_USER` environment variable,
 - and at least one user with the `admin` role

The usernames and credentials are stored in a table called
`user_login` in the database. TO remove users, simply delete rows in
this table.

There is currently no password change capability apart from deleting
and recreating a user.

### E-mail confirmations

To enable the sending of email confirmations after bookings using
SendGrid, you must get a SendGrid account and place the API key into
the `SENDGRID_API_KEY` environment variable. If `SENDGRID_API_KEY`
does not exist in the environment, sending e-mail confirmation
messages is disabled.

To disable email confirmation sending even though you have
`SENDGRID_API_KEY` defined, set environment variable
`EMAIL_DISABLED=1`. This setting may be useful, for
example, if you want to stop sending e-mails in Heroku but keep the
API key in place.

The e-mail confirmation message is read from
resources/templates/confirmation.txt. It contains fields that will be
replaced with the content of the booking: `[booked_dates]`, `[name]`,
`[yacht_name]`, `[email]`, and `[update_link]`. In addition, the field
`[contact]` will be substituted with value of `CONTACT_ADDR`
environment variable.

The environment variables [EMAIL_CONFIRMATION_FROM] and
[EMAIL_CONFIRMATION_SUBJECT] define the sender and subject of the
confirmation, respectively.

Below is a summary of the e-mail configuration:

```
export SENDGRID_API_KEY=...
export CONTACT_ADDR="sähköpostitse (test@example.com)."
export EMAIL_CONFIRMATION_FROM="test@example.com"
export EMAIL_CONFIRMATION_SUBJECT="Vartiovuorovaraukset"
```

#### Configuring SendGrid for reliable mail delivery

SendGrid in its default configuration edits the e-mails in several
ways. It adds, for example, a footer, and an invisible image for
tracking purposes. It also rewrites all links in the e-mails to track
clicks by redirecting all links through its own site. Such items in
e-mails are typical for spam, and messages sent with the default
settings may have hard time passing spam filters. In addition, you
probably do not need click tracking since links in the confirmation
e-mails should lead to your service.

To configure SendGrid not to edit the messages, perform the following
configuration in the SendGrid UI Settings:

 * in Mail Settings section, set
   * "Event notification" *Off*
   * "Footer" *Off*
   * "Plain Content" *On*
 * in Tracking section, set
   * "Click tracking" *Off*
   * "Google Analytics" *Off*
   * "Open Tracking" *Off*
   * "Subscription Tracking" *Off*

SendGrid may have other relevant settings. Please go through the
SendGrid configuration UI and make sure that the settings are suitable
for you.

## Clojure development instructions

After starting the server with `lein ring server` or `lein ring
server-headless` (remember the environment variables), point your web
browser to http://localhost:3000

## Interactive development for the backend with emacs)

Run `lein repl :headless` (with the environment variables). Then, in
emacs, run `cider-connect`. When promoted for hostname and port, enter
`localhost` and the repl port that lein writes out in its output.

To actually run the ring server, issue the command `(-main 3000)` in
the repl in emacs. Then, point your browser to localhost:3000.

## Setting up a development database

This application uses PostgreSQL for storing the bookings. To create a
temporary PostgreSQL database for development work, run the command

```
src/db/scripts/00-CREATE.sh
```

You can delete this database by running

```
src/db/scripts/stop_db.sh local-database
rm -rf local-database psql.log
```

To connect to this database using psql, run

```
psql postgresql://mcal@localhost/mcaldb
```

## ClojureScript development instructions

The instructions originate from a [reagent project template](https://github.com/reagent-project/reagent).

### cljs-devtools

To enable:

1. Open Chrome's DevTools,`Ctrl-Shift-i`
2. Open "Settings", `F1`
3. Check "Enable custom formatters" under the "Console" section
4. close and re-open DevTools

### Start Cider from Emacs:

Put this in your Emacs config file:

```
(setq cider-cljs-lein-repl "(do (use 'figwheel-sidecar.repl-api) (start-figwheel!) (cljs-repl))")
```

Navigate to a clojurescript file and start a figwheel REPL with `cider-jack-in-clojurescript` or (`C-c M-J`)

### Compile css:

Compile css file once.

```
lein less once
```

Automatically recompile css file on change.

```
lein less auto
```

### Run the main application:

Set the environment variables as described above

```
lein clean
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

### Run the admin application

The admin application has a separate cljs build. It works similarly to
the main app but has a separate build target. In addition, you must set HAS_ADMIN_UI=1 into the environment.

To develop the admin UI, set HAS_ADMIN_UI=1 (and other environment
variables as appropriate), and run

```
lein figwheel dev-admin-dev
```

## Production Build

```
lein clean
lein cljsbuild once min
```

## License

This software uses the so-called 3-clause BSD license. See LICENSE
file in this repository for details.
