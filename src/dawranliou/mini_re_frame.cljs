(ns ^:figwheel-hooks dawranliou.mini-re-frame
  (:require
   [goog.dom :as gdom]
   [goog.events :as gevents]
   [reagent.core :as reagent :refer [atom]]
   [reagent.ratom]
   [reagent.dom :as rdom]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.controllers :as rfc]
   [clojure.string :as str]
   [clojure.core.async :as a]))

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

;; App global state

(defonce global-state (atom {}))

(def global-events-ch (a/chan))

(defn dispatch-global-event! [event]
  (a/put! global-events-ch event))

(def global-event-handler
  {:navigate
   (fn [db [_event-type new-match]]
     (when new-match
       (let [old-controllers (:controllers (:current-route db))
             controllers     (rfc/apply-controllers old-controllers new-match)
             new-route       (assoc new-match :controllers controllers)]
         {:db (assoc db :current-route new-route)})))
   :log
   (fn [_db [_event-type data]]
     {:log data})})

(def global-fx-handler
  {:db  (fn [state _effect-key new-db]
          (when-not (identical? new-db @state)
            (reset! state new-db)))
   :log (fn [_state _effect-key data]
          (js/console.log data))})

(def global-subscribe
  {:current-route (reagent.ratom/make-reaction
                    #(get-in @global-state [:current-route]))})

;; Page state

(def events-ch (a/chan))

(defn dispatch! [event]
  (a/put! events-ch event))

(defonce page-state (atom {}))

(def subscribe
  {:h    (reagent.ratom/make-reaction
           #(str "H - " (or (get-in @page-state [:h]) 0)))
   :j    (reagent.ratom/make-reaction
           #(str "j - " (or (get-in @page-state [:j]) 0)))
   :k    (reagent.ratom/make-reaction
           #(str "k - " (or (get-in @page-state [:k]) 0)))
   :l    (reagent.ratom/make-reaction
           #(str "L - " (or (get-in @page-state [:l]) 0)))
   :http (reagent.ratom/make-reaction
           #(str "Server status - "
                 (name (or (get-in @page-state [:http]) :unknown))))})

(comment
  @global-state
  @page-state
  (js/console.log (first (:listeners @page-state)))
  @(subscribe :h))

(defn handle-init
  [_db _event]
  {:listen [{:src      js/document
             :type     "keydown"
             :listener [:keydown]}]
   :http   {:method     :get
            :url        "http://localhost:1111"
            :on-success [:good-http-result]
            :on-failure [:bad-http-result]}})

(defn handle-listened
  [db [_event-type listener-key]]
  {:db (update db :listeners conj listener-key)})

(defn handle-teardown
  [db _event]
  {:db       (dissoc db :listeners :http :h :j :k :l)
   :unlisten (:listeners db)})

(defn handle-good-http-result
  [db [_event-type data]]
  {:db (assoc db :http (:body data))})

(defn handle-bad-http-result
  [db [_event-type data]]
  {:db (assoc db :http (:body data))})

(defn handle-clicked
  [db [_event-type {:keys [element] :as _data}]]
  {:db (update db element inc)})

(defn handle-keydown
  [db [_event-type evt]]
  (when-let [key (#{"h" "j" "k" "l"} (.-key evt))]
    {:db (update db (keyword key) inc)}))

(defn handle-reset
  [db _event]
  {:db (dissoc db :h :j :k :l)})

(def event-handler
  {:init             handle-init
   :listened         handle-listened
   :teardown         handle-teardown
   :good-http-result handle-good-http-result
   :bad-http-result  handle-bad-http-result
   :clicked          handle-clicked
   :keydown          handle-keydown
   :reset            handle-reset})

(defn do-db!
  [page-state _effect-key new-db]
  (when-not (identical? new-db @page-state)
    (reset! page-state new-db)))

(defn do-http!
  [_page-state _effect-key {:keys [_method _url on-success on-failure]}]
  (if (zero? (rand-int 3))
    ;; Simulate HTTP failures once out of 3 tries
    (js/setTimeout
      #(dispatch! (conj on-failure {:body :bad}))
      (+ 2000 (rand-int 1000)))
    (js/setTimeout
      #(dispatch! (conj on-success {:body :good}))
      (+ 2000 (rand-int 1000)))))

(defn do-dispatch!
  [_page-state _effect-key event-v]
  (dispatch! event-v))

(defn do-dispatch-global!
  [_page-state _effect-key event-v]
  (dispatch-global-event! event-v))

(defn do-listen!
  [_page-state _effect-key listeners]
  (doseq [{:keys [src type listener]} listeners]
    (let [listener-key
          (gevents/listen src type #(dispatch! (conj listener %)))]
      (dispatch! [:listened listener-key]))))

(defn do-unlisten!
  [_page-state _effect-key listeners]
  (doseq [listener listeners]
    (gevents/unlistenByKey listener)))

(def fx-handler
  {:db              do-db!
   :http            do-http!
   :dispatch        do-dispatch!
   :dispatch-global do-dispatch-global!
   :listen          do-listen!
   :unlisten        do-unlisten!})

;; Components

(defn app []
  [:<>
   [:nav
    [:ul
     [:li
      [:a {:href "#/"} "/"]]
     [:li
      [:a {:href "#/evil"} "/evil"]]]]
   (when-let [page @(global-subscribe :current-route)]
     [:main
      [(-> page :data :page)]])])

(defn home-page []
  [:h1 "Home"])

(defn evil-page []
  [:<>
   [:h1 "Evil"]
   [:button {:on-click #(dispatch! [:clicked {:element :h}])} "H"]
   [:button {:on-click #(dispatch! [:clicked {:element :j}])} "J"]
   [:button {:on-click #(dispatch! [:clicked {:element :k}])} "K"]
   [:button {:on-click #(dispatch! [:clicked {:element :l}])} "L"]
   [:button {:on-click #(dispatch! [:reset])} "Reset"]
   [:ul
    [:li @(subscribe :h)]
    [:li @(subscribe :j)]
    [:li @(subscribe :k)]
    [:li @(subscribe :l)]]
   [:p @(subscribe :http)]])

;; Router

(def routes
  [["/" {:name ::home
         :page home-page
         :controllers
         [{:start #(dispatch-global-event! [:log "Starting /"])
           :stop  #(dispatch-global-event! [:log "Stopping /"])}]}]
   ["/evil" {:name ::evil-page
             :page evil-page
             :controllers
             [{:start (fn [& _params]
                        (dispatch-global-event! [:log "Starting /evil"])
                        (start-event-loop! events-ch
                                           page-state
                                           event-handler
                                           fx-handler)
                        (dispatch! [:init]))
               :stop  (fn [& _params]
                        (dispatch-global-event! [:log "Stopping /evil"])
                        (dispatch! [:teardown]))}]}]])

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
  (start-event-loop! global-events-ch
                     global-state
                     global-event-handler
                     global-fx-handler)
  (rfe/start! router
              #(when % (dispatch-global-event! [:navigate %]))
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