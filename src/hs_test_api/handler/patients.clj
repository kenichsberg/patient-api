(ns hs-test-api.handler.patients
  (:refer-clojure :exclude [filter for group-by into partition-by set update])
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :refer :all :as h]
            [clojure.core :as c]
            [hs-test-api.boundary.db.patients :as db.patients]))

(defn inst-date-field [m k]
  (c/update m k #(vector :to_date % "YYYY-MM-DD")))

(defn get-datasource [db]
  (-> db :spec :datasource))

(def jdbc-opts
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defmethod ig/init-key ::list-patients [_ {:keys [db]}]
  (fn [_]
    [::response/ok (db.patients/find-patients db)]))

(defmethod ig/init-key ::create-patient [_ {:keys [db]}]
  (fn [{[_ patient] :ataraxy/result}]
    (let [patient-id (db.patients/create-patient! db patient)]
      [::response/created (str "/patients/" patient-id) (db.patients/find-patient-by-id db patient-id)])))

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
