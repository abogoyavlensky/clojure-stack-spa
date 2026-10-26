(ns {{ project-name }}.api-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [{{ project-name }}.test-utils :as utils]))

(use-fixtures :once
  (utils/with-system))

(use-fixtures :each
  utils/with-truncated-tables)

(defn- get-json
  [path]
  (http/get (utils/server-url path) {:as :json
                                     ; parse JSON for error responses too
                                     :coerce :always
                                     :throw-exceptions false}))

(deftest test-health-check
  (let [response (get-json "/health")]
    (is (= 200 (:status response)))
    (is (= {:status "ok"} (:body response)))))

(deftest test-status-reads-from-database
  (let [{:keys [status body]} (get-json "/api/status")]
    (is (= 200 status))
    (is (= "ok" (:status body)))
    (testing "keys are snake_case in JSON"
      (is (re-matches #"\d+\.\d+\.\d+" (:sqlite_version body))))))

(deftest test-unknown-api-route-returns-json-404
  (let [{:keys [status body]} (get-json "/api/unknown")]
    (is (= 404 status))
    (is (= {:error "Not found"} body))))

(deftest test-missing-asset-is-not-served-as-index
  (let [response (http/get (utils/server-url "/assets/missing.js") {:throw-exceptions false})]
    (is (= 404 (:status response)))))

(deftest test-client-side-route-renders-index-or-build-hint
  (let [response (http/get (utils/server-url "/some/client/route") {:throw-exceptions false})]
    ; 200 when the frontend is built into resources/public, 503 otherwise
    (is (contains? #{200 503} (:status response)))
    (is (re-find #"text/html" (get-in response [:headers "Content-Type"])))))

