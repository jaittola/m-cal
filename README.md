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
```

Section "Setting up a development database" in this document
gives instructions on setting up a database for development work. You
probably want something more permanent for production use; services
like Heroku Postgres or Amazon RDS are good choices. If you want to
set up and manage the database yourself, have a look at PostgreSQL's
official documentation.

You can specify environment variables to protect the user interface
with HTTP Basic-Auth.

```

# These values are for local testing only
export BOOKING_USERNAME=user
export BOOKING_PASSWORD=password
export BOOKING_REALM="Vartiovuorovaraukset"

# To use these values, you can run
DATABASE_URL="postgresql://mcal@localhost/mcaldb" FIRST_BOOKING_DATE="2018-09-01" LAST_BOOKING_DATE="2018-12-31" REQUIRED_DAYS=2 BOOKING_USERNAME=user BOOKING_PASSWORD=password BOOKING_REALM="Varaukset" lein ring server-headless
```

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

m-cal is in the public domain.
