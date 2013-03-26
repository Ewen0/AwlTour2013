(defproject awl-tour-2013 "0.1.0-SNAPSHOT"
  :description ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :test-paths ["test" "test-server"]
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [org.clojure/clojurescript "0.0-1586"]
                 [com.cemerick/piggieback "0.0.4" :scope "test"]
                 [compojure "1.1.3"]
                 [ring/ring-jetty-adapter "1.2.0-beta1"]
                 [hiccup "1.0.2"]
                 [enlive "1.0.1"]
                 [enfocus "1.0.1-SNAPSHOT"]
                 [domina "1.0.1"]
                 [com.ewen.flapjax-cljs "1.0.0-RELEASE"]
                 [com.ewen.utils-cljs "1.0.0-RELEASE"]
                 [ojo "1.1.0"]
                 [com.novemberain/monger "1.5.0-rc1"]
                 [shoreleave/shoreleave-remote "0.3.0"]
                 [shoreleave/shoreleave-remote-ring "0.3.0"]]
  :dev-dependencies [[lein-cljsbuild "0.3.0"]
                     [lein-marginalia "0.7.1"]]
  :plugins [[lein-cljsbuild "0.3.0"]
            [lein-marginalia "0.7.1"]
            [lein-deps-tree "0.1.2"]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src-cljs" "browser-repl"]
                        :compiler {:output-to "resources/public/js/cljs.js"
                                   :optimizations :simple
                                   :pretty-print true}}
                       {:id "prod"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/cljs.js"
                                   :optimizations :advanced
                                   :pretty-print false}}]}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})