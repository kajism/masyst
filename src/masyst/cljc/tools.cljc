(ns masyst.cljc.tools
  (:require [clojure.string :as str]))

(def transliteration-table {
                            "á" "a"
                            "č" "c"
                            "ď" "d"
                            "d" "d"
                            "é" "e"
                            "ě" "e"
                            "í" "i"
                            "ň" "n"
                            "ó" "o"
                            "ř" "r"
                            "š" "s"
                            "ť" "t"
                            "ú" "u"
                            "ů" "u"
                            "ý" "y"
                            "ž" "z"})

(defn transliteration-text
  "Funkce odstrani z ceskych znaku diakritiku"
  [text]
  (when (some? text)
    (->> text
         (map (fn [letter]
                (get transliteration-table (str letter) letter)))
         (str/join ""))))

(defn replace-illegal-char
  "Funkce nahradi nepovolene znaky za podtrzitka"
  [filename]
  (when (some? filename)
    (str/replace filename #"[^a-z0-9-\.]" "_")))

(defn replace-multiple-underscores
  "Funkce nahradi vice podtrzitek podtrzitkem jednim"
  [filename]
  (when (some? filename)
    (str/replace filename #"[_]+" "_")))

(defn sanitize-filename
  "Funkce prevede nazev souboru do tvaru bezpecneho k ulozeni"
  [filename]
  (when-not (str/blank? filename)
    (-> filename
        (str/lower-case)
        (transliteration-text)
        (replace-illegal-char)
        (replace-multiple-underscores))))

(defn find-by-db-id [coll id]
  (reduce (fn [out e] (when (= id (:db/id e)) (reduced e))) nil coll))

(defn min->sec [min]
  (* min 60))

(defn hour->sec [hour]
  (* hour (min->sec 60)))

(defn hour->millis [hour]
  (* (hour->sec hour) 1000))
