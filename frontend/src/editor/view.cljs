(ns editor.view
  (:require [dumdom.core :as dumdom :refer [defcomponent]]))

(defn handle-buffer-changed [dispatch e]
  (let [cookie (.now js/Date)]
    (dispatch {:type :debounce-started
               :cookie cookie})
    (.setTimeout js/window
                 #(dispatch {:type :buffer-changed
                             :cookie cookie
                             :text (. e -target.value)})
                 250)))

(defcomponent credentials [state dispatch]
  [:div.credentials
   [:input {:placeholder "Document id"
            :maxlength 20
            :minlength 20
            :value (:doc-id-value state)
            :oninput #(dispatch {:type :doc-id-value-changed
                                 :value (. % -target.value)})}]
   [:input {:placeholder "Passphrase"
            :type :password
            :minlength 10
            :value (:passphrase-value state)
            :oninput #(dispatch {:type :passphrase-value-changed
                                 :value (. % -target.value)})}]
   [:button (merge
              {:onclick #(dispatch {:type :login-requested})}
              (when-not (and (= (count (:doc-id-value state)) 20)
                             (<= 10 (count (:passphrase-value state)))
                             (:secret-key state))
                {:disabled :disabled})) "Log in"]])

(defn status [state]
  (let [storage (:storage state)]
    (cond
      (:conflict? storage) "There is a conflict"
      (:dirty? storage) "Local changes made"
      :else "All synced to cloud")))

(defcomponent sync-status [state]
  [:div.sync-status
   {:style (cond
             (:pushing? state) {:background-image "url(upload-cloud.svg)"}
             (:pulling? state) {:background-image "url(download-cloud.svg)"}
             :else {:background-image "url(cloud.svg)"
                    :opacity 0.25})}])

(defcomponent main [state dispatch]
  [:div
   (if (:valid-credentials? (:storage state))
     [:div
      [:div.menu
       [sync-status state]
       [:button.changes {:disabled (when (:conflict? (:storage state)) :disabled)
                         :onclick #(dispatch {:type :pull-requested})} "Check for changes"]
       (when (:conflict? (:storage state))
         [:button {:onclick #(dispatch {:type :resolved})} "Mark resolved"])
       [:div (status state)]]
      [:textarea#buffer
       {:rows 20
        :cols 81
        :class (when (:conflict? (:storage state)) "conflict")
        :key (:generation state)
        :oninput (partial handle-buffer-changed dispatch)}
       (:local-buffer (:storage state))]]
     [credentials state dispatch])
   [:p.version "Running " js/appVersion]
   (when (:debug? state)
     [:code (prn-str state)])])
