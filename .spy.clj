(def common-excludes ["java.*" "com.sun.*" "sun.*"
                      "net.*" ;; "org.*"
                      "swank.*"
                      "clojure.*"])

(defn build-exclude-list [excludes]
  (set (reduce concat
               (map (fn [[p cs]]
                      (map (fn [c] (str p "." c))
                           cs))
                    excludes))))

(def exclude-classes
  [["org.apache" ["*"]]
   ["org.hibernate" ["*"]]
   ;; ["com.bm.xchange.db" ["DBObject" "Crit"]]
   ])

(defn spy []
  (spy-on :host "localhost"
          :port 8002
          :classes "com.zoomsystems.*"
          :exclude (concat common-excludes
                           (build-exclude-list exclude-classes)))
  (spy/start))
