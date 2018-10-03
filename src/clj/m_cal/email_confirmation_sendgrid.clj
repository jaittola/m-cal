(ns m-cal.email-confirmation-sendgrid
  (:require
   [m-cal.config :as config]
   [clj-http.client :as http-client]
   [clojure.data.json :as json]))

;; Everything in this file is specfic to the SendGrid HTTP API.

(def endpoint "https://api.sendgrid.com/v3/mail/send")

(defn email-request-body [from subject to {:keys [text html]}]
  {:personalizations [{:to [{:email to}]
                        :subject subject}]
   :from {:email from}
   :content [{:type "text/plain"
              :value text}
             {:type "text/html"
              :value html}]})

(defn send-email-confirmation [from subject to message]
  (let [{:keys [api-key disabled]} (config/sendgrid-email-config)
        send-email-body (email-request-body from subject to message)
        json-email-body (json/write-str send-email-body)]
    (println "JSON BODY is" json-email-body)
    (if (and api-key (not disabled))
      (do
        (http-client/post endpoint
                          {:async? true
                           :oauth-token api-key  ;; This is a bit funny but should cause an 'Authorization: Bearer XX' header to be created.
                           :accept :json
                           :content-type :json
                           :body json-email-body}
                          ;; success callback
                          (fn [response] (println "Send e-mail response is:" response))
                          ;; error callback
                          (fn [exception] (println "Sending e-mail failed: " (.getMessage exception)))))
      (println "Not sending e-mail because of configuration: explicit disable" disabled))))
