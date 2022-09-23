(ns clojurepod.feeds
  (:require [ajax.core :as ajx]
            [clojure.data.json :as json]
            [ring.util.codec :as rc]
            [clojure.data.xml :as xml]
            [clj-http.client :as http]
            [clj-http.conn-mgr :as connm]
            [clojurepod.db :as db]
            [next.jdbc :as jdbc]

            [clojure.core.async :as a]
            [clojure.set :as sets]
            [java-time :as jt]

            [clojurepod.itunes :as itunes])
  (:import (java.time.format DateTimeFormatter)
           (java.time.temporal TemporalQuery))
  )

;; Feed display
(defn get-feed [feed-url] (-> feed-url (slurp)
                              (xml/parse-str :namespace-aware false)
                              (get :content)
                              (first)
                              (get :content)))
(declare ep->map)

(defn only-eps [feed] (->> feed
                           (filter (fn [x] (= :item (get x :tag))))
                           (map :content)
                           (map (fn [x] (ep->map x)))))

(defn ep->map [in] (loop [tagmap {} nodes in]
                     (cond
                       ;; Return self if it's not a collection
                       (not (coll? nodes))
                       nodes

                       ;; Return nil if it's an empty collection
                       ;; (nothing ahead and nothing read yet)
                       (and (not (seq nodes)) (not (seq tagmap))) nil

                       ;; If you've finished, return tagmap
                       (not (seq nodes)) tagmap

                       ;; Return (first) of single item collections
                       ;; that don't contain maps
                       (and (not (second nodes)) (not (seq tagmap))
                            (not (map? (first nodes))))
                       (first nodes)

                       ;; Only include the first item (episode) in the map
                       ;; to save some time on long feeds
                       (and (= (:tag (first nodes) :item)) (:item tagmap))
                       (recur tagmap (rest nodes))

                       ;; Return map with attrs if it has any, recursing
                       ;; on content in case there's anything there
                       (-> (first nodes) (:attrs) (empty?) (not))
                       (let [fn (first nodes) rn (rest nodes)]
                         (recur (assoc tagmap (:tag fn) (-> fn
                                                            (dissoc :tag)
                                                            (assoc :content (ep->map (:content fn)))))
                                (rest nodes)))

                       ;; Otherwise return the contents, recursing
                       ;; in case there's any map content there
                       :else
                       (recur (assoc tagmap (:tag (first nodes))
                                     (ep->map (:content (first nodes)))) (rest nodes))
                       ))
  )
;; :itunes:image :title :description :subtitle
;; :title :audio-url :itunes:subtitle :description :itunes:description


(defn yn->bool [in] (if (string? in) (= "yes" (.toLowerCase ^String in)) nil))

(defn ingest-channel
  [itunesID feed feedUrl con]
  (db/upsert-channel con {
                          :itunesID (long itunesID)
                          :feedUrl feedUrl
                          :title (:title feed)
                          :itunesTitle (:itunes:title feed)
                          :description (:description feed)
                          :itunesSummary (:itunes:summary feed)
                          :itunesSubtitle (:itunes:subtitle feed)
                          :itunesImage (-> feed (:itunes:image) (:attrs) (:href))
                          :language (:language feed)
                          :itunesExplicit (yn->bool (:itunes:explicit feed))
                          :itunesAuthor (:itunes:author feed)
                          :link (:link feed)
                          :itunesType (:itunes:type feed)
                          :copyright (:copyright feed)
                          :itunesBlock (:itunes:block feed)
                          :itunesComplete (:itunes:complete feed)
                          :lastFetched (java.time.Instant/now)
                          }))

