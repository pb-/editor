(ns backend.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.resource :refer [wrap-resource]])
  (:import [java.security MessageDigest])
  (:gen-class))

(def storage-path (System/getenv "STORAGE_PATH"))

(defn document-path [document-id]
  (str storage-path \/ document-id))

(defn valid-document-id? [id]
  (and (boolean (re-matches #"[0-9a-zA-Z]{20}" id))
       (.exists (io/file (document-path id)))))

(defn bytes->hex [bs]
  (apply str (map #(format "%02x" %) bs)))

(defn md5 [data]
  (bytes->hex (.digest (MessageDigest/getInstance "MD5") data)))

(defn load-document [document-id]
  (locking storage-path
    (with-open [stream (io/input-stream (document-path document-id))]
      (.readAllBytes stream))))

(defn store-document! [document-id data replaces]
  (locking storage-path
    (let [old-sum (md5 (load-document document-id))]
      (if (= replaces old-sum)
        (with-open [stream (io/output-stream (document-path document-id))]
          (.write stream data)
          true)
        false))))

(defn not-found [& request]
  {:status 404
   :body "Not here"
   :headers {"Content-type" "text/plain"}})

(defn get-document [document-id]
  {:body (load-document document-id) 
   :headers {"Content-type" "application/octet-stream"}})

(defn put-document! [document-id data replaces]
  (if-let [result (store-document! document-id data replaces)]
    {:status 200}
    {:status 409}))

(defn strip-prefix [s prefix]
  (if (string/starts-with? s prefix)
    (subs s (count prefix))
    s))

(defn api [request]
  (let [document-id (subs (:uri request) 1)]
    (if (valid-document-id? document-id)
      (case (:request-method request)
        :get (get-document document-id)
        :put (put-document!
               document-id
               (.readAllBytes (:body request))
               (strip-prefix (or (:query-string request) "") "replaces="))
        (not-found))
      (not-found))))

(defn router [request]
  (if (string/starts-with? (:uri request) "/api/")
    (api (update request :uri subs 4))
    (not-found)))

(defn wrap-index [h]
  (fn [request]
    (if (= "/" (:uri request))
      (h (assoc request :uri "/index.html"))
      (h request))))

(defn wrap-nocache [h]
  (fn [request]
    (assoc-in (h request) [:headers "cache-control"] "no-store")))

(def app (wrap-nocache (wrap-index (wrap-content-type (wrap-resource router "public")))))

(defn -main []
  (run-jetty app {:port 8080 :send-server-version? false}))

(comment
  ;; evaluate this to start the development server
  (do
    (require '[ring.middleware.reload :refer [wrap-reload]])

    (defn wrap-dev-cors [h]
      (wrap-cors
        h
        :access-control-allow-origin [#"http://localhost:9500"]
        :access-control-allow-methods [:get :put :options]
        :access-control-allow-credentials "true"))

    (run-jetty (wrap-dev-cors (wrap-reload #'app)) {:port 4712 :join? false})))
