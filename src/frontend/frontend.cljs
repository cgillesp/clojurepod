(ns frontend
  (:require [goog.dom]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [reagent.dom.server :as rds]
            [ajax.core :as http]
            [fecomponents :as cts]
            [dexie :as dx]
            ))

(def global (r/atom {:searchoverlay false}))

(def defaultpods
  '(394775318 ;; 99% Invisible
    1200361736 ;; The Daily
    1222114325 ;; Up First
    1548604447 ;; Ezra Klein
    1567098962 ;; On Our Watch
    121493675 ;; NPR Now
    917918570 ;; Serial
    1564995058 ;; Day X
    290783428 ;; Planet Money
    1469394914 ;; The Journal
    1057255460 ;; NPR Politics
    ))

(def podidlist defaultpods)

(def subsc (r/cursor global [:subs]))
(def subinfoc (r/cursor global [:subinfo]))
(def podinfoc (r/cursor global [:podinfo]))
(def detailpodc (r/cursor global [:detailpod]))
(def epc (r/cursor global [:eps]))
(def nowplayingc (r/cursor global [:nowplaying]))
(def playstatus (r/cursor global [:playstatus]))
(def playing? (r/cursor global [:playstatus :playing?]))
(def duration (r/cursor global [:playstatus :duration]))
(def currenttime (r/cursor global [:playstatus :currenttime]))
(def searchoverlay (r/cursor global [:searchoverlay]))
(def searchquery (r/cursor global [:searchquery]))
(def searchresults (r/cursor global [:searchresults]))

(defn setval [k v] (swap! global #(assoc %1 k v)))

(def barpressed (r/atom false))

;; subs
;; - id
;;
;; podinfo
;; - title
;; - itunesID
;; - image
;; - subtitle
;; - description
;; - author
;; - link
;; - copyright
;;
;; episode
;; - channel
;; - guid
;; - title
;; - pubDate
;; - description
;; - itunesSummary
;; - subtitle
;; - duration
;; - link
;; - image
;; - fileUrl
;; - fileSize
;; - fileType
;;
;; episodeStatus
;; - channel
;; - guid
;; - isActive
;; - isFinished
;; - time

;; Initialize db
(def dxdb (new dx/Dexie "pinkcast"))

(-> dxdb (.version 1) (.stores (clj->js {"subs" "id"
                                         "channels" "&itunesID, title"
                                         "episode" "&[channel+guid], channel, guid, title, pubDate"
                                         "episodeStatus" "&[channel+guid], channel, &guid, isActive, isFinished"
                                         "meta" "&key"})))
(defn idb-addsub [id]
  (-> dxdb (.-subs) (.put (clj->js {:id id}))))

(defn idb-delsub [id]
  (-> dxdb (.-subs) (.delete id)))
;; ;; then sync pod info
(declare channels-atom-sync)


(swap! global assoc :subs (set []))
(swap! global assoc :eps {})
(declare get-channel-info)
(defn addsub [id fetch?] (do
                    (swap! global #(assoc % :subs (conj (:subs %) id)))
                    (idb-addsub id)
                    (if fetch?
                      (get-channel-info [id]))
                    ))

(declare delchannel)
(defn delsub [id] ((swap! global #(assoc % :subs (disj (:subs %) id)))
                   (delchannel id)
                   (idb-delsub id)))

(defn modsub [addordel id fetch?]
  (if (= addordel :add)
    (addsub id fetch?)
    (if (= addordel :del)
      (delsub id))))

(declare get-channel-info)
;; Add default pods if there aren't any yet
(defn poppods [in] (do (if (= (count in) 0)
                         (doall (map addsub defaultpods))
                         (doall (map addsub in)))
                       (print (type in))

                       (get-channel-info @subsc)
                       ;; (setval :detailpod (first @podinfoc))
                       ))

(-> dxdb (.-subs) (.toArray)
    (.then #(->> % (js->clj) (map (fn [x] (get x "id")))
                 (poppods))))

(defn vetchannels [] nil)

(defn updatechannel [] nil)

(defn pushchannel [chan] (do (swap! global
                                #(assoc % :subinfo (assoc (:subinfo %)
                                                          (:itunesID chan) chan)))

                             (swap! global
                                #(assoc % :podinfo (assoc (:podinfo %)
                                                          (:itunesID chan) chan)))

                             ))

