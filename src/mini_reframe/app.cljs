(ns ^:figwheel-hooks mini-reframe.app
  (:require
   [goog.dom :as gdom]
   [mini-reframe.evil-page :as evil-page]
   [mini-reframe.global :as global]
   [mini-reframe.home-page :as home-page]
   [mini-reframe.event-loop :as event-loop]
   [reagent.dom :as rdom]
   [reagent.ratom]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]))

;; Components

(defn app []
  [:<>
   [:nav
    [:ul
     [:li
      [:a {:href "#/"} "/"]]
     [:li
      [:a {:href "#/evil"} "/evil"]]]]
   (when-let [page @(global/subscribe :current-route)]
     [:main
      [(-> page :data :page)]])])

;; Router

(def routes
  [["/" {:name ::home
         :page home-page/main
         :controllers
         [{:start #(global/dispatch! [:log "Starting /"])
           :stop  #(global/dispatch! [:log "Stopping /"])}]}]
   ["/evil" {:name ::evil-page
             :page evil-page/main
             :controllers
             [{:start (fn [& _params]
                        (global/dispatch! [:log "Starting /evil"])
                        (event-loop/start-event-loop! evil-page/events-ch
                                                      evil-page/page-state
                                                      evil-page/event-handler
                                                      evil-page/fx-handler)
                        (evil-page/dispatch! [:init]))
               :stop  (fn [& _params]
                        (global/dispatch! [:log "Stopping /evil"])
                        (evil-page/dispatch! [:teardown]))}]}]])

(def router
  (rf/router routes))

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(defn init! []
  ;; conditionally start your application based on the presence of an "app"
  ;; element this is particularly helpful for testing this ns without launching
  ;; the app
  (event-loop/start-event-loop! global/events-ch
                                global/state
                                global/event-handler
                                global/fx-handler)
  (rfe/start! router
              #(when % (global/dispatch! [:navigate %]))
              {:use-fragment true})
  (mount-app-element))

(init!)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
