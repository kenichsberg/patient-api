(ns hs-test-api.handler.patients
  (:require [clojure.string :as str]
            [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [hs-test-api.boundary.db.patients :as db.patients])
  (:import [java.net URLDecoder]))

(defn format-params [params]
  (reduce-kv (fn [m k v]
               (let [k (cond
                         (keyword? k) k
                         (re-find #"\[\]$" k) (-> (str/replace k #"\[\]$" "")
                                                  keyword)
                         :else (keyword k))
                     parse-comma-separated (fn [s]
                                             (cond
                                               (not (string? s)) s
                                               (re-find #"," s) (str/split s #",")
                                               :else s))
                     vec|str->f->v (fn [f vec|str]
                                     (if (vector? vec|str)
                                       (mapv f vec|str)
                                       (f vec|str)))
                     v (and v
                            (vec|str->f->v
                             (comp (partial vec|str->f->v #(URLDecoder/decode %))
                                   parse-comma-separated)
                             v))]
                 (assoc m k v)))
             {}
             params))

(comment
  (format-params {:keywords "foo"
                  "filters[]" nil}))

(defmethod ig/init-key ::list-patients [_ {:keys [db]}]
  (fn [{:keys [params]}]
    (println params)
    (let [{:keys [keywords filters]} (format-params params)]
      (println keywords)
      (println filters)
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
