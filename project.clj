(defproject knight "0.1.0-SNAPSHOT"
  :description "sampersand's Knight, implemented in Clojure."
  :url "https://github.com/knight-lang/knight"
  :license {:name "MIT"
            :url "https://spdx.org/licenses/MIT.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot knight.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.compiler.elide-meta=[:doc :file :line :added]"]}})
