(ns hs-test-api.boundary.db.patients
  (:refer-clojure :exclude [filter for group-by into partition-by set update])
  (:require [clojure.spec.alpha :as s]
            [duct.database.sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :refer [select from where insert-into values update set delete-from] :as h]
            [clojure.core :as c]
            [clojure.string :as cstr]))

(s/def ::id nat-int?)
(s/def ::first_name string?)
(s/def ::last_name string?)
(s/def ::gender boolean?)
(s/def ::birth string?)
(s/def ::address string?)
(s/def ::health_insurance_number string?)
(s/def ::patient
  (s/keys :req-un [::id
                   ::first_name
                   ::last_name
                   ::gender
                   ::birth
                   ::address
                   ::health_insurance_number]))

(s/def ::field string?)
(s/def ::operator (s/nilable string?))
(s/def ::value string?)
(s/def ::filter
  (s/keys :req-un [::field
                   ::operator
                   ::value]))

(s/def ::row-count nat-int?)

(s/fdef find-patients
  :args (s/cat :db any?
               :keyword-str string?
               :filter-vec (s/coll-of ::filter))
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
  (find-patients [db keyword-str filter-vec])
  (find-patient-by-id [db id])
  (create-patient! [db patient])
  (update-patient! [db id patient])
  (delete-patient! [db id]))

(defn get-datasource [db]
  (-> db :spec :datasource))

(def jdbc-opts
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn patch-date-field [m k]
  (c/update m k #(vector :to_date % "YYYY-MM-DD")))

(defn keywordstr->whereclause [keyword-str]
  (if (empty? keyword-str)
    [:= true true]
    (let [keyword-vec (cstr/split keyword-str #" ")]
      (->> keyword-vec
           (map cstr/lower-case)
           (reduce #(conj %1 [:like
                              [:lower
                               [:concat :first_name
                                " "
                                :last_name
                                :health_insurance_number]]
                              (str "%" %2 "%")])
                   [:or])))))

;(defmacro compare-date-statement [field operator value]
;  `[~@(case operator
;        "eq" :=
;        "gt" :>
;        "lt" :<)
;    ~field
;    [:to_date ~value "YYYY-MM-DD"]])

(defn compare-date [field-kw operator value]
  [(case operator
     "eq" :=
     "gt" :>
     "lt" :<)
   field-kw
   [:to_date value "YYYY-MM-DD"]])

(defn compare-text [field-kw operator value]
  [(case operator
     "eq" :=
     "gt" :like)
   [:lower field-kw]
   [:lower (case operator
            "eq" value
            "gt" (str "%" value "%")
            )]])

;(defn validate-filter-cond [cond-vec]
;  (or (some? (some (-> cond-vec (get 0) list c/set)
;                   ["first_name"
;                    "last_name"
;                    "gender"
;                    "address"
;                    "health_insurance_number"]))
;      (and (= (get cond-vec 0) "birth")
;           (some? (some (-> cond-vec (get 1) list c/set)
;                        ["eq" "gt" "lt"]))
;           ;@TODO check valid date str or not
;           )))
(defn validate-filter-cond [[field operator value]]
  (and
   ;; field
   (some? (some (-> field list c/set)
                ["first_name"
                 "last_name"
                 "gender"
                 "birth"
                 "address"
                 "health_insurance_number"]))
   ;; operator
   (cond
     (= field "birth") (some? (some (-> operator list c/set)
                                    ["eq" "gt" "lt"]))
     (= field "gender") (some? (some (-> operator list c/set)
                                     ["eq"]))
     :else (some? (some (-> operator list c/set)
                        ["eq" "gt"])))
   ;; value
   (cond
     ;@TODO check valid date str or not
     (= field "birth") true
     (= field "gender") (boolean? (read-string value))
     :else (string? value))))

(defn filters->whereclause [filters]
  (if (empty? filters)
    [:= true true]
    (reduce #(if (validate-filter-cond %2)
               (let [[field operator value] %2
                     field-kw (keyword field)]
                 (conj %1
                       (cond
                         (= field-kw :gender) [:= field-kw (read-string value)]
                         (= field-kw :birth) (compare-date field-kw operator value)
                         :else (compare-text field-kw operator value))))
               %1)
            [:and]
            filters)))

(def output-columns
  [:id
   :first_name
   :last_name
   :gender
   [[:cast :birth :text]]
   :address
   :health_insurance_number])

(comment
  (compare-date :birth "gt" "1900-01-01")
  (-> (apply select output-columns)
      (from :patients)
      (where (keywordstr->whereclause nil))
      (where (filters->whereclause [["gender" "eq" "false"]
                                    ["birth" "gt" "1900-01-01"]
                                    ["address" "gt" "NY"]]))
      (sql/format)))

(extend-protocol Patient
  duct.database.sql.Boundary
  (find-patients [db keyword-str filters]
    (println (-> (apply select output-columns)
                 (from :patients)
                 (where (keywordstr->whereclause keyword-str))
                 (where (filters->whereclause filters))
                 (sql/format)))
    (jdbc/execute! (get-datasource db)
                   (-> (apply select output-columns)
                       (from :patients)
                       (where (keywordstr->whereclause keyword-str))
                       (where (filters->whereclause filters))
                       (sql/format))
                   jdbc-opts))

  (find-patient-by-id [db id]
    (jdbc/execute-one! (get-datasource db)
                       (-> (apply select output-columns)
                           (from :patients)
                           (where [:= :id [:cast id :integer]])
                           (sql/format))
                       jdbc-opts))

  (create-patient! [db patient]
    (-> (get-datasource db)
        (jdbc/execute-one!
         (-> (insert-into :patients)
             (values [(patch-date-field patient :birth)])
             (sql/format {:pretty true}))
         jdbc-opts)
        :id))

  (update-patient! [db id patient]
    (jdbc/execute-one! (get-datasource db)
                       (-> (update :patients)
                           (set (-> patient
                                    (dissoc :id)
                                    (patch-date-field  :birth)))
                           (where [:= :id [:cast id :integer]])
                           (sql/format {:pretty true}))
                       jdbc-opts))

  (delete-patient! [db id]
    (jdbc/execute-one! (get-datasource db)
                       (-> (delete-from :patients)
                           (where  [:= :id [:cast id :integer]])
                           (sql/format))
                       jdbc-opts)))
