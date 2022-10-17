(ns editor.crypto
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as cs]
            [goog.crypt.base64 :refer [encodeByteArray decodeStringToUint8Array]])
  (:import goog.crypt.Md5))

(def ^:private text-encoder (js/TextEncoder.))
(def ^:private text-decoder (js/TextDecoder.))

(defn ^:private import-key [hex-string]
  (let [array (js/Uint8Array.
                (clj->js
                  (for [byte-string (partition 2 hex-string)]
                    (js/parseInt (apply str byte-string) 16))))]
    (js/window.crypto.subtle.importKey
      "raw" array "AES-GCM" false #js ["encrypt" "decrypt"])))

(defn ^:private random-iv []
  (js/window.crypto.getRandomValues (js/Uint8Array. 12)))

(defn encrypt [secret-key plaintext]
  (go
    (let [iv (random-iv)
          plaintext (.encode text-encoder plaintext)
          ciphertext (js/window.crypto.subtle.encrypt
                       #js {"name" "AES-GCM", "iv" iv}
                       (<p! (import-key secret-key))
                       plaintext)]
      (str
        (encodeByteArray iv)
        (encodeByteArray (js/Uint8Array. (<p! ciphertext)))))))

(defn decrypt [secret-key ciphertext]
  (go
    (if (empty? ciphertext)
      ""
      (let [iv (decodeStringToUint8Array (subs ciphertext 0 16))
            ciphertext (decodeStringToUint8Array (subs ciphertext 16))
            plaintext (js/window.crypto.subtle.decrypt
                        #js {"name" "AES-GCM", "iv" iv}
                        (<p! (import-key secret-key))
                        ciphertext)]
        (.decode text-decoder (<p! plaintext))))))

(defn md5 [s]
  (let [h (Md5.)]
    (.update h s)
    (cs/join (map #(subs (.toString (+ % 256) 16) 1) (.digest h)))))
