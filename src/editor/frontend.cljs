(ns ^:figwheel-hooks editor.frontend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! chan pipe put!]]
            [cljs.reader]
            [dumdom.core :as dumdom]
            [editor.state :refer [evolve initial]]
            [editor.command :refer [run]]
            [editor.view :refer [main]]))

(defn app []
  (let [loaded-state (cljs.reader/read-string
                       (.getItem js/window.localStorage "state"))
        app-state (atom (update (initial) :storage #(or loaded-state %)))
        event-queue (chan)
        dispatch #(put! event-queue %)]
    (dispatch {:type :initialized})
    (go
      (while true
        (let [event (<! event-queue)
              _ (println "handling" event)
              state-and-commands (evolve @app-state event (.now js/Date))
              commands (:commands state-and-commands)
              state (dissoc state-and-commands :commands)]
          (assert (not (nil? state)))
          (reset! app-state state)
          (.setItem js/window.localStorage "state" (pr-str (:storage state)))
          (dumdom/render [main state dispatch] (js/document.getElementById "app"))
          (doseq [command commands]
            (pipe (run command) event-queue false)))))))

(app)
