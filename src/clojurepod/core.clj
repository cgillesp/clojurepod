(ns clojurepod.core
  (:gen-class)
  (:require [hiccup2.core :as hc]
            [ring.util.codec :as rc]
            [ring.adapter.jetty :as rj]
            [hiccup.page]
            [clojure.data.json :as json]
            [ring.middleware.resource]
            [ring.middleware.content-type]
            [ring.middleware.not-modified]
            [clojure.data.xml :as xml]
            [reitit.ring :as rtr]
            [rum.core :as rum]
            [clojurepod.webapi :as webapi]
            )
  (:import [java.net URLEncoder]))




(declare router)
(defn app [request] (router request))


(defn -main [& args]
  (rj/run-jetty app {:port 3000 :join? false}))

(rum/defc common-head [] [:head
                     [:meta {:charset "UTF-8"}]
                     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
                     [:title "Pinkcast"]
                     [:link {:rel "stylesheet" :href "/static/style.css"}]]
  )
;;    <script src="cljs-out/dev-main.js"></script>
(rum/defc common-footer [] [:script {
                                     :src "/static/js/main.js"
                                     ;; :src "/cljs-out/dev/main_bundle.js"
                                     }])

(defn to-html [in] (rum/render-static-markup [:html
                                              (common-head)
                                              in
                                              (common-footer)]))

;; React shell
(defn react-shell [] [:body
                      [:div {:id "app"}]])

(defn react-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (to-html (react-shell))})

;; Routers

(defn static-middleware [handle] (-> handle
                                   (ring.middleware.resource/wrap-resource "public")
                                   (ring.middleware.content-type/wrap-content-type)
                                   (ring.middleware.not-modified/wrap-not-modified)
                                    ))


(def router
  (rtr/ring-handler
   (rtr/router
    [["/" {:get react-handler}]
     ["/static/*" {:get {:middleware [static-middleware]} :handler react-handler}]
     ["/api/v1/search/*" {:get webapi/search-handler}]
     ["/api/v1/podcasts/info/*" {:get webapi/podinfo-handler}]
     ["/api/v1/podcasts/feed/*" {:get webapi/podfeed-handler}]
     ])
   (rtr/routes
    (rtr/redirect-trailing-slash-handler {:method :add})
    (rtr/create-default-handler))
   ))
