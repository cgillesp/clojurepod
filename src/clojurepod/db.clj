(ns clojurepod.db
  (:require [next.jdbc :as jdbc]
            [hugsql.core :as hs]
            [hugsql.adapter.next-jdbc :as hsna]
            [next.jdbc.result-set :as nrs]
            [java-time :as jt])
  (:import [java.sql ResultSet ResultSetMetaData]))


(def datafolder (or
                 (System/getenv "PERSIST_PATH")
                 (str (System/getProperty "user.home") "/.pinkcast")))
(defn make-home [] (let [dir (java.io.File. ^String datafolder)]
                     (if (.isDirectory dir) true (.mkdir dir))))
(make-home)


(defn date-column-reader
  [^ResultSet rs ^ResultSetMetaData rsmeta ^Integer i]
  (case (.getColumnClassName rsmeta i)
    "org.h2.api.TimestampWithTimeZone" (.getObject rs i java.time.OffsetDateTime)
    "java.sql.Time" (.getObject rs i java.time.LocalTime)
    "java.sql.Date" (.getObject rs i java.time.LocalDate)
    "java.sql.Timestamp" (.getObject rs i java.time.LocalDateTime)
    (.getObject rs i)))

(def db (jdbc/get-datasource {:dbtype "h2"
                              :dbname (str datafolder "/db" ";COMPRESS=TRUE")}))


(hs/set-adapter! (hsna/hugsql-adapter-next-jdbc {:builder-fn (nrs/as-maps-adapter nrs/as-unqualified-lower-maps date-column-reader)}))


(hs/def-db-fns "clojurepod/sql/tables.sql")

#_{:clj-kondo/ignore [:unresolved-symbol]}
(create-channels-table db)
#_{:clj-kondo/ignore [:unresolved-symbol]}
(create-episodes-table db)
#_{:clj-kondo/ignore [:unresolved-symbol]}
(create-topchart-table db)


(defn chanrow->map
  ([row] (chanrow->map row true))
  ([row convert-times?]
   (merge
    {:title (or (:title row) (:itunestitle row))
     :itunesID (:itunesid row)
     :image (:itunesimage row)
     :subtitle (:itunessubtitle row)
     :description (or (:description row) (:itunessumary row))
     :author (:itunesauthor row)
     :link (:link row)
     :copyright (:copyright row)
     :lastFetched (if convert-times?
                    (jt/format
                     (jt/formatter :iso-date-time)
                     (:lastfetched row))
                    (:lastfetched row))}
    (when-let [rank (:rank row)]
      {:rank rank}))))

(defn eprow->map
  ([row] (eprow->map row true))
  ([row convert-times?]
   {:channel (:channel row)
    :guid (:guid row)
    :title (:title row)
    :pubDate (if convert-times?
               (jt/format
                (jt/formatter :iso-date-time)
                (:pubdate row))
               (:pubdate row))
    :description (:description row)
    :itunesSummary (:itunessummary row)
    :subtitle (:itunessubtitle row)
    :duration (:itunesduration row)
    :link (:link row)
    :image (:itunesimage row)
    :fileUrl (:fileurl row)
    :fileSize (:filesize row)
    :fileType (:filetype row)
    :lastFetched (if convert-times?
                   (jt/format
                    (jt/formatter :iso-date-time)
                    (:lastfetched row))
                   (:lastfetched row))}))
