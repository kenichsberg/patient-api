(ns hs-test-api.handler.patients-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [integrant.core :as ig]
            [ring.mock.request :as mock]
            [hs-test-api.boundary.db.patients :as db.patients]
            [hs-test-api.handler.patients :as patients]
            [hs-test-api.boundary.db.patients-test :refer [mock-db
                                                           mock-patients]]))

;(def handler-atom (atom {}))
;
;(defn init-handler []
;  (ig/init-key :hs-test-api.handler.patients/list-patients {}))
;
;(defn halt-handler [system]
;  (ig/halt-key! :hs-test-api.handler.patients/list-patients system))
;
;(defn create-fixture [k db]
;  (fn [f]
;    (reset! handler-atom (ig/init-key k {:db db}))
;    (f)
;    (ig/halt-key! @handler-atom)))

(comment
  (mock/request :get "/patients?filters%5B%5D=gender%2Ceq%2Cfalse")
  (mock/request :get "/patients/1")
  (let [handler (ig/init-key :hs-test-api.handler.patients/list-patients {:db mock-db})]
    (prn handler)
    (prn (handler {:params {:keywords "1"}}))
    (prn (handler (mock/request :get "/patients?filters%5B%5D=gender%2Ceq%2Cfalse")))
    (ig/halt-key! :hs-test-api.handler.patients/list-patients handler)))

;;
;;
;; :list-patients
;;
(deftest list-patients-test
  (testing "Response OK"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (mock/request :get "/patients"))]
      (is (= :ataraxy.response/ok (first response)))
      (is (= mock-patients (second response)))))

  (testing "Response OK with query string 5"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (merge (mock/request :get "/patients?keywords=smith%20%20will%20&filters%5B%5D=birth%2Clt%2C1990-01-01&filters%5B%5D=gender%2Ceq%2Ctrue&filters%5B%5D=address%2Cgt%2Chollywood%252C")
                                   {:params {:keywords "smith  will "
                                             "filters[]" ["birth,lt,1990-01-01"
                                                          "gender,eq,true"
                                                          "address,gt,hollywood%2C"]}}))]
      (is (= :ataraxy.response/ok (first response)))))

  (testing "Bad Request 1"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (merge (mock/request :get "/patients?filters%5B%5D=gender%2Ceq%2Cfoo")
                                   {:params {:keywords "smith  will "
                                             "filters[]" ["gender,eq,foo"]}}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response)))))

  (testing "Bad Request 2"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (merge (mock/request :get "/patients?filters%5B%5D=true;%2Ceq%2Cfoo")
                                   {:params {:keywords "smith  will "
                                             "filters[]" ["true;,eq,foo"]}}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response)))))

  (testing "Bad Request 3"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (merge (mock/request :get "/patients?filters%5B%5D=birth%2Ceq%2C2000-02-31")
                                   {:params {:keywords "smith  will "
                                             "filters[]" ["birth,eq,2000-02-31"]}}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response)))))

  (testing "Bad Request 4"
    (let [handler  (ig/init-key :hs-test-api.handler.patients/list-patients
                                {:db mock-db})
          response (handler (merge (mock/request :get "/patients?filters%5B%5D=gender%2Cgt%2Ctrue")
                                   {:params {:keywords "smith  will "
                                             "filters[]" ["gender,gt,true"]}}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response))))))

;;
;;
;; :fetch-patient
;;
(deftest fetch-patient-test
  (testing "Response OK"
    (let [handler (ig/init-key :hs-test-api.handler.patients/fetch-patient
                               {:db mock-db})
          response (handler (merge (mock/request :get "/patients/1")
                                   {:ataraxy/result [nil "1"]}))]
      (is (= :ataraxy.response/ok (first response)))))

  (testing "Bad Request"
    (let [handler (ig/init-key :hs-test-api.handler.patients/fetch-patient
                               {:db mock-db})
          response (handler (merge (mock/request :get "/patients/foo")
                                   {:ataraxy/result [nil "foo"]}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response))))))

;;
;;
;; :create-patient
;;
(deftest create-patient-test
  (testing "Response OK"
    (let [handler (ig/init-key :hs-test-api.handler.patients/create-patient
                               {:db mock-db})
          response (handler (merge (mock/request :post "/patients")
                                   {:ataraxy/result  [nil
                                                      {:first_name "test"
                                                       :last_name "test"
                                                       :gender "true"
                                                       :birth "2020-02-22"
                                                       :address "test"
                                                       :health_insurance_number "123456789012"}]}))]
      (is (= :ataraxy.response/created (first response)))))

  (testing "Bad Request"
    (let [handler (ig/init-key :hs-test-api.handler.patients/create-patient
                               {:db mock-db})
          response (handler (merge (mock/request :post "/patients")
                                   {:ataraxy/result  [nil
                                                      {true "test"
                                                       :last_name "test"
                                                       :gender "true"
                                                       :birth "2020-02-22"
                                                       :address "test"
                                                       :health_insurance_number "123456789012"}]}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response))))))

;;
;;
;; :update-patient
;;
(deftest update-patient-test
  (testing "Response OK"
    (let [handler (ig/init-key :hs-test-api.handler.patients/update-patient
                               {:db mock-db})
          response (handler (merge (mock/request :put "/patients/1")
                                   {:ataraxy/result  [nil
                                                      "1"
                                                      {:first_name "test"
                                                       :last_name "test"
                                                       :gender "true"
                                                       :birth "2020-02-22"
                                                       :address "test"
                                                       :health_insurance_number "123456789012"}]}))]
      (is (= :ataraxy.response/no-content (first response)))))

  (testing "Bad Request"
    (let [handler (ig/init-key :hs-test-api.handler.patients/update-patient
                               {:db mock-db})
          response (handler (merge (mock/request :put "/patients/1")
                                   {:ataraxy/result  [nil
                                                      "1"
                                                      {:first_name "test"
                                                       :last_name "test"
                                                       :gender "true"
                                                       :birth "2020-02-22"
                                                       :address "test"
                                                       :health_insurance_number "foo"}]}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response))))))

;;
;;
;; :delete-patient
;;
(deftest delete-patient-test
  (testing "Response OK"
    (let [handler (ig/init-key :hs-test-api.handler.patients/delete-patient
                               {:db mock-db})
          response (handler (merge (mock/request :delete "/patients/1")
                                   {:ataraxy/result [nil "1"]}))]
      (is (= :ataraxy.response/no-content (first response)))))

  (testing "Bad Request"
    (let [handler (ig/init-key :hs-test-api.handler.patients/delete-patient
                               {:db mock-db})
          response (handler (merge (mock/request :delete "/patients/foo")
                                   {:ataraxy/result [nil "foo"]}))]
      (is (= :ataraxy.response/bad-request (first response)))
      (is (empty? (rest response))))))
