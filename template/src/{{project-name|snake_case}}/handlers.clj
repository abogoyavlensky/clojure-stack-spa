(ns {{ project-name }}.handlers
  (:require [ring.util.response :as response]
            [{{ project-name }}.db :as db]))

(defn health-handler
  [_]
  (response/response {:status "ok"}))

(defn status-handler
  "Return backend status with the SQLite version read from the database."
  [request]
  (let [db (get-in request [:context :db])
        {:keys [version]} (db/exec-one! db {:select [[[:sqlite_version] :version]]})]
    (response/response {:status "ok"
                        :sqlite-version version})))
