(ns masyst.cljs.util
  (:require [ajax.core :refer [GET POST]]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [clojure.string :as str]
            [cognitect.transit :as tran]
            [goog.string :as gstring]
            [masyst.cljc.schema :as schema]
            [masyst.cljc.util :as cljc.util]
            [re-frame.core :as re-frame]
            [schema.core :as s]
            [taoensso.timbre :as timbre]))

(defonce id-counter (atom 0))
(defn new-id []
  (swap! id-counter dec))

(def cid-counter (atom 0))
(defn new-cid []
  (swap! cid-counter inc))

(defn abs [n] (max n (- n)))

(defn valid-schema?
  "validate the given db, writing any problems to console.error"
  [db]
  (let [res (s/check schema/AppDb db)]
    (if (some? res)
      (.error js/console (str "schema problem: " res)))))

(def debug-mw [(when ^boolean goog.DEBUG re-frame/debug)
               #_(when ^boolean goog.DEBUG (re-frame/after valid-schema?))])

(timbre/set-level! (if ^boolean goog.DEBUG :debug :info))

(defn dissoc-temp-keys [m]
  (into {} (remove (fn [[k v]]
                     (or (str/starts-with? (name k) "-")
                         (and (str/starts-with? (name k) "_")
                              (sequential? v))))
                   m)))

(defn sort-by-locale
  "Tridi spravne cestinu (pouziva funkci js/String.localeCompare). keyfn musi vracet string!"
  [keyfn coll]
  (sort-by (comp str/capitalize str keyfn) #(.localeCompare %1 %2) coll))

(defn float--text [x]
  (if x
    (str/replace (gstring/format "%.2f" x) #"\." ",")
    ""))

(defn parse-bigdec [s]
  (when-let [n (cljc.util/parse-float s)]
    (tran/bigdec (str n))))

(defn boolean->text [b]
  (if b "Ano" "Ne"))

(defn money->text [n]
  (let [n (or n 0)
        [i d] (-> n
                  str
                  (str/replace "," ".")
                  (str/split #"\."))]
    (str (->> i
              reverse
              (partition-all 3)
              (map #(apply str %))
              (str/join " ")
              str/reverse)
         (when d
           (str "," d)))))

(defn bigdec->float [n]
  (when n
    (cljc.util/parse-float (.-rep n))))

(defn file-size->str [n]
  (cond
    (nil? n) ""
    (neg? n) "-"
    (zero? n) "0"
    :else
    (reduce (fn [div label]
              (let [q (quot n div)]
                (if (pos? q)
                  (reduced (str (.toFixed (/ n div) 1) " " label))
                  (/ div 1000))))
            1000000000000
            ["TB" "GB" "MB" "kB" "B"])))

(defn hiccup->string [h]
  (cond
    (string? h)
    h
    (vector? h)
    (apply str (map hiccup->string h))
    :else
    ""))

(defn dangerousHTML [text]
  [:div {:dangerouslySetInnerHTML {:__html text}}])
