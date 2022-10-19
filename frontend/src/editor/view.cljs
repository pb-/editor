(ns editor.view
  (:require [dumdom.core :as dumdom :refer [defcomponent]]))

(defn handle-buffer-changed [dispatch e]
  (dispatch {:type :buffer-changed
             :text (. e -target.value)}))

(defcomponent credentials [state dispatch]
  [:div.credentials
   [:input {:placeholder "Document id"
            :maxlength 20
            :minlength 20
            :oninput #(dispatch {:type :doc-id-value-changed
                                 :value (. % -target.value)})}]
   [:input {:placeholder "Encryption key"
            :maxlength 32
            :minlength 32
            :oninput #(dispatch {:type :secret-key-value-changed
                                 :value (. % -target.value)})}]
   [:button (merge
              {:onclick #(dispatch {:type :login-requested})}
              (when-not (and (= (count (:doc-id-value state)) 20)
                             (= (count (:secret-key-value state)) 32))
                {:disabled :disabled})) "Log in"]])

(defcomponent main [state dispatch]
  (if (:valid-credentials? (:storage state))
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
        :oninput (goog.functions.debounce (partial handle-buffer-changed dispatch) 250)}
       (:local-buffer (:storage state))]]]
    [credentials state dispatch]))
