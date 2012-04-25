(ns print
  (:use [clojure.string :as s :only ()]))

(defn class-name [cls]
  (if (not cls)
    cls
    (.substring cls (+ (.lastIndexOf cls ".") 1))))

(defn method-name [m]
  (.name m))

(defn formata [{:keys [name value type]}]
  (format "%s:%s (%s)" name value (class-name type)))

(defn formatm [{:keys [type method args class return return-type]}]
  (let [arg-str (if (empty? args)
                  ""
                  (s/join "," (doall (map formata args))))]
    (str (format "%s/%s(%s)"
                 (class-name class)
                 (method-name method)
                 arg-str)
         (if (= return "void")
           ""
           (format " => %s (%s)" return (class-name return-type))))))

(defn prefix-chars []
  (cycle (vec "|+!*")))

(defn prefix-string
  ([]
     (prefix-string "" (prefix-chars)))
  ([prefix [char & pcs]]
     (let [cur (str prefix char " ")]
       (lazy-cat [cur] (prefix-string cur pcs)))))

(defn print-method-tree [method prefixes]
  (when (:method method)
    (print (first prefixes))
    (println (formatm method)))
  (doseq [c (:children method)]
    (print-method-tree c (next prefixes))))

(defn print-methods [methods]
  (print-method-tree methods
                     (concat "" (prefix-string))))

