(ns {{ project-name }}.vite
  "Run the Vite dev server as part of the development system, so `(reset)`
  starts both the backend and the frontend with hot reload."
  (:require [clojure.java.io :as io]
            [clojure.java.process :as process]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [{{ project-name }}.utils.config :as config])
  (:import [java.lang ProcessHandle]
           [java.util.concurrent TimeUnit]))

(defmethod ig/assert-key ::dev-server
  [_ params]
  (config/validate-schema!
    {:component ::dev-server
     :data params
     :schema [:map
              [:dir string?]]}))

(defmethod ig/init-key ::dev-server
  [_ {:keys [dir]}]
  ; Run the vite binary directly rather than via npm, so stopping the
  ; process doesn't leave an orphaned node process holding the port.
  (if (.exists (io/file dir "node_modules/.bin/vite"))
    (do
      (log/info "[VITE] Starting dev server at http://localhost:5173 ...")
      (process/start {:dir dir
                      :out :inherit
                      :err :inherit}
                     "node_modules/.bin/vite"))
    (log/warn "[VITE] Frontend dependencies are not installed, run `bb frontend-install`.")))

(defmethod ig/halt-key! ::dev-server
  [_ ^Process proc]
  (when (some? proc)
    (log/info "[VITE] Stopping dev server...")
    ; Vite spawns helper processes (esbuild), stop them too
    (doseq [child (iterator-seq (.iterator (.descendants proc)))]
      (.destroy ^ProcessHandle child))
    (.destroy proc)
    (.waitFor proc 5 TimeUnit/SECONDS)))
