(ns clojurepod.itunes
  (:require
   [clojure.data.json :as json]
   [clojurepod.db :as db]
   [next.jdbc :as jdbc]
   [clojure.core.async :as a]
   [ring.util.codec :as rc]
   [java-time :as jt]
   )
   )

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

(defn m-genre [genres s] (reduce #(or %1 (= s (get %2 "name"))) false genres))

(defn lookup-ids [in]
  (-> (str "https://itunes.apple.com/lookup?id=" (apply str (interpose "," in)))
      (slurp)
      (json/read-str)
      (get "results")))

(defn ituneslookup [chart] (lookup-ids (map #(get % "id") chart)))

(defn geturls [chart] (filter boolean (map #(get % "feedUrl") (get chart "results"))))

