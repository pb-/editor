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

(defn ^:private ->hex [array]
  (cs/join (map #(subs (.toString (+ % 256) 16) 1) array)))

(defn derive-key [passphrase salt]
  (go
    (let [encoded (.encode text-encoder passphrase)
          kdf-key (<p! (js/window.crypto.subtle.importKey
                         "raw" encoded "PBKDF2" false
                         #js ["deriveBits" "deriveKey"]))
          aes-key (<p! (js/window.crypto.subtle.deriveKey
                         #js {:name "PBKDF2"
                              :salt (.encode text-encoder salt)
                              :iterations 100000
                              :hash "SHA-256"}
                         kdf-key
                         #js {:name "AES-GCM"
                              :length 256}
                         true
                         #js ["encrypt" "decrypt"]))]
      (->hex
        (js/Uint8Array.
          (<p! (js/window.crypto.subtle.exportKey "raw" aes-key)))))))

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
      (try
        (let [iv (decodeStringToUint8Array (subs ciphertext 0 16))
              ciphertext (decodeStringToUint8Array (subs ciphertext 16))
              plaintext (js/window.crypto.subtle.decrypt
                          #js {"name" "AES-GCM", "iv" iv}
                          (<p! (import-key secret-key))
                          ciphertext)]
          (.decode text-decoder (<p! plaintext)))
        (catch js/Error _ nil)))))

(defn md5 [s]
  (let [h (Md5.)]
    (.update h s)
    (->hex (.digest h))))
