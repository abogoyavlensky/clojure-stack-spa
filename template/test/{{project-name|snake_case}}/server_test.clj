(ns {{ project-name }}.server-test
  (:require [clojure.test :refer :all]
            [{{ project-name }}.utils.server :as server-utils]))

(def ^:private ok-handler
  (constantly {:status 200
               :headers {}
               :body "ok"}))

(deftest test-csrf-protection
  (let [handler (server-utils/wrap-csrf-protection ok-handler)]
    (testing "safe methods pass"
      (is (= 200 (:status (handler {:request-method :get})))))
    (testing "unsafe method without JSON or X-Requested-With is rejected"
      (is (= 403 (:status (handler {:request-method :post
                                    :headers {"content-type" "application/x-www-form-urlencoded"}})))))
    (testing "JSON body passes"
      (is (= 200 (:status (handler {:request-method :post
                                    :headers {"content-type" "application/json; charset=utf-8"}})))))
    (testing "X-Requested-With header passes"
      (is (= 200 (:status (handler {:request-method :delete
                                    :headers {"x-requested-with" "fetch"}})))))))

(deftest test-cors
  (let [handler (server-utils/wrap-cors ok-handler ["https://app.example.com"])]
    (testing "listed origin gets CORS headers"
      (is (= "https://app.example.com"
             (get-in (handler {:request-method :get
                               :headers {"origin" "https://app.example.com"}})
                     [:headers "Access-Control-Allow-Origin"]))))
    (testing "preflight from listed origin is answered"
      (is (= 204 (:status (handler {:request-method :options
                                    :headers {"origin" "https://app.example.com"}})))))
    (testing "unknown origin gets no CORS headers"
      (is (nil? (get-in (handler {:request-method :get
                                  :headers {"origin" "https://evil.example.com"}})
                        [:headers "Access-Control-Allow-Origin"]))))))

(deftest test-index-handler
  (let [handler (server-utils/create-index-handler
                  {:resource "{{ project-name | snake_case }}/fixtures/index.html"})]
    (testing "client-side route renders index.html without caching"
      (let [response (handler {:request-method :get
                               :uri "/settings"})]
        (is (= 200 (:status response)))
        (is (= "no-cache" (get-in response [:headers "Cache-Control"])))))
    (testing "API, assets and non-GET requests are not handled"
      (is (nil? (handler {:request-method :get
                          :uri "/api/unknown"})))
      (is (nil? (handler {:request-method :get
                          :uri "/assets/missing.js"})))
      (is (nil? (handler {:request-method :post
                          :uri "/settings"})))))
  (testing "missing build renders a hint"
    (let [handler (server-utils/create-index-handler {:resource "missing/index.html"})]
      (is (= 503 (:status (handler {:request-method :get
                                    :uri "/"})))))))

(deftest test-exception-details-are-hidden-in-prod
  (let [failing-handler (fn [_] (throw (ex-info "secret internals" {:token "x"})))
        wrap (fn [opts]
               ((:wrap (server-utils/exception-middleware opts)) failing-handler))]
    (is (= {:error "Internal server error"}
           (:body ((wrap {:expose-errors? false}) {:request-method :get
                                                   :uri "/api/x"}))))
    (is (= "secret internals"
           (:details (:body ((wrap {:expose-errors? true}) {:request-method :get
                                                            :uri "/api/x"})))))))
