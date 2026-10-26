(ns {{ project-name }}.server
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [muuntaja.core :as muuntaja-core]
            [reitit.coercion.malli :as coercion-malli]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as ring-parameters]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cookies :as ring-cookies]
            [ring.middleware.default-charset :as default-charset]
            [ring.middleware.not-modified :as not-modified]
            [ring.middleware.session :as ring-session]
            [ring.middleware.session.cookie :as ring-session-cookie]
            [ring.middleware.ssl :as ring-ssl]
            [ring.middleware.x-headers :as x-headers]
            [{{ project-name }}.routes :as app-routes]
            [{{ project-name }}.utils.config :as config]
            [{{ project-name }}.utils.server :as server-utils])
  (:import [com.zaxxer.hikari HikariDataSource]))

(defmethod ig/assert-key ::server
  [_ params]
  (config/validate-schema!
    {:component ::server
     :data params
     :schema [:map
              [:options
               [:map
                [:port pos-int?]
                [:session-secret-key string?]
                [:cookie-attrs-secure? boolean?]
                [:allowed-origins [:vector string?]]
                [:expose-errors? boolean?]
                [:auto-reload? boolean?]
                [:cache-assets? {:optional true} boolean?]
                [:cache-control {:optional true} string?]]]
              [:db [:fn
                    {:error/message "Invalid datasource type"}
                    #(instance? HikariDataSource %)]]]}))

; JSON uses snake_case keys, Clojure code uses kebab-case keywords.
(def ^:private muuntaja-instance
  (muuntaja-core/create
    (-> muuntaja-core/default-options
        (assoc-in [:formats "application/json" :decoder-opts]
                  {:decode-key-fn #(keyword (str/replace % "_" "-"))})
        (assoc-in [:formats "application/json" :encoder-opts]
                  {:encode-key-fn #(str/replace (name %) "-" "_")}))))

; Validation errors are returned as {:humanized {:field ["message"]}}.
(def ^:private coercion
  (coercion-malli/create
    (assoc coercion-malli/default-options :error-keys #{:type :in :humanized})))

(defn- json-error-handler
  [status message]
  (constantly {:status status
               :headers {"Content-Type" "application/json; charset=utf-8"}
               :body (format "{\"error\":\"%s\"}" message)}))

(defn ring-handler
  "Return main application handler: the JSON API and the SPA's static files."
  [{:keys [options]
    :as context}]
  (let [session-store (ring-session-cookie/cookie-store
                        {:key (server-utils/string->16-byte-array
                                (:session-secret-key options))})]
    (ring/ring-handler
      (ring/router
        app-routes/routes
        {:exception pretty/exception
         :data {:muuntaja muuntaja-instance
                :coercion coercion
                :middleware [[server-utils/wrap-cors (:allowed-origins options)]
                             [x-headers/wrap-content-type-options :nosniff]
                             [x-headers/wrap-frame-options :sameorigin]
                             ring-ssl/wrap-hsts
                             not-modified/wrap-not-modified
                             [default-charset/wrap-default-charset "utf-8"]
                             ; add handler options to request
                             [server-utils/wrap-context context]
                             ; parse query and form parameters
                             ring-parameters/parameters-middleware
                             ; negotiate request and response
                             muuntaja/format-middleware
                             ; reject cross-site form submissions
                             server-utils/wrap-csrf-protection
                             ; handle exceptions
                             (server-utils/exception-middleware options)
                             ; coerce request and response to spec
                             ring-coercion/coerce-exceptions-middleware
                             ring-coercion/coerce-request-middleware
                             ring-coercion/coerce-response-middleware]}})
      (ring/routes
        ; Vite puts hashed JS/CSS under /assets/, so they can be cached forever
        (server-utils/create-resource-handler-cached {:path "/assets/"
                                                      :root "public/assets"
                                                      :cached? (:cache-assets? options)
                                                      :cache-control (:cache-control options)})
        (ring/redirect-trailing-slash-handler)
        ; any other GET outside /api renders the SPA
        (server-utils/create-index-handler)
        (ring/create-default-handler {:not-found (json-error-handler 404 "Not found")
                                      :method-not-allowed (json-error-handler 405 "Method not allowed")
                                      :not-acceptable (json-error-handler 406 "Not acceptable")}))
      ; Session middleware at ring-handler level so both router routes
      ; and fallback handlers (index page) share the same session
      {:middleware [ring-cookies/wrap-cookies
                    [ring-session/wrap-session
                     {:cookie-attrs {:secure (:cookie-attrs-secure? options)
                                     :http-only true
                                     :same-site :lax}
                      :store session-store}]]})))

(defmethod ig/init-key ::server
  [_ {:keys [options]
      :as context}]
  (log/info "[SERVER] Starting server...")
  (let [handler-fn #(ring-handler context)
        handler (if (:auto-reload? options)
                  (server-utils/wrap-reload handler-fn)
                  (handler-fn))]
    (server-utils/set-default-exception-handler!)
    (jetty/run-jetty handler {:port (:port options)
                              :host "0.0.0.0"
                              :join? false})))

(defmethod ig/halt-key! ::server
  [_ server]
  (log/info "[SERVER] Stopping server...")
  (.stop server))
