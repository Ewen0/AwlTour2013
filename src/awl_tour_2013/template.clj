(ns awl-tour-2013.template
  (:require [net.cgrand.enlive-html :as enlive]
            [hiccup.core :refer [html]]
            [hiccup.element :refer [javascript-tag]])
  (:import [java.io.File]))

#_(defn main-tml [in]
  (apply str (enlive/emit*
              (enlive/at (enlive/html-resource in) []))))

(enlive/defsnippet layout "public/main.html" [[:body]] [ff]
                   [:#body] (enlive/content "e"))

(enlive/deftemplate main-tml "public/main.html" []
  #_[[:header (enlive/attr= :role "banner")]] #_(enlive/content ""))


