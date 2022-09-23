(ns clojurepod.standalone-server
  (:require [ring.adapter.jetty :as jetty]
            [clojurepod.core :as core])
  (:gen-class))
(defn -main [& args]
  (jetty/run-jetty core/app {:port 9599}))
