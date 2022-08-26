(ns hs-test-api.boundary.db.patients
  (:refer-clojure :exclude [filter for group-by into partition-by set update])
  (:require [clojure.spec.alpha :as s]
            [duct.database.sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :refer [select from where order-by insert-into values update set delete-from] :as h]
            [clojure.core :as c]
            [clojure.string :as str])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter
                             DateTimeParseException
                             ResolverStyle)))

;;
;;
;; Protocol definition
;;
(defprotocol Patient
  (find-patients [db keyword-str filter-vec])
  (find-patient-by-id [db id])
  (create-patient! [db patient])
  (update-patient! [db id patient])
  (delete-patient! [db id]))

;;
;;
;; Specs
;;
;(s/def ::id nat-int?)
(s/def ::id (s/and string?
                   #(->> %
                         str
                         (re-matches #"\d+")
                         some?)))
(s/def ::first_name string?)
(s/def ::last_name string?)
(s/def ::gender (s/and string?
                       #(->> %
                             str
                             (re-matches #"(true|false)")
                             some?)))
(s/def ::birth #(try
                  (. LocalDate parse
                     %
                     (-> (. DateTimeFormatter ofPattern "uuuu-M-d")
                         (.withResolverStyle (. ResolverStyle STRICT))))
                  true
                  (catch DateTimeParseException _
                    false)))
(s/def ::address string?)
(s/def ::health_insurance_number (s/and string?
                                        #(->> %
                                              str
                                              (re-matches #"\d{12}")
                                              some?)))
(s/def ::patient
  (s/keys :req-un [::id
                   ::first_name
                   ::last_name
                   ::gender
                   ::birth
                   ::address
                   ::health_insurance_number]))
(s/def ::patient-without-id
  (s/keys :req-un [::first_name
                   ::last_name
                   ::gender
                   ::birth
                   ::address
                   ::health_insurance_number]))

(s/def ::field #(some?
                 (some #{%}
                       ["first_name"
                        "last_name"
                        "gender"
                        "birth"
                        "address"
                        "health_insurance_number"])))
