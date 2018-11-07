(ns pallet.command-line
  "Process command-line arguments according to a given cmdspec"
  (:refer-clojure :exclude [group-by]))

(defn #^String join
  "Returns a string of all elements in coll, separated by
  separator.  Like Perl's join."
  [#^String separator coll]
  (apply str (interpose separator coll)))

(defn group-by ;; in clojure 1.2 core
  "Returns a sorted map of the elements of coll keyed by the result of
  f on each element. The value at each key will be a vector of the
  corresponding elements, in the order they appeared in coll."
  [f coll]
  (reduce
   (fn [ret x]
     (let [k (f x)]
       (assoc ret k (conj (get ret k []) x))))
   (sorted-map) coll))

(defn make-map [args cmdspec]
  (let [{spec true [rest-sym] false} (group-by vector? cmdspec)
        rest-str (str rest-sym)
        key-data (into {} (for [[syms [_ default]] (map #(split-with symbol? %)
                                                        (conj spec '[help? h?]))
                                sym syms]
                            [(re-find #"^.*[^?]" (str sym))
                             {:sym (str (first syms)) :default default}]))
        defaults (into {} (for [[_ {:keys [default sym]}] key-data
                                :when default]
                            [sym default]))]
    (loop [[argkey & [argval :as r]] args
           cmdmap (assoc defaults :cmdspec cmdspec rest-str [])]
      (if argkey
        (let [[_ & [keybase]] (re-find #"^--?(.*)" argkey)]
          (cond
            (nil? keybase) (recur r (update-in cmdmap [rest-str] conj argkey))
            (= keybase "")  (update-in cmdmap [rest-str] #(apply conj % r))
            :else (if-let [found (key-data keybase)]
                    (if (= \? (last (:sym found)))
                      (recur r (assoc cmdmap (:sym found) true))
                      (recur (next r) (assoc cmdmap (:sym found)
                                             (if (or (nil? r) (= \- (ffirst r)))
                                               (:default found)
                                               (first r)))))
                    (throw (Exception. (str "Unknown option " argkey))))))
        cmdmap))))

(defn- align
   "Align strings given as vectors of columns, with first vector
   specifying right or left alignment (:r or :l) for each column."
   [spec & rows]
   (let [maxes (vec (for [n (range (count (first rows)))]
                      (apply max (map (comp count #(nth % n)) rows))))
         fmt (join " "
                   (for [n (range (count maxes))]
                     (str "%"
                          (when-not (zero? (maxes n))
                            (str (when (= (spec n) :l) "-") (maxes n)))
                          "s")))]
     (join "\n"
           (for [row rows]
             (apply format fmt row)))))

(defn- rmv-q
   "Remove ?"
   [#^String s]
   (if (.endsWith s "?")
      (.substring s 0 (dec (count s)))
      s))

(defn print-help [desc cmdmap]
  (println desc)
  (println "Options")
  (println
     (apply align [:l :l :l]
        (for [spec (:cmdspec cmdmap) :when (vector? spec)]
            (let [[argnames [text default]] (split-with symbol? spec)
                  [_ opt q] (re-find #"^(.*[^?])(\??)$"
                                 (str (first argnames)))
                  argnames  (map (comp rmv-q str) argnames)
                  argnames
                        (join ", "
                          (for [arg argnames]
                            (if (= 1 (count arg))
                              (str "-" arg)
                              (str "--" arg))))]
               [(str "  " argnames (when (= "" q) " <arg>") " ")
                text
                (if-not default
                  ""
                  (str " [default " default "]"))])))))

(defmacro with-command-line
  "Bind locals to command-line args."
  [args desc cmdspec & body]
  (let [locals (vec (for [spec cmdspec]
                      (if (vector? spec)
                        (first spec)
                        spec)))]
    `(let [{:strs ~locals :as cmdmap#} (make-map ~args '~cmdspec)]
       (if (cmdmap# "help?")
         (print-help ~desc cmdmap#)
         (do ~@body)))))

(comment

; example of usage:

(with-command-line *command-line-args*
  "tojs -- Compile ClojureScript to JavaScript"
  [[simple? s? "Runs some simple built-in tests"]
   [serve      "Starts a repl server on the given port" 8081]
   [mkboot?    "Generates a boot.js file"]
   [verbose? v? "Includes extra fn names and comments in js"]
   filenames]
  (binding [*debug-fn-names* verbose? *debug-comments* verbose?]
    (cond
      simple? (simple-tests)
      serve   (start-server (Integer/parseInt serve))
      mkboot? (mkboot)
      :else   (doseq [filename filenames]
                 (filetojs filename)))))

)
