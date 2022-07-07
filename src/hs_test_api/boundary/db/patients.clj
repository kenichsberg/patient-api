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

(defn validate-cond-map [cond-map]
  (or (some? (some (-> cond-map :field list c/set)
                   ["first_name"
                    "last_name"
                    "gender"
                    "address"
                    "health_insurance_number"]))
      (and (= (cond-map :field) "birth")
           (some? (some (-> cond-map :operator list c/set)
                        ["eq" "gt" "lt"]))
           ;@TODO check valid date str or not
           )))

(defn filtervec->whereclause [filter-vec]
  (if (empty? filter-vec)
    [:= true true]
    (reduce #(if (validate-cond-map %2)
               (let [{:keys [field operator value]} %2
                     field-kw (keyword field)
                     acc %1]
                 (conj acc
                       (cond
                         (= field-kw :gender) [:= field-kw value]
                         (= field-kw :birth) (compare-date
                                              field-kw
                                              operator
                                              value)
                         :else [:= [:lower field-kw]
                                [:lower value]])))
               %1)
            [:and]
            filter-vec)))

(def output-columns
  [:id
   :first_name
   :last_name
   :gender
   [[:cast :birth :text]]
   :address
   :health_insurance_number])

(comment
  (def keyword-str "e")
  (def filter-vec [{:field "gender" :value false}
                   ;{:field "birth" :operator "gt" :value "1900-01-01"}])
                   {:field "last_name" :value "blockwell"}])
  (validate-cond-map {:field "last_name" :value "blockwell"})
  (some? (some #{"gender"}
               ["first_name"
                "last_name"
                "gender"
                "address"
                "health_insurance_number"]))
  (= (-> {:field "last_name" :value "blockwell"}
         :field
         (vector)
         (c/set))
     #{"last_name"})
  (-> {:field "last_name" :value "blockwell"} :field vector set)
  (-> (select :*)
      (from :patients)
      (where [:or [:= :c "c"] [:= :a "a"]])
      ;(where [:= :b "b"] [:= :d "d"])
      ;(apply where [[:= :b "b"] [:= :d "d"]])
      (where [:and [:= :b "b"] [:= :d "d"]])
      (sql/format))
  (-> (apply select output-columns)
      (from :patients)
      (where (keywordstr->whereclause nil))
      (where (filtervec->whereclause [{:field "gender" :value false}
                                      {:field "last_name" :value "blockwell"}]))
      (sql/format)))

(extend-protocol Patient
  duct.database.sql.Boundary
  (find-patients [db keyword-str filter-vec]
    (jdbc/execute! (get-datasource db)
                   (-> (apply select output-columns)
                       (from :patients)
                       (where (keywordstr->whereclause keyword-str))
                       (where (filtervec->whereclause filter-vec))
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
