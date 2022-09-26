(ns frontend
  (:require [goog.dom]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [ajax.core :as http]
            [fecomponents :as cts]
            [dexie :as dx]))

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
;; adds subscription to dexie database
(defn idb-addsub [id]
  (-> dxdb (.-subs) (.put (clj->js {:id id}))))

;; removes subscription
(defn idb-delsub [id]
  (-> dxdb (.-subs) (.delete id)))
;; ;; then sync pod info


(swap! global assoc :subs (set []))
(swap! global assoc :eps {})

(declare get-channel-info)

;; adds a subscription by id and optionally
;; gets info for the channel
(defn addsub ([id] (addsub id false))
  ([id fetch?]
   (swap! global #(assoc % :subs (conj (:subs %) id)))
   (idb-addsub id)
   (when fetch?
     (get-channel-info [id]))))

(declare delchannel)

;; removes subscription for id
(defn delsub [id] ((swap! global #(assoc % :subs (disj (:subs %) id)))
                   (delchannel id)
                   (idb-delsub id)))

;; adds or deletes a subscription based on tag
(defn modsub [addordel id fetch?]
  (if (= addordel :add)
    (addsub id fetch?)
    (when (= addordel :del)
      (delsub id))))

(declare get-channel-info)

;; Takes a list of podcasts and adds them to
;; the local state, and populates with the default
;; set if the list is empty
(defn populate-pods [in]  (if (= (count in) 0)
                            (doall (map addsub defaultpods))
                            (doall (map addsub in)))

  (get-channel-info @subsc))

;; reads out every podcast in the db, gets its id,
;; then passes the ids to populate-pods
(-> dxdb (.-subs) (.toArray)
    (.then #(->> % (js->clj) (map (fn [x] (get x "id")))
                 (populate-pods))))

;; adds a new channel to local state
(defn pushchannel [chan]  (swap! global
                                 #(assoc % :subinfo (assoc (:subinfo %)
                                                           (:itunesID chan) chan)))
  (swap! global
         #(assoc % :podinfo (assoc (:podinfo %)
                                   (:itunesID chan) chan))))
;; removes a channel from local state
(defn delchannel [id] (swap! global
                             #(assoc % :subinfo
                                     (dissoc (:subinfo %) id))))

;; :eps -> [id] -> episode data
;; eps: map between channels and episodes
;; ep: episode to add to the map
;; adds an episode to the of channels to their
;; corresponding episodes: map<channels: list<episode>>
(defn concat-ep [eps ep] (conj (or (get eps (:channel ep)) []) ep))

;; adds an episode to the local state
(defn pushep [epn] (let [ep (js->clj epn :keywordize-keys true)]
                     (swap! global
                            #(assoc % :eps (assoc (:eps %) (:channel ep) (concat-ep (:eps %) ep))))))

;; removes an episode from the local state
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn delep [chan epid] (swap! global
                               #(assoc % :eps
                                       (assoc (:eps %) chan
                                              (filter (fn [ep] (= (:guid ep) epid))  (get % chan))))))

;; parses the fetch date to a real date object
(defn parse-fetch-date [m] (clj->js (let [mc (js->clj m)]
                                      (assoc mc "lastFetched"
                                             (js/Date.parse (get mc "lastFetched"))))))

;; callback to add the channels' info once it's been fetched
(declare loadfeeds)
(defn add-info [resp]
  (doall (map (comp pushchannel
                    #(js->clj % :keywordize-keys true)
                    parse-fetch-date)
              (js/JSON.parse resp)))
  (js/setTimeout loadfeeds 300))

;; reads episodes from response to local data
(defn ingest-feed [resp]
  (let [parsed (js->clj (js/JSON.parse resp) :keywordize-keys true)]
    (doall (for [ep (get parsed :episodes)]
             (do
               (when (= nil (get ep :guid)) (print (-> parsed (get :channel) (get :title))))
               (pushep ep))))))

;; loads each feed we're subscribed to
(defn loadfeeds [] (doall (for [id (map #(:itunesID %) (vals (:subinfo @global)))]
                            #_{:clj-kondo/ignore [:unresolved-var]}
                            (http/GET "/api/v1/podcasts/feed/"
                              {:params {:q id} :handler ingest-feed}))) nil)
;; gets channel info by id array
(defn get-channel-info [ids]
  #_{:clj-kondo/ignore [:unresolved-var]}
  (http/GET "/api/v1/podcasts/info/" {:params {:q ids} :handler add-info}))


;; sets search results to the newly received values
;; (but only if the request time is the highest we've seen)
(defn add-search-results [time]
  (fn [resp]
    (let [parsed (js->clj (js/JSON.parse resp) :keywordize-keys true)]
      (js/console.log (clj->js parsed))
      (swap! searchresults #(if (< (:time %1) time)
                              {:time time
                               :results parsed}
                              %1)))))

(defn app []
  [cts/main-view subinfoc detailpodc setval
   epc nowplayingc playstatus searchoverlay
   searchquery searchresults modsub
   subsc podinfoc])

;; Imperative things
(let [appbox (goog.dom/getElement "app")]
  (when appbox
    (rd/render [app] appbox)))
(reset! playing? false)

;; Start playing an episode when it's set to "now playing"
(add-watch nowplayingc nil #(js/setTimeout (fn [] (-> (.getElementById js/document "audioplayer")
                                                      (.play))) 80))

;; triggers function whenever you type in the search bar
(add-watch searchquery nil
           #(js/setTimeout (fn [] (when (and (seq %4) (= @searchquery %4))
                                    #_{:clj-kondo/ignore [:unresolved-var]}
                                    (http/GET "/api/v1/search/"
                                      {:params {:q %4} :handler
                                       (add-search-results (js/Date.now))}))) 200))

;; updates clojure state to match the audio tag when it changes
(def audiotag (.getElementById js/document "audioplayer"))
(.addEventListener audiotag "timeupdate"
                   #(when (not @barpressed)
                      (reset! currenttime (.-currentTime audiotag))))
(.addEventListener audiotag "pause"
                   #(reset! playing? false))
(.addEventListener audiotag "play"
                   #(reset! playing? true))
(.addEventListener audiotag "durationchange"
                   #(reset! duration (.-duration audiotag)))

;; listens to the range bar and updates its position
(.addEventListener (js/document.getElementById "rangebar") "input"
                   (fn [e] (reset! barpressed true)
                     (reset! currenttime (.-target.value e))))

;; on release, sets the current time to the selected value
(.addEventListener (js/document.getElementById "rangebar") "change"
                   (fn [e] (reset! barpressed false)
                     (set! (.-currentTime audiotag) (.-target.value e))))