(defn delchannel [id] (swap! global
                             #(assoc % :subinfo
                                     (dissoc (:subinfo %) id))))

;; Update feeds & channels

(defn veteps [] nil)

(defn updatefeed [] nil)

;; :eps -> [id] -> episode data

(defn reformateps [eps ep] (conj (or (get eps (:channel ep)) []) ep))

(defn pushep [epn] (let [ep (js->clj epn :keywordize-keys true)] (do
                    (swap! global
                             #(assoc % :eps (assoc (:eps %) (:channel ep) (reformateps (:eps %) ep) )))
                      )))

(defn delep [chan epid] (swap! global
                               #(assoc % :eps
                                       (assoc (:eps %) chan
                                              (filter (fn [ep] (= (:guid ep) epid))  (get % chan))))))

(defn parse-fetch-date [m] (clj->js (let [mc (js->clj m)]
                                      (assoc mc "lastFetched"
                                             (js/Date.parse (get mc "lastFetched"))))))
(declare loadfeeds)
(defn add-info [resp] (do
                        (doall (map (comp pushchannel
                                          #(js->clj % :keywordize-keys true)
                                          parse-fetch-date)
                                    (js/JSON.parse resp)))
                        (js/setTimeout loadfeeds 300)
                        )
  )

(defn ingest-feed [resp]
  (let [parsed (js->clj (js/JSON.parse resp) :keywordize-keys true)]
    (doall (for [ep (get parsed :episodes)]
             (do
               (if (= nil (get ep :guid)) (print (-> parsed (get :channel) (get :title))))
               ;; (print (get ep "guid"))
               (pushep ep)))))
  )

(defn loadfeeds [] (doall (for [id (map #(:itunesID %) (vals (:subinfo @global)))]
                            (http/GET "/api/v1/podcasts/feed/"
                                      {:params {:q id} :handler ingest-feed}))) nil)
(defn get-channel-info [ids]
  (http/GET "/api/v1/podcasts/info/" {:params {:q ids} :handler add-info}))


(defn app []
  [cts/main-view subinfoc detailpodc setval
   epc nowplayingc playstatus searchoverlay
   searchquery searchresults modsub
   subsc podinfoc])


;; Imperative things
(let [appbox (goog.dom/getElement "app")]
  (if appbox
    (rd/render [app] appbox)))

(reset! playing? false)

(defn add-search-results [time]
  (fn [resp]
    (let [parsed (js->clj (js/JSON.parse resp) :keywordize-keys true)]
      (js/console.log (clj->js parsed))
      (swap! searchresults #(if (< (:time %1) time)
                              {:time time
                               :results parsed}
                              %1))
      )
    ))

(add-watch nowplayingc nil #(js/setTimeout (fn [] (-> (.getElementById js/document "audioplayer")
                                                      (.play))) 80))

(add-watch searchquery nil
           #(js/setTimeout (fn [] (if (and (not (empty? %4)) (= @searchquery %4))
                                    (http/GET "/api/v1/search/"
                                              {:params {:q %4} :handler
                                               (add-search-results (js/Date.now))})
                                    )) 200))

(def audiotag (.getElementById js/document "audioplayer"))
(.addEventListener audiotag "timeupdate"
                   #(if (not @barpressed)
                      (reset! currenttime (.-currentTime audiotag))))
(.addEventListener audiotag "pause"
                   #(reset! playing? false))
(.addEventListener audiotag "play"
                   #(reset! playing? true))
(.addEventListener audiotag "durationchange"
                   #(reset! duration (.-duration audiotag)))
(.addEventListener (js/document.getElementById "rangebar") "input"
                   (fn [e] (do (reset! barpressed true)
                               (reset! currenttime (.-target.value e)))))
(.addEventListener (js/document.getElementById "rangebar") "change"
                   (fn [e] (do (reset! barpressed false)
                               (set! (.-currentTime audiotag) (.-target.value e)))))
