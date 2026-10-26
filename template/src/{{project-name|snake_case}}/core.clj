(ns {{ project-name }}.core
  (:gen-class)
  (:require [{{ project-name }}.utils.config :as config]))

(defn -main
  "Run application system in production."
  []
  (config/run-system {:profile :prod
                      :config-path "config.edn"}))
