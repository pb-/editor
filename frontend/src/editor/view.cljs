(ns editor.view
  (:require [dumdom.core :as dumdom :refer [defcomponent]]))

(defn handle-changed [dispatch e]
  (dispatch {:type :buffer-changed
             :text (. e -target.value)}))

(defcomponent main [state dispatch]
  [:div
   [:button {:onclick #(dispatch {:type :push-requested})} "push"]
   [:button {:onclick #(dispatch {:type :pull-requested})} "pull"]
   [:div
    [:div (name (:status state))]
    (when (:conflict? (:storage state))
      [:div
       [:div "There is a conflict"]
       [:button {:onclick #(dispatch {:type :resolved})} "Mark resolved"]])
    [:textarea#buffer
     {:rows 20
      :cols 81
      :class (when (:conflict? (:storage state)) "conflict")
      :key (:generation state)
      :oninput (goog.functions.debounce (partial handle-changed dispatch) 250)}
     (:local-buffer (:storage state))]]
   [:pre (str state)]])
