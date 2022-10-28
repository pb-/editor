(ns editor.command
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [chan put! <!]]
            [cljs-http.client :as http]
            [editor.crypto :refer [encrypt decrypt md5 derive-key]]))

(def endpoint
  (if ^boolean goog.DEBUG "http://localhost:4712" ""))

(defmulti run :type)

(defmethod run :dispatch [cmd]
  (go (:event cmd)))

(defmethod run :timer [cmd]
  (let [c (chan)]
    (.setTimeout js/window #(put! c (:event cmd)) (:delay-ms cmd))
    c))

(defmethod run :push [cmd]
  (go
    (let [encrypted (<! (encrypt (:key cmd) (:text cmd)))
          response (<! (http/put (str endpoint "/api/" (:doc-id cmd))
                                 {:timeout 5000
                                  :query-params {"replaces" (:replaces cmd)}
                                  :body encrypted
                                  :headers {"Content-Type" "application/octet-stream"}}))]
      (merge {:type :pushed
              :status (:status response)}
             (when (#{200} (:status response))
               {:text (:text cmd)
                :sum (md5 encrypted)})))))

(defmethod run :pull [cmd]
  (go
    (let [response (<! (http/get (str endpoint "/api/" (:doc-id cmd))
                                 {:timeout 5000}))
          body (:body response)
          decrypted (<! (decrypt (:key cmd) body))]
      (if (and (#{200} (:status response)) (some? decrypted))
        {:type :pulled
         :status (:status response)
         :text decrypted
         :sum (md5 body)}
        {:type :pulled
         :status 0}))))

(defmethod run :derive-key [cmd]
  (go {:type :key-derived
       :key (<! (derive-key (:passphrase cmd) (:doc-id cmd)))}))
