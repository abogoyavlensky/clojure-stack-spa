(ns {{ project-name }}.utils.server
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [ring.middleware.gzip :as gzip]
            [ring.util.response :as response])
  (:import (java.security MessageDigest)))

; Middlewares

(defn wrap-context
  "Add system dependencies of handler to request as a context key."
  [handler context]
  (fn [request]
    (-> request
        (assoc :context context)
        (handler))))

(defn wrap-reload
  "Reload ring handler on every request. Useful in dev mode."
  [f]
  ; require reloader locally to exclude dev dependency from prod build
  (let [reload! ((requiring-resolve 'ring.middleware.reload/reloader) ["src"] true)]
    (fn
      ([request]
       (reload!)
       ((f) request))
      ([request respond raise]
       (reload!)
       ((f) request respond raise)))))

(defn string->16-byte-array
  "Derive a 16-byte key for the session cookie store from a secret string."
  [s]
  (let [digest (MessageDigest/getInstance "MD5")]
    (.digest digest (.getBytes s "UTF-8"))))

(defn- cors-headers
  [origin]
  {"Access-Control-Allow-Origin" origin
   "Access-Control-Allow-Methods" "GET, POST, PUT, PATCH, DELETE, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type, X-Requested-With"
   "Access-Control-Allow-Credentials" "true"
   "Vary" "Origin"})

(defn wrap-cors
  "Allow cross-origin requests from the listed origins only.

  The app's own frontend is served from the same origin as the API, so it
  needs no CORS at all; list origins here only for a frontend hosted on
  another domain. Credentials (the session cookie) are allowed for the listed
  origins, so never list an origin you don't control."
  [handler allowed-origins]
  (let [allowed? (set allowed-origins)]
    (fn [request]
      (let [origin (get-in request [:headers "origin"])]
        (cond
          (not (allowed? origin)) (handler request)

          (= :options (:request-method request))
          {:status 204
           :headers (cors-headers origin)
           :body ""}

          :else (some-> (handler request)
                        (update :headers merge (cors-headers origin))))))))

(def ^:private SAFE-METHODS #{:get :head :options})

(defn- json-request?
  [request]
  (some-> (get-in request [:headers "content-type"])
          (str/starts-with? "application/json")))

(defn wrap-csrf-protection
  "Reject state-changing requests that a plain HTML form on another site could send.

  The session cookie is sent by the browser automatically, so a malicious page
  could submit a form to the API on behalf of a logged-in user. Such a form can
  neither send a JSON body nor set a custom header without a CORS preflight,
  which the server does not allow for unknown origins. So POST, PUT, PATCH and
  DELETE must carry either `Content-Type: application/json` or the
  `X-Requested-With` header (the frontend API client sets it on every request)."
  [handler]
  (fn [request]
    (if (or (contains? SAFE-METHODS (:request-method request))
            (json-request? request)
            (some? (get-in request [:headers "x-requested-with"])))
      (handler request)
      {:status 403
       :body {:error "Forbidden: missing JSON content type or X-Requested-With header"}})))

; Exceptions

(defn- get-error-path
  [exception]
  (mapv
    (comp #(str/join ":" %) :at)
    (:via (Throwable->map exception))))

(defn- unexpected-error-handler
  [expose-errors? exception _request]
  {:status 500
   :body (cond-> {:error "Internal server error"}
           expose-errors? (assoc :details (ex-message exception)
                                 :data (some-> (ex-data exception) (pr-str))
                                 :path (get-error-path exception)))})

(defn- wrap-exception
  [handler e request]
  (log/error e (pr-str (:request-method request) (:uri request)) (ex-message e))
  (handler e request))

(defn exception-middleware
  "Common exception middleware to handle all errors.
  Exception details are added to the response only when `expose-errors?` is
  true, so production clients never see stack paths or internal messages."
  [{:keys [expose-errors?]}]
  (exception/create-exception-middleware
    (merge
      exception/default-handlers
      {; override the default handler
       ::exception/default (partial unexpected-error-handler expose-errors?)
       ; log all exceptions
       ::exception/wrap wrap-exception})))

(defn set-default-exception-handler!
  "Set a default uncaught exception handler that logs error."
  []
  (Thread/setDefaultUncaughtExceptionHandler
    (fn [thread ex]
      (log/error ex "Uncaught exception on" (.getName thread)))))

; Handlers

(def ^:private DEFAULT-CACHE-365D "public,max-age=31536000,immutable")

(defn create-resource-handler-cached
  "Return resource handler with optional Cache-Control header."
  [{:keys [cached? cache-control]
    :or {cached? false}
    :as opts}]
  (letfn [(resource-response-cached-fn
            ([path]
             (resource-response-cached-fn path {}))
            ([path options]
             (some-> (response/resource-response path options)
                     (response/header "Cache-Control" (or cache-control DEFAULT-CACHE-365D)))))]
    (let [response-fn (if cached?
                        resource-response-cached-fn
                        response/resource-response)]
      (-> response-fn
          (ring/-create-file-or-resource-handler opts)
          (gzip/wrap-gzip)))))

(def ^:private FRONTEND-NOT-BUILT-PAGE
  "<!doctype html>
<html lang=\"en\">
  <head><meta charset=\"UTF-8\"><title>Frontend is not built</title></head>
  <body>
    <h1>Frontend is not built</h1>
    <p>In development, open the Vite dev server at
      <a href=\"http://localhost:5173\">http://localhost:5173</a>.</p>
    <p>To serve the frontend from this server, build it with <code>bb frontend-build</code>.</p>
  </body>
</html>")

(defn- spa-route?
  "Client-side routes are GET requests outside the API and static assets."
  [{:keys [request-method uri]}]
  (and (contains? #{:get :head} request-method)
       (not (or (= uri "/api")
                (str/starts-with? uri "/api/")
                (str/starts-with? uri "/assets/")))))

(defn create-index-handler
  "Serve the SPA's index.html for every client-side route, so the frontend
  router can handle paths like /settings on a full page load.

  Unknown /api and /assets paths fall through to the default handler and get a
  real 404 instead of HTML. index.html is never cached, so a new deployment's
  hashed assets are picked up right away."
  ([]
   (create-index-handler {}))
  ([{:keys [resource]
     :or {resource "public/index.html"}}]
   (fn handler
     ([request]
      (when (spa-route? request)
        (if-let [index (io/resource resource)]
          (-> (response/response (io/input-stream index))
              (response/content-type "text/html; charset=utf-8")
              (response/header "Cache-Control" "no-cache"))
          (-> (response/response FRONTEND-NOT-BUILT-PAGE)
              (response/status 503)
              (response/content-type "text/html; charset=utf-8")))))
     ([request respond _raise]
      (respond (handler request))))))
