(ns test-server.awl-tour-2013.test-server.test
  (:require [com.ewen.cle-usb-cljs.test-server.handler :as handler-test]
            [com.ewen.cle-usb-cljs.handlers :as handlers]
            [ring.util.serve :refer [serve-headless stop-server]]
            [cljs.repl.browser]))

(serve-headless handlers/app)

(stop-server)

;Starts the browser connected REPL
(cemerick.piggieback/cljs-repl
  :repl-env (doto (cljs.repl.browser/repl-env :port 9000)
              cljs.repl/-setup))

