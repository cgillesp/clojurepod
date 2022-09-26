(ns clojurepod.profiling
  (:require [clj-async-profiler.core :as prof]
            [clojurepod.feeds :as feeds]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn profetch []
  (prof/profile (feeds/geteps-or-fetch 617416468))
  (prof/serve-files 8080))
