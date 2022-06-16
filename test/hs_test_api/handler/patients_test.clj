(ns hs-test-api.handler.patients-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [ring.mock.request :as mock]
            [hs-test-api.handler.patients :as patients]))

(deftest smoke-test
  (testing "patients page exists"
    (let [handler  (ig/init-key :hs-test-api.handler/patients {})
          response (handler (mock/request :get "/patients"))]
      (is (= :ataraxy.response/ok (first response)) "response ok"))))
