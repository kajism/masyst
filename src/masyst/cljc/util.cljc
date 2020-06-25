(ns masyst.cljc.util
  #?@(:clj
       [(:require
         [clj-time.coerce :as tc]
         [clj-time.core :as t]
         [clj-time.format :as tf]
         [clj-time.predicates :as tp]
         [clojure.edn :as edn]
         [clojure.string :as str]
         [taoensso.timbre :as timbre])
        (:import java.util.Date)]
       :cljs
       [(:require
         [cljs-time.coerce :as tc]
         [cljs-time.core :as t]
         [cljs-time.format :as tf]
         [cljs-time.predicates :as tp]
         [cljs.tools.reader.edn :as edn]
         [clojure.string :as str]
         [taoensso.timbre :as timbre])]))

#?(:clj (def clj-tz (t/time-zone-for-id "Europe/Prague")))

(def formatter-dMyyyy (tf/formatter "d.M.yyyy"))
(def formatter-ddMMyyyy (tf/formatter "dd.MM.yyyy"))
(def formatter-ddMMyyyyHHmm (tf/formatter "dd.MM.yyyy HH:mm" #?(:clj clj-tz)))
(def formatter-ddMMyyyyHHmmss (tf/formatter "dd.MM.yyyy HH:mm:ss" #?(:clj clj-tz)))
(def formatter-ddMMyyyyHHmmssSSS (tf/formatter "dd.MM.yyyy HH:mm:ss.SSS" #?(:clj clj-tz)))
(def formatter-HHmm (tf/formatter "HH:mm" #?(:clj clj-tz)))
(def formatter-yyyyMM (tf/formatter "yyyy/MM"))
(def formatter-MMyyyy (tf/formatter "MM.yyyy"))

(def pasman-cottages {1 {:id 1 :label "Chata 1"}
                      2 {:id 2 :label "Chata 2"}})

(def czech-day-of-week {1 "pondělí"
                        2 "úterý"
                        3 "středa"
                        4 "čtvrtek"
                        5 "pátek"
                        6 "sobota"
                        7 "neděle"})

(defn to-format [date formatter]
  (if (nil? date)
    ""
    (let [out (tc/from-date date)
          out #?(:cljs
                 (cond-> out
                   (not (or (= formatter formatter-dMyyyy) (= formatter formatter-ddMMyyyy)))
                   (t/to-default-time-zone))
                 :clj out)]
      (tf/unparse formatter out))))

(defn from-format [s formatter]
  (when-not (str/blank? s)
    (-> (tf/parse formatter s)
        #?(:cljs
           (cond->
               (not (or (= formatter formatter-dMyyyy) (= formatter formatter-ddMMyyyy)))
             (t/from-default-time-zone)))
        (tc/to-date))))

(defn date-to-str [date]
  (to-format date formatter-ddMMyyyy))

(defn str-to-date [str]
  (from-format str formatter-dMyyyy))

(defn datetime-to-str [date]
  (to-format date formatter-ddMMyyyyHHmmss))

(defn str-to-datetime [str]
  (from-format str formatter-ddMMyyyyHHmmss))

(defn time-to-str [date]
  (to-format date formatter-HHmm))

(defn now []
  #?(:cljs (js/Date.)
     :clj (Date.)))

(defn one-year-from-now []
  (-> (now)
      (tc/from-date)
      (t/plus (t/years 1))
      (tc/to-date)))

(defn- date-plus-hours--dt [date n]
  (t/plus (tc/from-date date) (t/hours n)))

(defn date-plus-hours [date n]
  (tc/to-date (date-plus-hours--dt date n)))

(defn time-plus-hours-to-str [date n]
  (if (nil? date)
    ""
    (tf/unparse formatter-HHmm (date-plus-hours--dt date n))))

(defn validni-jmeno [jmeno] ;http://stackoverflow.com/questions/3617797/regex-to-match-only-letters
  (and (not (str/blank? jmeno))
       (re-matches #"^\p{L}+$" jmeno)))

(defn validni-email [email] ;http://stackoverflow.com/questions/33736473/how-to-validate-email-in-clojure
  (let [pattern #"[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"]
    (and (not (str/blank? email))
         (re-matches pattern email))))

(defn validni-telefon [telefon]
  (and (not (str/blank? telefon))
       (re-matches #"^\+?\d{9,14}$" telefon)))

(defn full-dMyyyy [s]
  (when-not (str/blank? s)
    (let [today (t/today)
          s (str/replace s #"\s" "")
          end-year  (->> (re-find #"\d{1,2}\.\d{1,2}\.(\d{1,4})$" s)
                         second
                         (drop-while #(= % \0))
                         (apply str))
          s (str s (when (and (<= (count (re-seq #"\." s)) 1)
                              (not (str/ends-with? s ".")))
                     "."))
          s (cond
              (= (count (re-seq #"\." s)) 1)
              (str s (t/month today) "." (t/year today))
              (and (= (count (re-seq #"\." s)) 2)
                   (str/ends-with? s "."))
              (str s (t/year today))
              (and (= (count (re-seq #"\." s)) 2)
                   (< (count end-year) 4))
              (str (subs s 0 (- (count s) (count end-year)))
                   (+ 2000 (edn/read-string end-year)))
              :else s)]
      s)))

(defn from-dMyyyy [s]
  (from-format (full-dMyyyy s) formatter-dMyyyy))

(defn full-HHmm [s]
  (when-let [s (not-empty (str/replace (str s) #"\s" ""))]
    (when-let [[_ h m] (re-find #"^(\d{1,2}):?(\d{2,2})?$" s)]
      (str (when (= (count (str h)) 1) "0")
           h
           ":"
           (if (seq m) m "00")))))

(defn from-HHmm [date s]
  (when-let [hhmm (full-HHmm s)]
    (from-format (str (to-format date formatter-ddMMyyyy) " " hhmm) formatter-ddMMyyyyHHmm)))

(defn shorten
  ([s] (shorten s 110))
  ([s n]
   (if (<= (count s) n)
     s
     (str (subs s 0 n) " ..."))))

(defn bin-search
  ([xs x comparator-fn]
   (bin-search xs (partial comparator-fn x)))
  ([xs comparator-fn]
   (let [last-idx (dec (count xs))]
     (loop [lower 0
            upper last-idx]
       (if (> lower upper)
         nil
         (let [mid (quot (+ lower upper) 2)
               midvalue (nth xs mid)]
           (case (comparator-fn midvalue)
             -1 (recur lower (dec mid))
             1 (recur (inc mid) upper)
             mid)))))))

(defn date-time-midnight [dt]
  (t/date-time (t/year dt) (t/month dt) (t/day dt)))

(defn date-midnight [d]
  (-> (tc/from-date d)
      (date-time-midnight)
      (tc/to-date)))

;; Source: https://gist.github.com/werand/2387286
(defn- easter-sunday-for-year [year]
  (let [golden-year (+ 1 (mod year 19))
        div (fn div [& more] (Math/floor (apply / more)))
        century (+ (div year 100) 1)
        skipped-leap-years (- (div (* 3 century) 4) 12)
        correction (- (div (+ (* 8 century) 5) 25) 5)
        d (- (div (* 5 year) 4) skipped-leap-years 10)
        epac (let [h (mod (- (+ (* 11 golden-year) 20 correction)
                             skipped-leap-years) 30)]
               (if (or (and (= h 25) (> golden-year 11)) (= h 24))
                 (inc h) h))
        m (let [t (- 44 epac)]
            (if (< t 21) (+ 30 t) t))
        n (- (+ m 7) (mod (+ d m) 7))
        day (if (> n 31) (- n 31) n)
        month (if (> n 31) 4 3)]
    (t/date-time year (int month) (int day))))

(defn- *easter-monday-for-year [year]
  (t/plus (easter-sunday-for-year year) (t/days 1)))

(def easter-monday-for-year (memoize *easter-monday-for-year))

(defn- *bank-holiday? [{:keys [:bank-holiday/day :bank-holiday/month :bank-holiday/easter-delta]} dt]
  (or (and (= (t/day dt) day)
           (= (t/month dt) month))
      (and (some? easter-delta)
           (t/equal? (date-time-midnight dt)
                     (t/plus (easter-monday-for-year (t/year dt))
                             (t/days easter-delta))))))

(defn bank-holiday-for-date-time [bank-holidays dt]
  (some #(when (*bank-holiday? % dt)
           %)
        bank-holidays))

(defn bank-holiday? [bank-holidays dt]
  (boolean (bank-holiday-for-date-time bank-holidays dt)))

(defn period-working-date-times
  "Returns all date-times except weekends and holidays from - to (inclusive)."
  [holiday?-fn from-dt to-dt]
  (let [from-dt (date-time-midnight from-dt)
        to-dt (date-time-midnight to-dt)]
    (->> from-dt
         (iterate #(t/plus % (t/days 1)))
         (take-while #(not (t/after? % to-dt)))
         (remove tp/weekend?)
         (remove holiday?-fn))))

(defn period-working-dates
  "Returns all dates except weekends and holidays from - to (inclusive)."
  [holiday?-fn from to]
  (->> (period-working-date-times holiday?-fn (tc/from-date from) (tc/from-date to))
       (map #(tc/to-date %))))

(defn find-by-ident [ident xs]
  (some (fn [x]
          (when (= ident (:db/ident x))
            (select-keys x [:db/id])))
        xs))

(defn remove-spaces [s]
  (str/replace (str s) #"\s+" ""))

(defn remove-leading-zeros [s]
  (-> s
      str
      (str/replace #"^0+(\d)" "$1")))

(defn parse-int [s]
  (when-let [s (not-empty (remove-leading-zeros (remove-spaces s)))]
    #?(:cljs
       (let [n (js/parseInt s)]
         (if (js/isNaN n)
           nil
           n))
       :clj
       (let [n (edn/read-string s)]
         (when (number? n)
           (long n))))))

(defn parse-float [s]
  (when-let [s (not-empty (str/replace (remove-spaces s) #"," "."))]
    #?(:cljs
       (let [n (js/parseFloat s)]
         (if (js/isNaN n)
           nil
           n))
       :clj
       (let [n (edn/read-string s)]
         (when (number? n)
           (float n))))))

(defn calendar-days [{:keys [:ent/from :ent/to :vacation/half-day?]}]
  (if (and from to)
    (try
      (cond-> (inc (t/in-days (t/interval (tc/from-date from)
                                          (tc/from-date to))))
        half-day?
        (- 0.5))
      (catch #?(:cljs js/Error
                :clj Exception) e
        (timbre/error e)
        [:span {:style {:color "red"}} "Datum Do nesmí být před datumem Od!"]))
    0))

(defn calendar-nights [item]
  (if (zero? (calendar-days item))
    0
    (dec (calendar-days item))))

(defn dates-of-the-month [d]
  (let [dt (-> d (tc/from-date) (t/first-day-of-the-month) (date-time-midnight))]
    (->> dt
         (iterate #(t/plus % (t/days 1)))
         (take-while (let [last-day (t/last-day-of-the-month dt)]
                       #(not (t/after? % last-day))))
         (map tc/to-date))))

(defn deref-or-value
  "Takes a value or an atom
  If it's a value, returns it
  If it's a Reagent object that supports IDeref, returns the value inside it by derefing
  Stolen from re-com.util."
  [val-or-atom]
  (if (satisfies? #?(:clj clojure.lang.IDeref :cljs IDeref) val-or-atom)
    @val-or-atom
    val-or-atom))

(defn first-by-ident [ident xs]
  (let [xs (deref-or-value xs)
        xs (if (map? xs) (vals xs) xs)]
    (some (fn [x]
            (when (= ident (:db/ident x))
              x))
          xs)))

(defn duration-min [from to]
  (t/in-minutes (t/interval (tc/from-date from) (tc/from-date to))))

(defn mins--hm [mins]
  (cond
    (nil? mins)
    ""
    (zero? mins)
    "0"
    :else
    (str (quot mins 60) "h " (rem mins 60) "m")))

(defn date-with-current-time [d]
  (let [now (t/now)
        dt (tc/from-date d)]
    (tc/to-date
     (t/date-time (t/year dt) (t/month dt) (t/day dt) (t/hour now) (t/minute now) (t/second now)))))

(defn today? [d]
  (= (date-midnight d) (date-midnight (now))))

(defn today []
  (tc/to-date (date-time-midnight (t/now))))

(defn with-date [d time-fn]
  (-> (tc/from-date d)
      (time-fn)
      (tc/to-date)))