;; rfc 2822 formatter
(def dtp (DateTimeFormatter/ofPattern  "EEE, d MMM yyyy HH:mm:ss [Z][z]"))
(def dtf (.withZone (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z") (java.time.ZoneId/of "UTC")))
(defn parserfc [in] (try
                      (.atOffset (java.time.Instant/from (.parse ^DateTimeFormatter dtp in))
                                       java.time.ZoneOffset/UTC)
                      (catch Exception e nil)))

(defn parseduration [in] (let [s (re-find #"^\d*$" in)
                               ms (re-find #"(\d+):(\d+)" in)
                               hms (re-find #"(\d+):(\d+):(\d+)" in)]
                           (cond
                               s (Long/parseLong s)
                               hms (let [h (Long/parseLong (nth hms 1))
                                         m (Long/parseLong (nth hms 2))
                                         s (Long/parseLong (nth hms 3))]
                                     (-> h (* 60) (+ m) (* 60) (+ s)))
                               ms (let [m (Long/parseLong (nth ms 1))
                                        s (Long/parseLong (nth ms 2))]
                                    (if (> 60 s) (+ s (* 60 m)))
                                    )
                               :else "hello"
                               )))

(defn ingest-episode
  [itunesID ep con]
  (db/upsert-episode con {
                          :channel (long itunesID)
                          :title (:title ep)
                          :itunesTitle (:itunes:title ep)
                          :fileUrl (-> ep (:enclosure) (:attrs) (:url))
                          :fileSize (try (-> ep (:enclosure) (:attrs) (:length) (Long/parseLong)) (catch Exception e nil))
                          :fileType (-> ep (:enclosure) (:attrs) (:type))
                          :guid (or (-> ep (:guid) (:content)) (-> ep (:guid)))
                          :pubDate (parserfc (:pubDate ep))
                          :description (:description ep)
                          :itunesSummary (:itunes:summary ep)
                          :itunesSubtitle (:itunes:subtitle ep)
                          :itunesDuration (if (:itunes:duration ep)
                                            (parseduration (:itunes:duration ep)))
                          :link (:link ep)
                          :itunesImage (-> ep (:itunes:image) (:attrs) (:href))
                          :itunesExplicit (yn->bool (:itunes:explicit ep))
                          :itunesEpisode (:itunes:episode ep)
                          :itunesSeason (:itunes:season ep)
                          :itunesEpisodeType (:itunes:episodeType ep)
                          :itunesBlock (yn->bool (:itunes:block ep))
                          :lastFetched (java.time.Instant/now)
                          }))

(defn add-eps [itunesID eps tx] (doall (map #(ingest-episode itunesID % tx) eps)))

(defn add-channel
  ([itunesID feedUrl] (add-channel itunesID feedUrl nil nil))
  ([itunesID feedUrl rank fetchedAt]
   (try
     (let [feed (get-feed feedUrl)]
       (jdbc/with-transaction [tx db/db]
         [(ingest-channel itunesID (ep->map feed) feedUrl tx)
          (add-eps itunesID (only-eps feed) tx)
          (if (and rank fetchedAt)
            (db/add-to-topchart tx
                                {:channel (long itunesID)
                                 :rank rank
                                 :fetchedAt fetchedAt
                                 }))
          ])) (catch Exception e (do (println itunesID feedUrl (:cause e)) e)))
   ))

(def sqlchan (a/chan))

(defn fliplist [rows id] (loop [ret {} left rows]
                           (if (seq left)
                             (recur
                              (assoc ret (get (first left) id)
                                     (first left)) (rest left))
                             ret
                             )))

(defn chan-fetchorreadcache [row validdate]
  (if (jt/before? (jt/instant (:lastfetched row)) validdate)
    (do (add-channel (:itunesid row) (:feedurl row))
        (first (db/channels-by-ids db/db {:ids [(:itunesid row)]}))
        )
    row)
                       )

(defn getchans-or-fetch [ids]
  (let [rows (fliplist (db/channels-by-ids db/db {:ids ids}) :itunesid)
        resids (set (mapv #(:itunesid %) (vals rows)))
        validdate (jt/minus (jt/instant) (jt/days 1))
        ]
    (loop [ret '() pods ids]
      (if (seq pods)
        (if (contains? resids (first pods))
          (recur  (conj ret (chan-fetchorreadcache
                             (get rows (first pods)) validdate)) (rest pods))
          (let [lookup (first (itunes/lookup-ids [(first pods)]))]
            (add-channel (get lookup "collectionId")
                         (get lookup "feedUrl"))
            (recur (conj ret
                         (first (db/channels-by-ids db/db {:ids [(first pods)]})))
                   (rest pods))
            )
          )
        ret)
      )
    ))

(defn geteps-or-fetch [id]
  (let [oldrow (first (db/channels-by-ids db/db {:ids [id]}))]
    (cond (not oldrow)
          (let [lookup (first (itunes/lookup-ids [id]))]
            (add-channel (get lookup "collectionId")
                         (get lookup "feedUrl"))
            )

          (jt/before? (:lastfetched oldrow)
                      (jt/minus (jt/offset-date-time) (jt/minutes 30)))
          (add-channel (:itunesid oldrow) (:feedurl oldrow))
          )
    (db/eps-by-chanid db/db {:id id})
    )
  )


(def sqlchan (a/chan))

(defn populate-chans []
  (let [fetchedAt (jt/offset-date-time)]
    (count (into '()
                 (map-indexed
                  #(if (get %2 "feedUrl")
                     (a/go (a/>! sqlchan
                                 (add-channel (get %2 "collectionId")
                                                    (get %2 "feedUrl")
                                                    (+ %1 1)
                                                    fetchedAt))
                           )
                     nil)

                  (itunes/ituneslookup (itunes/fetchchart))
                  )))))
