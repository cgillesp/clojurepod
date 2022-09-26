(ns clojurepod.itunes
  (:require
   [clojure.data.json :as json]
   [ring.util.codec :as rc]))

(def itunes-top-url "https://rss.itunes.apple.com/api/v1/us/podcasts/top-podcasts/all/200/explicit.json")

(defn fetchchart [] (-> itunes-top-url
                        (slurp)
                        (json/read-str)
                        (get "feed")
                        (get "results")))

;; Search page (homepage)
(defn get-results [query]
  (json/read-str (slurp (str "https://itunes.apple.com/search?"
                             (rc/form-encode {:media "podcast" :term query})))))
;; results > artworkUrl60 feedUrl trackName artistName


(defn lookup-ids [in]
  (-> (str "https://itunes.apple.com/lookup?id=" (apply str (interpose "," in)))
      (slurp)
      (json/read-str)
      (get "results")))

(defn ituneslookup [chart] (lookup-ids (map #(get % "id") chart)))

