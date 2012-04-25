(defn build-exclude-list [excludes]
  (set (reduce concat
               (map (fn [[p cs]]
                      (map (fn [c] (str p "." c))
                           cs))
                    excludes))))

(def exclude-classes
  [["com.bm.xchange.util" ["*"]]
   ["com.bm.xchange.db" ["DBObject" "Crit"]]])

(defn spy []
  (spy-on :host "localhost"
          :port 5002
          :classes "com.bm.xchange.services.*"
          :exclude (build-exclude-list exclude-classes))
  (spy/start))
