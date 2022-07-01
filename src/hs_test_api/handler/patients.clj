(ns hs-test-api.handler.patients
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [hs-test-api.boundary.db.patients :as db.patients]))

(defmethod ig/init-key ::list-patients [_ {:keys [db]}]
  (fn [{{:keys [keywords]} :params}]
    [::response/ok (db.patients/find-patients db keywords)]))

(defmethod ig/init-key ::create-patient [_ {:keys [db]}]
  (fn [{[_ patient] :ataraxy/result}]
    (let [patient-id (db.patients/create-patient! db patient)]
      [::response/created (str "/patients/" patient-id)
       (db.patients/find-patient-by-id db patient-id)])))

(defmethod ig/init-key ::fetch-patient [_ {:keys [db]}]
  (fn [{[_ patient-id] :ataraxy/result}]
    (if-let [patient (db.patients/find-patient-by-id db patient-id)]
      [::response/ok patient]
      [::response/not-found {:message "Not Found"}])))

(defmethod ig/init-key ::update-patient [_ {:keys [db]}]
  (fn [{[_ patient-id patient] :ataraxy/result}]
    (if-not (db.patients/find-patient-by-id db patient-id)
      [::response/not-found {:message "Not Found"}]
      (do (db.patients/update-patient! db patient-id patient)
          [::response/no-content]))))

(defmethod ig/init-key ::delete-patient [_ {:keys [db]}]
  (fn [{[_ patient-id] :ataraxy/result}]
    (if-not (db.patients/find-patient-by-id db patient-id)
      [::response/not-found {:message "Not Found"}]
      (do (db.patients/delete-patient! db patient-id)
          [::response/no-content]))))
