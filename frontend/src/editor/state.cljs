(ns editor.state
  (:require [clojure.string :as cs]))

(defn initial []
  {:storage {:valid-credentials? false
             :conflict? false
             :dirty? false
             :local-buffer ""
             :remote-buffer ""
             :remote-buffer-sum "d41d8cd98f00b204e9800998ecf8427e"}
   :next-scheduled-push 0
   :generation 1
   :debug? ^boolean goog.DEBUG})

(defmulti evolve (fn [_ event _] (:type event)))

(defn pull [s]
  (let [storage (:storage s)]
    (assoc s
           :pulling? true
           :commands [{:type :pull
                       :key (:key storage)
                       :doc-id (:doc-id storage)}])))

(defn push [s]
  (let [storage (:storage s)]
    (assoc s
           :pushing? true
           :commands [{:type :push
                       :key (:key storage)
                       :doc-id (:doc-id storage)
                       :text (:local-buffer storage)
                       :replaces (:remote-buffer-sum storage)}])))

(defmethod evolve :login-requested [s event ts]
  (let [secret-key (:secret-key s)
        doc-id (:doc-id-value s)
        storage (:storage s)]
    (pull
      (assoc s
             :storage (assoc storage
                             :key secret-key
                             :doc-id doc-id)))))

(defmethod evolve :push-requested [s event ts]
  (let [storage (:storage s)]
    (if (or (:conflict? storage)
            (= (:local-buffer storage) (:remote-buffer storage))
            (and (:scheduled? event) (< ts (:next-scheduled-push s))))
      s
      (push s))))

(defmethod evolve :pushed [s event ts]
  (assoc
    (case (:status event)
      200 (-> s
              (assoc-in [:storage :remote-buffer] (:text event))
              (assoc-in [:storage :remote-buffer-sum] (:sum event))
              (assoc-in [:storage :dirty?] false))
      409 (pull s)
      s)
    :pushing? false))

(defmethod evolve :pull-requested [s event ts]
  (let [storage (:storage s)]
    (if (or (:conflict? storage) (not (:valid-credentials? storage)))
      s
      (pull s))))

(defn ^:private str->lines [s]
  (let [workaround? (cs/ends-with? s "\n")
        input (if workaround? (str s \x) s)
        output (cs/split input #"\n")]
    (if workaround?
      (conj (pop output) "")
      output)))

(defn ^:private merge->str [merge-info]
  (cs/join
    \newline
    (flatten
      (for [part merge-info]
        (or (part "ok")
            ["<<<<<<<"
             (get-in part ["conflict" "a"])
             "======="
             (get-in part ["conflict" "b"])
             ">>>>>>>"])))))

(defn ^:private conflict? [merge-info]
  (boolean (some #(contains? % "conflict") merge-info)))

(defn ^:private diff3 [a orig b]
  (let [merged (js->clj (js/diff3 (clj->js (str->lines a))
                                  (clj->js (str->lines orig))
                                  (clj->js (str->lines b))))]
    {:conflict? (conflict? merged)
     :result (merge->str merged)}))

(defmethod evolve :pulled [s event ts]
  (assoc
    (let [storage (:storage s)]
      (if (and (not (:conflict? storage)) (#{200} (:status event)))
        (let [merged (diff3 (:local-buffer storage) (:remote-buffer storage) (:text event))
              generation ((if (= (:local-buffer storage)
                                 (:result merged))
                            identity inc) (:generation s))]
          (assoc s
                 :generation generation
                 :debounce-cookie 0
                 :storage (assoc storage
                                 :valid-credentials? true
                                 :conflict? (:conflict? merged)
                                 :remote-buffer (:text event)
                                 :remote-buffer-sum (:sum event)
                                 :local-buffer (:result merged))))
        s))
    :pulling? false))

(defmethod evolve :buffer-changed [s event ts]
  (if (= (:cookie event) (:debounce-cookie s))
    (let [push-delay-ms 3000]
      (-> s
          (assoc-in [:storage :local-buffer] (:text event))
          (assoc-in [:storage :dirty?] true)
          (assoc :next-scheduled-push (+ ts push-delay-ms))
          (assoc :commands [{:type :timer
                             :delay-ms push-delay-ms
                             :event {:type :push-requested
                                     :scheduled? true}}])))
    s))

(defmethod evolve :debounce-started [s event ts]
  (assoc s :debounce-cookie (:cookie event)))

(defmethod evolve :resolved [s event ts]
  (push (assoc-in s [:storage :conflict?] false)))

(defmethod evolve :pull-timer-expired [s event ts]
  (assoc s :commands [{:type :timer
                       :delay-ms (* 15 60 1000)
                       :event {:type :pull-timer-expired}}
                      {:type :dispatch
                       :event {:type :pull-requested}}]))

(defmethod evolve :initialized [s event ts]
  (assoc s :commands [{:type :dispatch
                       :event {:type :pull-timer-expired}}]))

(defmethod evolve :doc-id-value-changed [s event ts]
  (-> s
      (assoc :doc-id-value (:value event)
             :commands [{:type :derive-key
                         :doc-id (:value event)
                         :passphrase (:passphrase-value s)}])
      (dissoc :secret-key)))

(defmethod evolve :passphrase-value-changed [s event ts]
  (-> s
      (assoc :passphrase-value (:value event))
      (assoc :commands [{:type :derive-key
                         :doc-id (:doc-id-value s)
                         :passphrase (:value event)}])
      (dissoc :secret-key)))

(defmethod evolve :key-derived [s event ts]
  (assoc s :secret-key (:key event)))
