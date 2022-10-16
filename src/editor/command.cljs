(ns editor.command
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [chan put! <!]]
            [cljs-http.client :as http]
            [editor.crypto :refer [encrypt decrypt md5]]))

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
          response (<! (http/put "http://localhost:4712/SI6kmli1Wp/raw"
                                 {:query-params {"replaces" (:replaces cmd)}
                                  :body encrypted
                                  :headers {"Content-Type" "application/octet-stream"}}))]
      (merge {:type :pushed
              :status (:status response)}
             (when (#{200} (:status response))
               {:text (:text cmd)
                :sum (md5 encrypted)})))))

(defmethod run :pull [cmd]
  (go
    (let [response (<! (http/get "http://localhost:4712/SI6kmli1Wp/raw"))
          body (:body response)]
      {:type :pulled
       :status (:status response)
       :text (<! (decrypt (:key cmd) body))
       :sum (md5 body)})))
