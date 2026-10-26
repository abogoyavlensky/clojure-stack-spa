(ns {{ project-name }}.utils.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu])
  (:import [clojure.lang IFn]
           [java.net ServerSocket]))

(def CONFIG-DEFAULT "config.edn")

; Add #ig/ref tag for reading integrant config from aero.
(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

; Add #free-port tag to pick a random free port, useful for tests.
(defmethod aero/reader 'free-port
  [_ _ _value]
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn get-config
  "Return edn config with all variables set."
  ([profile]
   (get-config profile nil))
  ([profile config-path]
   (-> (or config-path CONFIG-DEFAULT)
       (io/resource)
       (aero/read-config {:profile profile
                          :resolver aero/resource-resolver}))))

(defn read-config
  "Read config file, load namespaces for Integrant component and return config map."
  ([profile]
   (read-config profile nil))
  ([profile config-path]
   (let [config (get-config profile config-path)]
     (ig/load-namespaces config)
     config)))

(defn validate-schema!
  "Validate data against schema and throw a humanized error if data is not valid."
  [{:keys [component schema data]}]
  (some-> schema
          (mu/closed-schema)
          (m/explain data)
          (me/with-spell-checking)
          (me/humanize)
          (#(throw (Exception. (format "Invalid %s component config: %s" component %))))))

; System

(defn at-shutdown
  "Add hook for shutdown system on sigterm."
  [system]
  (-> (Runtime/getRuntime)
      (.addShutdownHook
        (Thread. ^IFn (bound-fn []
                        (log/info "[SYSTEM] System is stopping...")
                        (ig/halt! system)
                        (shutdown-agents)
                        (log/info "[SYSTEM] System has been stopped."))))))

(defn run-system
  "Run application system."
  [{:keys [profile config-path]}]
  (log/info "[SYSTEM] System is starting with profile:" profile)
  (-> (read-config profile config-path)
      (ig/init)
      (at-shutdown))
  (log/info "[SYSTEM] System has been started successfully."))
