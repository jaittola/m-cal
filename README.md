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
export BASE_URI_FOR_UPDATES="https://example.com/bookings/index" # URL of the application for update links
```

Section "Setting up a development database" in this document
gives instructions on setting up a database for development work. You
probably want something more permanent for production use; services
like Heroku Postgres or Amazon RDS are good choices. If you want to
set up and manage the database yourself, have a look at PostgreSQL's
official documentation.

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

### HTTP Basic Authentication

You can specify environment variables to protect the user interface
with HTTP Basic-Auth.

```

# These values are for local testing only
export BOOKING_USERNAME=user
export BOOKING_PASSWORD=password
export BOOKING_REALM="Vartiovuorovaraukset"

# To use these values, you can run
CONTACT_ADDR="sähköpostitse (test@example.com)." EMAIL_CONFIRMATION_FROM=test@example.com EMAIL_CONFIRMATION_SUBJECT="Vartiovuorovaraukset" DATABASE_URL="postgresql://mcal@localhost/mcaldb" FIRST_BOOKING_DATE="2018-09-01" LAST_BOOKING_DATE="2018-12-31" REQUIRED_DAYS=2 BOOKING_USERNAME=user BOOKING_PASSWORD=password BOOKING_REALM="Varaukset" BASE_URI_FOR_UPDATES="http://localhost:3000/bookings/index" lein ring server-headless
```

I strongly recommend using the Basic Authentication because otherwise
the API will be completely open if deployed in the open Internet. The
authentication can be disabled to help with development work.

A better authentication mechanism than HTTP Basic Authentication
should be implemented. It just does not exist, yet.

## Clojure development instructions

After starting the server with `lein ring server` or `lein ring
server-headless` (remember the environment variables), point your web
browser to http://localhost:3000

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

### Run application:

```
lein clean
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Production Build

```
lein clean
lein cljsbuild once min
```

## License

This software uses the so-called 3-clause BSD license. See LICENSE
file in this repository for details.
