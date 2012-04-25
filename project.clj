(defproject spy "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.1"]]
  :repl-init spy
  :jvm-opts ["-Xss5m"
             ;; "-Xdebug"
             ;; "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
             ]
  :java-source-path "test-java"
  :extra-classpath-dirs ["/usr/lib/jvm/default-java/lib/tools.jar"])