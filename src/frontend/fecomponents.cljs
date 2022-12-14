(ns fecomponents
  (:require
   ["react-icons/gr" :as grr]
   ["react-icons/fa" :as fa]))

(defn top-bar [searchoverlay]
  [:div {:id "top-bar"}
   "Pinkcast"
   [:button {:id "searchbutton" :on-click #(reset! searchoverlay true)}
    "Add Podcasts"
    [fa/FaPlusCircle]]])

(declare cast-searchitem)

(defn search-overlay [searchquery searchresults modsub subsc]
  [:div {:id "searchoverlay"}
   [:div {:class "searchhed"} "Search Podcasts"]
   [:input {:id "searchbox" :type "text" :auto-focus true
            :value @searchquery
            :on-change #(reset! searchquery
                                (-> % .-target .-value))}]
   [:div {:class "searchresults"}
    (map-indexed (fn [i pod]
                   ^{:key i}
                   [cast-searchitem pod #(modsub %1 (:itunesID %2) true) subsc])
                 (:results @searchresults))]])

;; let you subscribe or view the itunes page for a podcast
(defn sidebuttons [change-subs subsc pod]
  [:div {:class "sidebuttons"}
   (if (contains? @subsc (:itunesID pod))
     [:button {:class "subbed searchsubbutton"
               :on-click #(change-subs :del pod)} "Unsubscribe"]
     [:button {:class "unsubbed searchsubbutton"
               :on-click #(change-subs :add pod)} "Subscribe"])
   [:a {:href (:itunesUrl pod) :class "itunesbutton"}
    [fa/FaApple] "iTunes"]])

(defn cast-searchitem [pod change-subs subsc]
  [:div {:class "cast-searchitem cast-listitem"}
   [:div {:class "cast-search-inner"}
    [:div {:class "listitem-imgcontainer"}
     [:img {:class "listitem-img" :src (:image pod)}]]
    [:div {:class "listitem-castt"}
     [:div {:class "listitem-castname"} (:title pod)]
     [:div {:class "listitem-castauthor"} (:author pod)]]]
   [sidebuttons change-subs subsc pod]])



(defn cast-listitem [pod on-click]
  [:div {:class "cast-listitem" :on-click #(on-click pod)}
   [:div {:class "listitem-imgcontainer"}
    [:img {:class "listitem-img" :src (:image pod)}]]
   [:div {:class "listitem-castt"}
    [:div {:class "listitem-castname"} (:title pod)]
    [:div {:class "listitem-castauthor"} (:author pod)]]])

(defn strip-the [in] (let [reg (last (re-find #"(?i)The (.*)" in))]
                       (if reg reg in)))

(defn alpha-titles [in] (sort #(->> %& (map (fn [x] (get x :title))) (map strip-the) (apply <)) in))

(defn top-view [subinfoc setval]
  [:div {:id "top-view" :class "viewcolumn"}
   (map-indexed (fn [i pod]
                  ^{:key i}
                  [cast-listitem pod #(setval :detailpod %)]) (alpha-titles
                                                               (vals @subinfoc)))])

(def audiotag #(.getElementById js/document "audioplayer"))

(defn detail-top [pod subsc change-subs setval eps nowplayingc playstatus]
  [:div {:class "detailtopview"}
   [:div {:class "dtvblurc"}
    [:img {:class "dtvblur" :src (:image pod)}]]
   [:div {:class "dtvcontent"}
    [:div {:class "dtvborderitems"}
     [:div {:class "dtvimgcontainer"}
      [:img {:class "dtvimg" :src (:image pod)}]]

     ;; show a subscribe/unsubscribe button depending on
     ;; subscription status
     (if (and pod (contains? @subsc (:itunesID pod)))
       [:button {:class "dtvsubbedbutton"
                 :on-click #(do (setval :detailpod pod) (change-subs :del pod))}
        [fa/FaCheck]]
       [:button {:class "dtvsubbedbutton"
                 :on-click #(change-subs :add pod)}
        [fa/FaPlus]])

     ;; button that plays/pauses the first episode of the feed
     (if (and (first eps) (and (= (first eps) @nowplayingc) (:playing? @playstatus)))
       [:button {:class "dtvplaybutton"
                 :on-click #(.pause (audiotag))}
        [fa/FaPause]]
       [:button {:class "dtvplaybutton"
                 :on-click #(do (setval :nowplaying (first eps))
                                (when (= (first eps) @nowplayingc)
                                  (.play (audiotag))))}
        [fa/FaPlay]])]
    [:div {:class "dtvtitle"}
     (:title pod)]
    [:div {:class "dtvauthor"}
     (:author pod)]
    [:div {:class "dtvdesc"}
     (:description pod)]]])

(defn ep-listitem [ep setval]
  [:div {:class "ep-listitem" :on-click #(do (setval :nowplaying ep))}
   [:div {:class "listitem-epwords"}
    [:div {:class "listitem-castname"} (:title ep)]
    [:div {:class "listitem-castauthor"} (:author ep)]]])

(defn eps-view [eps setval]
  [:div {:class "eps-view"}
   (if eps
     (map-indexed (fn [i ep]
                    ^{:key i}
                    [ep-listitem ep setval]) eps)
     [:div {:class "loadingmessage"} "Loading"])])


(defn detail-view [detailpod eps setval subsc modsub nowplayingc playstatus]
  [:div {:id "detail-view" :class "viewcolumn"}
   [detail-top detailpod subsc  #(modsub %1 (:itunesID %2) true) setval (get @eps (:itunesID detailpod)) nowplayingc playstatus]
   [eps-view (get @eps (:itunesID detailpod)) setval]
   ()])

;; look ma, no NPM!
(defn left-pad [n] (.padStart (str (js/Math.floor n)) 2 "0"))

(defn hms [n] (let [ni (js/Math.floor n)]
                (str
                 (when (< 3600 ni)
                   (str (js/Math.floor (/ ni 3600)) ":"))
                 (left-pad (mod (/ ni 60) 60))
                 ":" (left-pad (mod ni 60)))))

(defn player-view [nowplaying subinfo playstatus]
  [:div {:id "player-view" :class "viewcolumn"}
   [:div {:class "player-coverart"}
    (when @nowplaying
      [:img {:src (:image (get @subinfo (:channel @nowplaying)))}])]
   [:div {:class "player-info"}
    [:div {:id "player-title"} (:title @nowplaying)]
    [:div {:id "player-channel"} (:title (get @subinfo (:channel @nowplaying)))]]
   [:div {:class "player-controls"}
    [:div {:class "player-times"}
     [:div (hms (:currenttime @playstatus))]
     [:div (hms (:duration @playstatus))]]
    [:div {:id "playbars"}
     [:input {:type "range"
              :id "rangebar"
              :max (or (:duration @playstatus) 100)
              :value (or (:currenttime @playstatus) 0)
              ;; this is stupid but it gets rid
              ;; of a react warning
              :on-change #(constantly nil)}]]
    [:div {:id "playbuttons"}
     ;; rewind button
     [:button {:on-click
               #(set! (.-currentTime (audiotag)) (- (.-currentTime (audiotag)) 10))}
      [grr/GrBackTen #js{:className "controlbutton"}]]

     ;; play/pause button
     (if (:playing? @playstatus)
       [:button {:on-click #(.pause (audiotag))}
        [grr/GrPauseFill #js{:className "controlbutton"}]]

       [:button {:on-click #(.play (audiotag))}
        [grr/GrPlayFill #js{:className "controlbutton"}]])

     ;; fast forward button
     [:button {:on-click
               #(set! (.-currentTime (audiotag)) (+ (.-currentTime (audiotag)) 10))}
      [grr/GrForwardTen #js{:className "controlbutton"}]]]]

   [:audio {:id "audioplayer"
            :src (:fileUrl @nowplaying) :controls false :preload "metadata"}]])

(defn main-view [subinfoc detailpodc setval
                 epc nowplayingc playstatus
                 searchoverlay searchquery searchresults
                 modsub subsc podinfoc]
  [:div {:id "overall"}
  ;; shows search overlay if active
   (when @searchoverlay
     [:<>
      [:div {:class "shade" :on-click #(reset! searchoverlay false)} ""]
      [:div {:class "overlay"}
       [search-overlay searchquery searchresults modsub subsc]]])
   [:div {:id "appcontainer"}
    [top-bar searchoverlay]
    [:div {:id "main-view"}
    ;; three panel view
     [top-view subinfoc setval]
     [detail-view (or @detailpodc (first (alpha-titles (vals @subinfoc)))) epc setval subsc modsub nowplayingc playstatus]
     [player-view nowplayingc podinfoc playstatus]]]])
