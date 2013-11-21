(defproject spy "1.0.0-SNAPSHOT"
  :description "FIXME: write description"

  :resource-paths ["lib/tools.jar"]
  
  :dependencies [[org.clojure/clojure "1.4.0"]]
  
  :repl-options {:init-ns spy}
  :jvm-opts ["-Xss5m"
             "-Xmx512m"
             ;; "-Xdebug"
             ;; "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
             ]
  :java-source-paths ["test-java"])