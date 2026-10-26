(ns {{ project-name }}.test-utils
  (:require [clj-reload.core :as reload]
            [integrant.core :as ig]
            [{{ project-name }}.db :as db]
            [{{ project-name }}.server :as server]
            [{{ project-name }}.utils.config :as config]))

(def ^:dynamic *test-system* nil)

(defn with-system
  "Run the test system before tests."
  ([]
   (with-system nil))
  ([config-path]
   (fn
     [test-fn]
     (let [test-config (config/get-config :test config-path)]
       (ig/load-namespaces test-config)
       (reload/reload)
       (binding [*test-system* (ig/init test-config)]
         (try
           (test-fn)
           (finally
             (ig/halt! *test-system*))))))))

(defn with-truncated-tables
  "Remove all data from all tables except migrations."
  [f]
  (let [db (::db/db *test-system*)]
    (doseq [table (->> {:select [:name]
                        :from [:sqlite_master]
                        :where [:= :type "table"]}
                       (db/exec! db)
                       (map (comp keyword :name)))
            :when (not= :ragtime_migrations table)]
      (db/exec! db {:delete-from table}))
    (f)))

(defn db
  "Get the database connection from the test system."
  []
  (::db/db *test-system*))

(defn server
  "Get the server instance from the test system."
  []
  (::server/server *test-system*))

(defn server-url
  "Return the base url of the test server, optionally joined with a path."
  ([]
   (server-url ""))
  ([path]
   (str "http://localhost:" (.getLocalPort (first (.getConnectors (server)))) path)))
