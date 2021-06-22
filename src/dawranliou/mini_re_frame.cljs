(ns ^:figwheel-hooks dawranliou.mini-re-frame
  (:require
   [goog.dom :as gdom]
   [reagent.ratom]
   [reagent.dom :as rdom]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [clojure.core.async :as a]
   [dawranliou.global :as global]
   [dawranliou.home-page :as home-page]
   [dawranliou.evil-page :as evil-page]))

;; Event loop abstraction

(defn handle-event
  [db event-handler [event-type & _event-params :as event-v]]
  {:event   event-v
   :effects ((event-handler event-type) db event-v)})

(defn handle-fx
  [state fx-handler {event                        :event
                     {:keys [db] :as effects-map} :effects}]
  ;; Do the :db effect before any other effects
  (when db
    ((fx-handler :db) state :db db))
  (doseq [[effect-key effect-value] (dissoc effects-map :db)]
    ((fx-handler effect-key) state effect-key effect-value))
  ;; Explicitly return the event
  event)

(defn event-xf
  [state event-handler fx-handler]
  (comp (map #(handle-event @state event-handler %))
        (map #(handle-fx state fx-handler %))))

(def event-history-ch (a/chan (a/sliding-buffer 10)))

(a/go-loop []
  (js/console.log (a/<! event-history-ch))
  (recur))

(defn -start-event-loop!
  [in-ch state event-handler fx-handler]
  (a/pipeline 1
              event-history-ch
              (event-xf state event-handler fx-handler)
              in-ch))

(def start-event-loop! (memoize -start-event-loop!))

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
                        (start-event-loop! evil-page/events-ch
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
  (start-event-loop! global/events-ch
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
