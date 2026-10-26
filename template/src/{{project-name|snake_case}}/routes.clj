(ns {{ project-name }}.routes
  (:require [{{ project-name }}.handlers :as handlers]))

(def routes
  [["/health" {:name ::health-check
               :get {:handler handlers/health-handler
                     :responses {200 {:body [:map
                                             [:status :string]]}}}}]
   ["/api"
    ["/status" {:name ::status
                :get {:handler handlers/status-handler
                      :responses {200 {:body [:map
                                              [:status :string]
                                              [:sqlite-version :string]]}}}}]]])
