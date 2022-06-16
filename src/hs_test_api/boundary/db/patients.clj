(ns hs-test-api.boundary.db.patients
  (:refer-clojure :exclude [filter for group-by into partition-by set update])
  (:require [clojure.spec.alpha :as s]
            [duct.database.sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :refer :all :as h]
            [clojure.core :as c]
            [java-time :as jt])
  (:import java.util.Date))

(s/def ::health_insurance_number string?)
(s/def ::first_name string?)
(s/def ::middle_name string?)
(s/def ::last_name string?)
(s/def ::gender boolean?)
(s/def ::birth string?)
(s/def ::address string?)

(s/def ::patient
  (s/keys :req-un [::health_insurance_number
                   ::first_name
                   ::middle_name
                   ::last_name
                   ::gender
                   ::birth
                   ::address]))
(s/def ::row-count nat-int?)

(s/fdef find-patients
  :arg (s/cat :db any?)
  :ret (s/coll-of ::patient))

(s/fdef find-patient-by-id
  :args (s/cat :db any?
               :id ::health_insurance_number)
  :ret (s/nilable ::patient))

(s/fdef create-patient!
  :args (s/cat :db any?
               :patient (s/keys :req-un [::patient]))
  :ret ::health_insurance_number)

(s/fdef update-patient!
  :args (s/cat :db any?
               :id ::health_insurance_number
               :patient (s/keys :req-un [::patient]))
  :ret ::health_insurance_number)

(s/fdef delete-patient!
  :args (s/cat :db any?
               :id ::health_insurance_number)
  :ret ::row-count)

(defprotocol Patient
  (find-patients [db])
  (find-patient-by-id [db id])
  (create-patient! [db patient])
  (update-patient! [db id patient])
  (delete-patient! [db id]))

(defn get-datasource [db]
  (-> db :spec :datasource))

(def jdbc-opts
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

;(defn inst-date-fields [m ks]
;  (update-in m ks jt/zoned-date-time))
(defn inst-date-field [m k]
  (c/update m k #(vector :to_date % "YYYY-MM-DD")))

(extend-protocol Patient
  duct.database.sql.Boundary
  (find-patients [db]
    (jdbc/execute! (get-datasource db)
                   (-> (select :*)
                       (from :patients)
                       (sql/format))
                   jdbc-opts))

  (find-patient-by-id [db id]
    (jdbc/execute-one! (get-datasource db)
                       (-> (select :*)
                           (from :patients)
                           (where [:= :health_insurance_number id])
                           (sql/format))
                       jdbc-opts))

  (create-patient! [db patient]
    (-> (get-datasource db)
        (jdbc/execute-one!
         (-> (insert-into :patients)
             (values [(inst-date-field patient :birth)])
             (sql/format {:pretty true}))
         jdbc-opts)
        :health_insurance_number))

  (update-patient! [db id patient]
    (jdbc/execute-one! (get-datasource db)
                       (-> (update :patients)
                           (set (-> patient
                                    (dissoc :health_insurance_number)
                                    (inst-date-field  :birth)))
                           (where [:= :health_insurance_number id])
                           (sql/format {:pretty true}))
                       jdbc-opts))

  (delete-patient! [db id]
    (jdbc/execute-one! (get-datasource db)
                       (-> (delete-from :patients)
                           (where  [:= :health_insurance_number id])
                           (sql/format))
                       jdbc-opts)))

(comment
  (def id 1)
  (def patient {::health_insurance_number 1
                ::first_name "bob"
                ::middle_name ""
                ::last_name "sponge"
                ::gender true
                ::birth "1999-09-09"
                ::address "222 abc st."}))
