(function() {
var Bacon = require("baconjs")
var _ = require("underscore")

function setupBookings() {
    var bookingParams = {
        firstDate: parseDate("2016-05-01"),
        lastDate: parseDate("2016-10-02"),
        numberOfBookings: 2
    }

    var selectedDates = []
    var bookedDates = {}
    var bookingData = {}

    var templates = {
        confirmation: require("../templates/confirmation.handlebars"),
        error: require("../templates/error.handlebars"),
        selectedDates: require("../templates/selectedDates.handlebars"),
        conflict: require("../templates/conflict.handlebars")
    }

    var formFields = [
        { selector: "#name", minLength: 3 },
        { selector: "#boat", minLength: 2 },
        { selector: "#email", minLength: 7,
          validator: function(email) { return /^[^@ ]+@.+\.[^.]{2,3}$/.test(email) }},
    ]

    var dateValidatorBus = new Bacon.Bus()

    function fetchBookings() {
        $.ajax({
            type: "GET",
            url: "/api/1/bookings",
            dataType: "json",
        })
        .fail(function(jqXHR) {
            showError("Varaustietojen hakeminen epäonnistui. Yritä myöhemmin uudelleen.", jqXHR)
        })
        .done(function(data) {
            updateBookings(data)
            asyncInitDone()
        })
    }

    function updateBookings(bookingsData) {
        bookedDates = _.chain(bookingsData)
            .map(function(bd) { return [ bd.date, bd.name ] })
            .object()
            .value()
    }

    function attachSubmit() {
        $("#doBooking").click(function() {
            bookingData = {
                name: $("#name").val(),
                boat: $("#boat").val(),
                email: $("#email").val(),
                dates: formatSelections()
            }

            $("#bookingContent").hide()

            $.ajax({
                type: "POST",
                url: "/api/1/booking",
                data: JSON.stringify(bookingData),
                contentType: "application/json; charset=utf-8",
                dataType: "json"
            })
            .fail(function(jqXHR) {
                if (!(jqXHR.status == 409 && handleDateConflict(jqXHR))) {
                    showError("Varausten tallettaminen epäonnistui. Yritä myöhemmin uudelleen tai " +
                              "ota yhteyttä Merenkävijöiden toimistoon.",
                              jqXHR)
                }
            })
            .done(showConfirmation)
        })
    }

    function handleDateConflict(jqXHR) {
        if (jqXHR.responseJSON && jqXHR.responseJSON.alreadyBookedDates) {
            var updatedBookings = jqXHR.responseJSON.alreadyBookedDates
            updateBookings(updatedBookings)
            var selections = _.reject(
                selectedDates, function(d) {
                    return _.has(bookedDates, toMachineDate(d))
                })
            $("#datepicker").datepick("clear")
            $("#datepicker").datepick
                .apply($("#datepicker"), _.flatten(["setDate", selections]))
            $("#conflict")
                .empty()
                .html(templates.conflict({ errorMessage: "Valitsemasi päivä olikin " +
                                           "jo varattu. Tarkista valintasi." }))
            $("#bookingContent").show()
            return true
        }
        return false
    }

    function selectDates(dates) {
        selectedDates = dates
        showSelectedDates(selectedDates)
        dateValidatorBus.push(dates.length ===
                              bookingParams.numberOfBookings)
    }

    function setupDatepicker() {
        $("#datepicker").empty()
        $("#datepicker").datepick({
            minDate: bookingParams.firstDate,
            maxDate: bookingParams.lastDate,
            rangeSelect: false,
            multiSelect: bookingParams.numberOfBookings,
            onSelect: selectDates,
            onDate: checkIfBooked
        })

        function checkIfBooked(date, inMonth) {
            if (inMonth && _.has(bookedDates, toMachineDate(date)))
                return {
                    dateClass: "datepick-selected-by-others",
                    selectable: false,
                }
            return {}
        }

        showSelectedDates(selectedDates)
    }

    function showSelectedDates(selectedDates, element) {
        element = element || "#selectedDates"

        function updateDatesForPublishing(dates) {
            if (!dates.length)
                return dates
            var index = 1
            var resultDates = [];
            _.each(dates, function(d) {
                resultDates.push({ idx: index++,
                                   date: toFinnishDateString(d) })
            })
            _.times(bookingParams.numberOfBookings - resultDates.length,
                    function() { resultDates.push({
                        idx: index++,
                        date: "ei valittu" }) })
            return resultDates
        }

        $(element)
            .empty()
            .html(templates.selectedDates({count: selectedDates.length,
                                           dates: updateDatesForPublishing(selectedDates)}))
    }

    function showConfirmation() {
        $("#confirmationContent")
            .empty()
            .html(templates.confirmation(bookingData))
        var dates = _.map(bookingData.dates, function(d) { return parseDate(d) })
        showSelectedDates(dates, "#bookedDates")
    }

    function showError(message, jqXHR) {
        if (console) {
            console.log("Error: " + message)
            if (JSON && JSON.stringify) console.log(JSON.stringify(jqXHR))
        }
        $("#errorBanner")
            .empty()
            .html(templates.error({ errorMessage: message }))
    }

    function validateTextField(field) {
        var value = $(field.selector).val()
        if (value.length < field.minLength ||
            (field.validator && !field.validator(value))) {
            return false
        }
        return true
    }

    function allTrue() {
        return _.every(arguments, function(a) { return !!a })
    }

    function setupValidation() {
        var formValidatorProperties =
            _.map(formFields, function(field) {
                return $(field.selector).asEventStream('keyup cut paste input')
                    .map(function() { return validateTextField(field) })
                    .skipDuplicates()
                    .toProperty(false)
            })
        Bacon.combineWith.apply(this, _.flatten([allTrue,
                                                 dateValidatorBus.toProperty(false),
                                                 formValidatorProperties]))
            .onValue(setSubmitEnabled)
    }

    function setSubmitEnabled(isEnabled) {
        $("#doBooking").prop("disabled", !isEnabled || false);
    }

    function formatSelections() {
        return _.map(selectedDates, toMachineDate)
    }

    function parseDate(dateAsString) {
        return new Date(dateAsString)  // Note, may not work on old browsers
    }

    function format2Digits(num) {
        return num < 10 ? "0" + num : num
    }

    function toMachineDate(date) {
        return date.getFullYear() + "-" +
            format2Digits(date.getMonth() + 1) + "-" +
            format2Digits(date.getDate())
    }

    function toFinnishDateString(date) {
        return date.getDate() + "." +
            (date.getMonth() + 1) + "." +
            date.getFullYear()
    }

    function asyncInitDone() {
        setupDatepicker()
    }

    fetchBookings()
    attachSubmit()
    setupValidation()
}

$(document).ready(setupBookings)
}());
