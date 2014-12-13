# m-cal

This application provides a simple booking service, using Google
Calendar as the back-end. The Web UI has a design that mimics the
look-and-feel of the website of yacht club Merenkävijät
(http://www.merenkavijat.fi/) (in a somewhat crappy way).

## Prerequisites

 * Install Leiningen
 * Install node.js
 * In your Google account,
   * create a service account for Calendar in the Google console
   * create a calendar, and share it with read/write access with the
     service account that you just created

## Setting up

 * Set up the following environment variables based on the account
   information at Google:

     `SERVICE_ACCOUNT_EMAIL`  : The e-mail address of your service account at Google


     `CALENDAR_ID`            : The ID of the calendar to use

     `CALENDAR_PRIVATE_KEY`   : The private key of your calendar in pkcs8 format. You can use
                                OpenSSL to convert Google's .p12 file to pkcs8 format.
                                TODO, how to exactly.

 * To install client-side dependencies, run

    `make deps`

 * To build the client-side code, run

    `make all`

 * To run the application, run either

    `lein ring server`

    or

    `make run`

 * The Makefile contains also other targets that may be useful for you.

## Deployment

 * This application can be deployed to Heroku:

   * Set up an application to Heroku
   * Set up the environment variables in Heroku configuration
   * Perform the client-side build
   * Run 'make publish' to push your code to Heroku.

## License

m-cal is in the public domain.