(s/def ::operator #(some?
                    (some #{%} ["eq" "gt" "lt"])))
(s/def ::value string?)
(s/def ::filter (s/tuple ::field ::operator ::value))

(s/def ::filters
  (s/nilable (s/coll-of ::filter :kind vector?)))

(comment
  (s/valid? ::filters
            [["gender" "eq" ""]])
  (s/explain ::filters
             [["birth" "lt" "1990-01-01"] ["gender" "eq" "true"] ["address" "gt" "hollywood,"]]))

(s/def ::row-count nat-int?)

;;
;; s/fdef
(s/fdef find-patients
  :args (s/cat :db any?
               :keyword-str string?
               :filter-vec ::filters)
  :ret (s/coll-of ::patient))

(s/fdef find-patient-by-id
  :args (s/cat :db any?
               :id ::id)
  :ret (s/nilable ::patient))

(s/fdef create-patient!
  :args (s/cat :db any?
               :patient (s/keys :req-un [::patient]))
  :ret ::health_insurance_number)

(s/fdef update-patient!
  :args (s/cat :db any?
               :id ::id
               :patient (s/keys :req-un [::patient]))
  :ret ::health_insurance_number)

(s/fdef delete-patient!
  :args (s/cat :db any?
               :id ::id)
  :ret ::row-count)

;;
;;
;; Validators
;;
(defn validate-filter-cond [[field operator value]]
  (and
   ;; field
   (some #{field}
         ["first_name"
          "last_name"
          "gender"
          "birth"
          "address"
          "health_insurance_number"])
   ;; operator
   (condp = field
     "gender" (some #{operator} ["eq"])
     "birth" (some #{operator} ["eq" "gt" "lt"])
     (some #{operator} ["eq" "gt"]))
   ;; value
   (condp = field
     "gender" (s/valid? ::gender value)
     "birth" (s/valid? ::birth value)
     "health_insurance_number" (s/valid? ::health_insurance_number value)
     (string? value))))

(defn validate-keywords [keywords]
  (or (nil? keywords)
      (string? keywords)))

(defn validate-filters [filters]
  (or (nil? filters)
      (reduce #(and %1 (validate-filter-cond %2))
              true
              filters)))

(defmulti validate-params (fn [k _] k))
(defmethod validate-params :list-patients
  [_ [keywords filters]]
  (and (validate-keywords keywords)
          ;(s/valid? ::filters filters)
       (validate-filters filters)))
(defmethod validate-params :fetch-patient
  [_ [id]]
  (s/valid? ::id id))
(defmethod validate-params :create-patient
  [_ [patient]]
  (s/valid? ::patient-without-id patient))
(defmethod validate-params :update-patient
  [_ [id patient]]
  (and
   (s/valid? ::id id)
   (s/valid? ::patient-without-id patient)))
(defmethod validate-params :delete-patient
  [_ [id]]
  (s/valid? ::id id))

;;
;;
;; Functions and variables for protocol implementation
;;
(defn get-datasource [db]
  (-> db :spec :datasource))

(def jdbc-opts
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn patch-boolean-field [m k]
  (c/update m k #(Boolean/valueOf %)))

(defn patch-date-field [m k]
  (c/update m k #(vector :to_date % "YYYY-MM-DD")))

(defn keywordstr->whereclause [keyword-str]
  (if (empty? keyword-str)
    [:= true true]
    (let [keyword-vec (-> (str/trim keyword-str)
                          (str/replace #"\s+" " ")
                          (str/split  #" "))]
      (->> keyword-vec
           (map str/lower-case)
           (reduce #(conj %1 [:like
                              [:lower
                               [:concat :first_name
                                " "
                                :last_name
                                :health_insurance_number]]
                              (str "%" %2 "%")])
                   [:or])))))

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
             "gt" (str "%" value "%"))]])

(defn filters->whereclause [filters]
  (if (empty? filters)
    [:= true true]
    (reduce #(let [[field operator value] %2
                   field-kw (keyword field)]
               (conj %1
                     (cond
                       (= field-kw :gender) [:= field-kw (Boolean/valueOf value)]
                       (= field-kw :birth) (compare-date field-kw operator value)
                       :else (compare-text field-kw operator value))))
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
  (validate-filter-cond ["address" "gt" "k,"])
  (-> (apply select output-columns)
      (from :patients)
      (where (keywordstr->whereclause nil))
      (where (filters->whereclause [["address" "gt" "k,"]]))
      (sql/format)))

;;
;;
;; Protocol implementation
;;
(extend-protocol Patient
  duct.database.sql.Boundary
  (find-patients [db keyword-str filters]
    (prn (-> (apply select output-columns)
             (from :patients)
             (where (keywordstr->whereclause keyword-str))
             (where (filters->whereclause filters))
             (sql/format)))
    (jdbc/execute! (get-datasource db)
                   (-> (apply select output-columns)
                       (from :patients)
                       (where (keywordstr->whereclause keyword-str))
                       (where (filters->whereclause filters))
                       (order-by :id)
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
             (values [(-> patient
                          (patch-boolean-field :gender)
                          (patch-date-field :birth))])
             (sql/format {:pretty true}))
         jdbc-opts)
        :id))

  (update-patient! [db id patient]
    (prn (-> (update :patients)
             (set (-> patient
                      (dissoc :id)
                      (patch-boolean-field :gender)
                      (patch-date-field  :birth)))
             (where [:= :id [:cast id :integer]])
             (sql/format {:pretty true})))
    (jdbc/execute-one! (get-datasource db)
                       (-> (update :patients)
                           (set (-> patient
                                    (dissoc :id)
                                    (patch-boolean-field :gender)
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
