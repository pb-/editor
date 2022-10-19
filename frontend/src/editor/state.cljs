(ns editor.state
  (:require [clojure.string :as cs]))

(defn initial []
  {:storage {:key "00000000000000000000000000000000"
             :valid-credentials? false
             :conflict? false
             :local-buffer ""
             :remote-buffer ""
             :remote-buffer-sum "d41d8cd98f00b204e9800998ecf8427e"}
   :status :idle
   :next-scheduled-push 0
   :generation 1})

(defmulti evolve (fn [_ event _] (:type event)))

(defmethod evolve :login-requested [s event ts]
  (let [secret-key (:secret-key-value s)
        doc-id (:doc-id-value s)
        storage (:storage s)]
    (assoc s
           :storage (assoc storage
                           :key secret-key
                           :doc-id doc-id)
           :commands [{:type :pull
                       :key secret-key
                       :doc-id doc-id}])))

(defmethod evolve :push-requested [s event ts]
  (let [storage (:storage s)]
    (if (or (:conflict? storage)
            (= (:local-buffer storage) (:remote-buffer storage))
            (and (:scheduled? event) (< ts (:next-scheduled-push s))))
      s
      (assoc s :commands [{:type :push
                           :key (:key storage)
                           :doc-id (:doc-id storage)
                           :text (:local-buffer storage)
                           :replaces (:remote-buffer-sum storage)}]))))

(defmethod evolve :pushed [s event ts]
  (case (:status event)
    200 (-> s
            (assoc-in [:storage :remote-buffer] (:text event))
            (assoc-in [:storage :remote-buffer-sum] (:sum event)))
    409 (assoc s :commands [{:type :pull
                             :doc-id (:doc-id (:storage s))
                             :key (:key (:storage s))}])
    s))

(defmethod evolve :pull-requested [s event ts]
  (let [storage (:storage s)]
    (if (or (:conflict? storage) (not (:valid-credentials? storage)))
      s
      (assoc s :commands [{:type :pull
                           :key (:key storage)
                           :doc-id (:doc-id storage)}]))))

(defn ^:private diff3 [a orig b]
  (let [merged (js->clj (.merge js/Diff3 a orig b (clj->js {:stringSeparator "\n"})))]
    {:conflict? (merged "conflict")
     :result (cs/join \newline (merged "result"))}))

(defmethod evolve :pulled [s event ts]
  (let [storage (:storage s)]
    (if (and (not (:conflict? storage)) (#{200} (:status event)))
      (let [merged (diff3 (:local-buffer storage) (:remote-buffer storage) (:text event))
            generation ((if (= (:local-buffer storage)
                               (:result merged))
                          identity inc) (:generation s))]
        (assoc s
               :generation generation
               :storage (assoc storage
                               :valid-credentials? true
                               :conflict? (:conflict? merged)
                               :remote-buffer (:text event)
                               :remote-buffer-sum (:sum event)
                               :local-buffer (:result merged))))
      s)))

(defmethod evolve :buffer-changed [s event ts]
  ;; TODO fix a bug where we just turned into a conflict, but pending debounced buffer
  ;; overwrites the conflict markup
  (let [push-delay-ms 3000]
    (-> s
        (assoc-in [:storage :local-buffer] (:text event))
        (assoc :next-scheduled-push (+ ts push-delay-ms))
        (assoc :commands [{:type :timer
                           :delay-ms push-delay-ms
                           :event {:type :push-requested
                                   :scheduled? true}}]))))

(defmethod evolve :resolved [s event ts]
  (let [storage (:storage s)]
    (-> s
        (assoc-in [:storage :conflict?] false)
        (assoc :commands [{:type :push
                           :key (:key storage)
                           :doc-id (:doc-id storage)
                           :text (:local-buffer storage)
                           :replaces (:remote-buffer-sum storage)}]))))

(defmethod evolve :pull-timer-expired [s event ts]
  (assoc s :commands [{:type :timer
                       :delay-ms 10000
                       :event {:type :pull-timer-expired}}
                      {:type :dispatch
                       :event {:type :pull-requested}}]))

(defmethod evolve :initialized [s event ts]
  (assoc s :commands [{:type :dispatch
                       :event {:type :pull-timer-expired}}]))

(defmethod evolve :doc-id-value-changed [s event ts]
  (assoc s :doc-id-value (:value event)))

(defmethod evolve :secret-key-value-changed [s event ts]
  (assoc s :secret-key-value (:value event)))
