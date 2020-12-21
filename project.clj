(defproject stockmon3 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.postgresql/postgresql "42.1.1"]
                 [ragtime "0.8.0"]
                 [seancorfield/next.jdbc "1.1.613"]
                 [environ "1.2.0"]]
  :plugins [[lein-environ "1.2.0"]]
  :main ^:skip-aot stockmon3.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
