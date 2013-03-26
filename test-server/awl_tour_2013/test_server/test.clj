(ns test-server.awl-tour-2013.test-server.test
  (:require [awl-tour-2013.handler :as handler]
            [ring.adapter.jetty :refer [run-jetty]]
            [cljs.repl.browser]
            [ojo.watch :refer [defwatch start-watch cease-watch]])
  (:import [java.security KeyStore]))

#_(with-open [ks-f (java.io.FileInputStream. "resources/keystore.jks")
            ts-f (java.io.FileInputStream. "resources/truststore.jks")]
  (def ks (KeyStore/getInstance "JKS"))
  (def ts (KeyStore/getInstance "JKS"))
  (.load ks ks-f (.toCharArray "password"))
  (.load ts ts-f (.toCharArray "password")))

#_(def server (run-jetty handler/app {:port 3000
                                    :join? false}))

#_(def server (run-jetty handler/app {:ssl false
                                      :ssl-port 3443
                                      :keystore ks
                                      :key-password "password"
                                      :truststore ts
                                      :trust-password "password"
                                      :client-auth :need
                                      :port 3000
                                      :join? false}))

#_(.stop server)


;Starts the browser connected REPL
#_(cemerick.piggieback/cljs-repl
  :repl-env (doto (cljs.repl.browser/repl-env :port 9000)
              cljs.repl/-setup))

#_(defwatch w
  ["/home/ewen/clojure/AwlTour2013/resources/public/" 
   [#"/main\.html"]
   [#"/css/common\.css"]] [:modify]
  {}
  (require 'awl-tour-2013.template :reload))

#_(.start (Thread. #(start-watch w)))
#_(cease-watch w)




