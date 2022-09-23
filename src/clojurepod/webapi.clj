(ns clojurepod.webapi
  (:require
   [ring.util.codec :as rc]
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojurepod.itunes :as itunes]
   [ring.util.codec :as rc]
   [clojurepod.db :as db]

   [clojurepod.feeds :as feeds]
   [lambdaisland.uri :as uri]
   [clojure.string :as strn]
   )

  )

;; Podcast Info
;; - itunesID
;; - Title (\> itunesTitle)
;; - Description (\> Summary)
;; - Subtitle
;; - smallImage
;; - Image (itunesImage)
;; - Author (itunesAuthor)
;; - Link
;; - Copyright
;;
;; Episode Info
;; - Channel
;; - guid
;; - Title (\> itunesTitle)
;; - PubDate
;; - Description
;; - iTunesSummary
;; - Subtitle (itunesSubtitle)
;; - Duration
;; - Link
;; - Image (itunesImage)
;;
;; - fileUrl
;; - fileSize
;; - fileType

(defn test-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "hello!"})

(defn getq [request] (if (:query-string request)
                       (get (rc/form-decode
                             (:query-string request)) "q")))

;; Returns podcast channels matching a search term
(declare search-result)
(defn search-handler [request]
  (let [query (getq request)]
    (if query
      (try {:status 200
            :headers {"Content-Type" "text/json"
                      "Cache-Control" "max-age=1300"}
            :body (search-result query)}
           (catch Exception e {:status 500
                               :headers {"Content-Type" "text/json"}
                               :body ""
                               })
           )
      {:status 400
       :headers {"Content-Type" "text/json"}
       :body "Needs query"}
      )))

(defn search-result [query]
  (as->
      (itunes/get-results query) x
    (get x "results")
    (map #(identity {:author (get % "artistName")
                     :itunesID (get % "collectionId")
                     :title (get % "trackName")
                     :image (get % "artworkUrl100")
                     :itunesUrl (get % "collectionViewUrl")}) x)
    (json/write-str x)
    )
  )

;; Returns info about podcast channels by ID

(defn podinfo-result [ids]
  (->>
   (feeds/getchans-or-fetch ids)
   (map db/chanrow->map)
   (json/write-str))
  )

(defn q->longs
  [query] (->> [query]
               (flatten)
               (map #(strn/split % #","))
               (flatten)
               (map #(Long/parseLong %))
               ))

(defn podinfo-handler [request]
  (let [query (getq request)]
    (if query
      ;; (try
        {:status 200
            :headers {"Content-Type" "text/json"
                      "Cache-Control" "max-age=43200"}
            :body (podinfo-result (q->longs query))}
           ;; (catch Exception e {:status 500
           ;;                     :headers {"Content-Type" "text/json"}
           ;;                     :body ""
           ;;                     }))
      {:status 400
       :headers {"Content-Type" "text/json"}
       :body "Needs query"}
      )))

;; Returns the feed of a given podcast channel by ID
(defn podfeed-result [id]
  (let [eps (feeds/geteps-or-fetch id)
        chaninfo
        (first (feeds/getchans-or-fetch [id]))]
    (json/write-str
     {:channel (db/chanrow->map chaninfo)
      :episodes (map db/eprow->map eps)}))
  )

(defn podfeed-handler [request]
  (let [query (getq request)]
    (if query
      (try {:status 200
            :headers {"Content-Type" "text/json"
                      "Cache-Control" "max-age=1300"}
            :body (podfeed-result (Long/parseLong query))}
           (catch Exception e (do (println e)
                                  {:status 500
                                   :headers {"Content-Type" "text/json"}
                                   :body ""
                                   })))
      {:status 400
       :headers {"Content-Type" "text/json"}
       :body "Needs query"}
      )))
