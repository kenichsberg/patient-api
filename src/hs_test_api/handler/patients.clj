(ns hs-test-api.handler.patients
  (:require [clojure.string :as str]
            [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [hs-test-api.boundary.db.patients :as db.patients])
  (:import [java.net URLDecoder]))

(defn format-params [params]
  (->> params
       (map (fn [[k v]]
              (let [k (cond
                        (keyword? k) k
                        (some? (re-find #"\[\]$" k)) (-> (str/replace k #"\[\]$" "")
                                                         keyword)
                        :else (keyword k))
                    v (if (not (vector? v))
                        v
                        (mapv (fn [v]
                                (let [v (URLDecoder/decode v)]
                                  (if (not (str/includes? v ","))
                                    (URLDecoder/decode v)
                                    (->> (str/split v #",")
                                         (mapv #(URLDecoder/decode %))))))
                              v))]
                [k v])))
       (reduce #(merge %1 (apply hash-map %2)) {})))

(comment
  (format-params {:keywords "el"
                  "filters[]" ["gender,eq,true"
                               "address,gt,n.y."]}))

(defmethod ig/init-key ::list-patients [_ {:keys [db]}]
  (fn [{:keys [params]}]
    (let [{:keys [keywords filters]} (format-params params)]
      [::response/ok (db.patients/find-patients db keywords filters)])))

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
