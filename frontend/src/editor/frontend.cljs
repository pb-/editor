(ns ^:figwheel-hooks editor.frontend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! chan pipe put!]]
            [cljs.reader]
            [dumdom.core :as dumdom]
            [editor.state :refer [evolve initial]]
            [editor.command :refer [run]]
            [editor.view :refer [main]]))

;; for development hot reloading, avoid running multiple main loops
(def run-id (.now js/Date))
(defn keep-running? [id]
  (= id run-id))

(def loaded-state  (cljs.reader/read-string
                       (.getItem js/window.localStorage "state")))
(defonce app-state (atom (update (initial) :storage #(or loaded-state %))))

(when (:debug? @app-state)
  (set! dumdom.component/*render-eagerly?* true))

(defn app [id]
  (let [event-queue (chan)
        dispatch #(put! event-queue %)]
    (dispatch {:type :initialized})
    (go
      (while (keep-running? id)
        (let [event (<! event-queue)]
          (when (keep-running? id)
            (let [state-and-commands (evolve @app-state event (.now js/Date))
                  commands (:commands state-and-commands)
                  state (dissoc state-and-commands :commands)]
              (when (:debug? state)
                (println "event" event))
              (reset! app-state state)
              (.setItem js/window.localStorage "state" (pr-str (:storage state)))
              (dumdom/render [main state dispatch] (js/document.getElementById "app"))
              (doseq [command commands]
                (when (:debug? state)
                  (println "command" command))
                (pipe (run command) event-queue false)))))))))

(app run-id)
