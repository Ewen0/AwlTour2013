(ns test-server.awl-tour-2013.test-server.test
  (:require [awl-tour-2013.handler :as handler]
            [ring.util.serve :refer [serve-headless stop-server]]
            [cljs.repl.browser]
            [watchtower.core :refer [watcher on-change file-filter rate ignore-dotfiles
                                     extensions]]))

(serve-headless handler/app)

(stop-server)

(watcher ["resources/public/main.html"]
  (rate 50) ;; poll every 50ms
  #_(file-filter ignore-dotfiles) ;; add a filter for the files we care about
  #_(file-filter (extensions :html :css :js)) ;; filter by extensions
  (on-change #(println "er") #_(require :reload ['awl-tour-2013.template])))

;Starts the browser connected REPL
(cemerick.piggieback/cljs-repl
  :repl-env (doto (cljs.repl.browser/repl-env :port 9000)
              cljs.repl/-setup))

