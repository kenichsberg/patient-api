(ns hs-test-api.handler.patients
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [hs-test-api.boundary.db.patients :as db.patients]))

(defn params->vec [params]
  (loop [cnt 0
         acc []]
    (let [field (get params (keyword (str "f-" cnt)))
          operator (get params (keyword (str "o-" cnt)))
          value (get params (keyword (str "v-" cnt)))]
      (if (nil? field)
        acc
        (recur (inc cnt) (conj acc
                               {:field field
                                :operator operator
                                :value value}))))))

(defmethod ig/init-key ::list-patients [_ {:keys [db]}]
  (fn [{{:keys [keywords] :as params} :params}]
    (let [filter-vec (params->vec params)]
      [::response/ok (db.patients/find-patients db keywords filter-vec)])))

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
