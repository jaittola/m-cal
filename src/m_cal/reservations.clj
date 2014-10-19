(ns m-cal.reservations
  (use m-cal.cal-access))

(defn list-reservations
  []
  { :body { :reservations [ { :date "2014-09-23" },
                            { :name "Veikko Veneilijä" },
                            { :boatname "S/Y Vesi" }]}}
  )
