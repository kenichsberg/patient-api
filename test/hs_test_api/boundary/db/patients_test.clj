(ns hs-test-api.boundary.db.patients-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [integrant.core :as ig]
            [hs-test-api.boundary.db.patients :as sut]
            [shrubbery.core :refer [stub]]
            [dev :refer [read-config profiles]]))

;(def system ( {}))
;
;(defn init-handler []
;  (ig/init-key :hs-test-api.handler.patients/list-patients {}))
;
;(defn halt-handler [system]
;  (ig/halt-key! :hs-test-api.handler.patients/list-patients system))
;
;(defn init-halt-handler [f]
;  (reset! system (init-handler))
;  (f)
;  (halt-handler @system))
;
;(use-fixtures :once init-halt-handler)

(duct/load-hierarchy)

;;
;; REPL code to check if init system process is done correctly
(comment
  (let [prepped (duct/prep-config (read-config) profiles)
        for-fixture (assoc-in prepped [:duct.server.http/jetty :port] 3333)
        system (ig/init for-fixture)]
    (prn system)
    (prn (:duct.database.sql/hikaricp system))
    (ig/halt! system)))
;;
;;

(defonce system (atom {}))

(defonce port (atom 3333))

(defn init-halt-system [f]
  (let [prepped (duct/prep-config (read-config) profiles)
        for-fixture (assoc-in prepped [:duct.server.http/jetty :port] (swap! port inc))]
    (reset! system (ig/init for-fixture))
    (f)
    (ig/halt! @system)))

(use-fixtures :each init-halt-system)

(def patients
  [{:id 1
    :first_name "John"
    :last_name "Smith"
    :gender true
    :birth "2000-01-01"
    :address "N.Y., US"
    :health_insurance_number "123456789012"}
   {:id 2
    :first_name "Bob"
    :last_name "Smith"
    :gender true
    :birth "1980-01-01"
    :address "N.Y., US"
    :health_insurance_number "223456789012"}
   {:id 3
    :first_name "Will"
    :last_name "Smith"
    :gender true
    :birth "1968-06-15"
    :address "Hollywood, US"
    :health_insurance_number "323456789012"}
   {:id 4
    :first_name "William"
    :last_name "Smith"
    :gender true
    :birth "1980-06-11"
    :address "N.Y., US"
    :health_insurance_number "423456789012"}
   {:id 5
    :first_name "Maggie"
    :last_name "Smith"
    :gender false
    :birth "1934-12-28"
    :address "London, UK"
    :health_insurance_number "523456789012"}
   {:id 6
    :first_name "Tommy"
    :last_name "Lee Jones"
    :gender true
    :birth "1946-09-15"
    :address "Hollywood, US"
    :health_insurance_number "623456789012"}])

(def new-patient
  {:first_name "test"
   :last_name "test"
   :gender false
   :birth "2000-01-01"
   :address "test"
   :health_insurance_number "723456789012"})

(defonce test-id (atom nil))

;;
;;
;; Patient/find-patients
;;
(deftest find-patients-test
  (testing "Returns all records."
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system) nil nil)]
      (is (= patients records))))

  (testing "With keywords 1"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     "  will "
                                     nil)]
      (is (= (filterv #(some #{(:id %)} [3 4]) patients) records))))

  (testing "With keywords 2"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     "smith  will "
                                     nil)]
      (is (= (filterv #(some #{(:id %)} [1 2 3 4 5]) patients) records))))

  (testing "With filter 1"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     nil
                                     [["birth" "gt" "1990-01-01"]])]
      (is (= (filterv #(= 1 (:id %)) patients) records))))

  (testing "With filter 2"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     nil
                                     [["gender" "eq" "false"]])]
      (is (= (filterv #(= 5 (:id %)) patients) records))))

  (testing "With filter 3"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     nil
                                     [["address" "gt" "hollywood,"]])]
      (is (= (filterv #(some #{(:id %)} [3 6]) patients) records))))

  (testing "With keywords & filters"
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system)
                                     " smith  will"
                                     [["birth" "lt" "1990-01-01"]
                                      ["gender" "eq" "true"]
                                      ["address" "gt" "hollywood,"]])]
      (is (= (filterv #(some #{(:id %)} [3]) patients) records)))))

;;
;;
;; Patient/find-patient-by-id
;;
(deftest find-patient-by-id-test
  (testing "With existing id"
    (let [record (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) 1)]
      (is (= (->> patients
                  (filterv #(= 1 (:id %)))
                  first)
             record))))

  (testing "With non-existing id"
    (let [record (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) 99999)]
      (is (= nil record)))))

;;
;;
;; Patient/create-patient!
;; Patient/update-patient!
;; Patient/delete-patient!
;;
(deftest create-patient!
  (testing "create"
    (let [new-id (sut/create-patient! (:duct.database.sql/hikaricp @system)
                                      (update new-patient :gender str))]
      (reset! test-id new-id)
      (is (number? @test-id))
      (is (= 7
             (count (sut/find-patients (:duct.database.sql/hikaricp @system) nil nil))))
      (is (= (assoc new-patient :id @test-id)
             (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) @test-id)))))

  (testing "update"
    (let [updated-patient {:id @test-id
                           :first_name "test2"
                           :last_name "test2"
                           :gender false
                           :birth "2020-02-20"
                           :address "test2"
                           :health_insurance_number "823456789012"}]
      (sut/update-patient! (:duct.database.sql/hikaricp @system)
                           @test-id
                           (update updated-patient :gender str))
      (is (= updated-patient
             (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) @test-id)))))

  (testing "delete"
    (sut/delete-patient! (:duct.database.sql/hikaricp @system)
                         @test-id)
    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system) nil nil)]
      (is (= patients records))
      (is (nil? (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) @test-id))))))

;;;
;;;
;;; Patient/update-patient!
;;;
;(deftest update-patient!
;  (testing "update"
;    (let [updated-patient {:id 1
;                           :first_name "test2"
;                           :last_name "test2"
;                           :gender false
;                           :birth "2020-02-20"
;                           :address "test2"
;                           :health_insurance_number "823456789012"}]
;      (sut/update-patient! (:duct.database.sql/hikaricp @system)
;                           1
;                           (update updated-patient :gender str))
;      (is (= updated-patient
;             (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) 1)))
;
;      (sut/update-patient! (:duct.database.sql/hikaricp @system)
;                           1
;                           (first patients))
;      (is (= (first patients)
;             (sut/find-patient-by-id (:duct.database.sql/hikaricp @system) 1))))))
;
;;;
;;;
;;; Patient/delete-patient!
;;;
;(deftest delete-patient!
;  (testing "delete"
;    (sut/delete-patient! (:duct.database.sql/hikaricp @system)
;                         @test-id)
;    (let [records (sut/find-patients (:duct.database.sql/hikaricp @system) nil nil)]
;      (is (= patients records)))))

;;
;;
;; Mock (for handlers which use this boundary)
;;
(def mock-patients patients)

(def mock-db
  (stub sut/Patient
        {:find-patients mock-patients
         :find-patient-by-id (first mock-patients)
         :create-patient! nil
         :update-patient! nil
         :delete-patient! nil}))
