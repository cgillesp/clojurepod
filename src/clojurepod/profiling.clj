(ns clojurepod.profiling
  (:require [clj-async-profiler.core :as prof]
            [clojurepod.feeds :as feeds]))

(defn profetch []
  (do
    (prof/profile (feeds/geteps-or-fetch 617416468))
    (prof/serve-files 8080)
    ))
