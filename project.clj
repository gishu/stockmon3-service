(defproject stockmon3 "1.0.0"
  :description "Gishu's stock portfolio monitor"
  :url "github.com/gishu/stockmon3-service"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.postgresql/postgresql "42.1.1"]
                 [ragtime "0.8.0"]
                 [seancorfield/next.jdbc "1.1.613"]
                 [clojurewerkz/money "1.10.0"]
                 [environ "1.2.0"]]
  :plugins [[lein-environ "1.2.0"]]
  :main ^:skip-aot stockmon3.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :now :now})
