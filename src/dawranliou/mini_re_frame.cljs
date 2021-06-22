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

(def events-ch (a/chan))

(defn dispatch! [event]
  (a/put! events-ch event))

(defonce app-state (atom {}))

(comment
  @app-state
  (js/console.log (first (:listeners @app-state))))

(defn handle-init
  [_db _event]
  (println "Retrieving data...")
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
  (println "Retrieved data" data)
  {:db (assoc db :http (:body data))})

(defn handle-bad-http-result
  [db [_event-type data]]
  (println "Error retrieving data" data)
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

(defn handle-navigate
  [db [_event-type new-match]]
  (when new-match
    (let [old-match   (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      {:db (assoc db
                  :current-route (assoc new-match :controllers controllers))})))

(def event-handler
  {:init             handle-init
   :listened         handle-listened
   :teardown         handle-teardown
   :good-http-result handle-good-http-result
   :bad-http-result  handle-bad-http-result
   :clicked          handle-clicked
   :keydown          handle-keydown
   :reset            handle-reset
   :navigate         handle-navigate})

(defn handle-event
  [event-handler db [event-type :as event]]
  ((event-handler event-type) db event))

(defn do-db!
  [_effect-key new-db]
  (when-not (identical? new-db @app-state)
    (reset! app-state new-db)))

(defn do-http!
  [_effect-key {:keys [_method _url on-success on-failure]}]
  (if (zero? (rand-int 3))
    (js/setTimeout
      #(dispatch! (conj on-failure {:body :bad}))
      (+ 2000 (rand-int 1000)))
    (js/setTimeout
      #(dispatch! (conj on-success {:body :good}))
      (+ 2000 (rand-int 1000)))))

(defn do-dispatch!
  [_effect-key event-v]
  (dispatch! event-v))

(defn do-listen!
  [_effect-key listeners]
  (doseq [{:keys [src type listener]} listeners]
    (let [listener-key
          (gevents/listen src type #(dispatch! (conj listener %)))]
      (dispatch! [:listened listener-key]))))

(defn do-unlisten!
  [_effect-key listeners]
  (doseq [listener listeners]
    (gevents/unlistenByKey listener)))

(def fx-handler
  {:db       do-db!
   :http     do-http!
   :dispatch do-dispatch!
   :listen   do-listen!
   :unlisten do-unlisten!})

(defn do-effects!
  [fx-handler {:keys [db] :as effects}]
  (when db
    ((fx-handler :db) :db db))
  (doseq [[effect-key effect-value] (dissoc effects :db)]
    ((fx-handler effect-key) effect-key effect-value)))

(a/go-loop []
  (let [event   (a/<! events-ch)
        effects (handle-event event-handler @app-state event)]
    (do-effects! fx-handler effects))
  (recur))

(def subscribe
  {:h    (reagent.ratom/make-reaction
           #(str "H - " (or (get-in @app-state [:h]) 0)))
   :j    (reagent.ratom/make-reaction
           #(str "j - " (or (get-in @app-state [:j]) 0)))
   :k    (reagent.ratom/make-reaction
           #(str "k - " (or (get-in @app-state [:k]) 0)))
   :l    (reagent.ratom/make-reaction
           #(str "L - " (or (get-in @app-state [:l]) 0)))
   :http (reagent.ratom/make-reaction
           #(str "Server status - " (name (or (get-in @app-state [:http])
                                              :unknown))))
   :current-route
   (reagent.ratom/make-reaction
     #(get-in @app-state [:current-route]))})

(defn button [element-key]
  [:button
   {:on-click #(dispatch! [:clicked {:element element-key}])}
   (str/upper-case (name element-key))])

(defn li [element-key]
  [:li @(subscribe element-key)])

(defn app []
  [:<>
   [:nav
    [:ul
     [:li
      [:a {:href "#/"} "/"]]
     [:li
      [:a {:href "#/evil"} "/evil"]]]]
   (when-let [page @(subscribe :current-route)]
     [(-> page :data :page)])])

(defn home-page []
  [:h1 "Home"])

(defn evil-page []
  [:h1 "Evil"]
  [:main
   [:h1 "Evil navigation"]
   [button :h]
   [button :j]
   [button :k]
   [button :l]
   [:button
    {:on-click #(dispatch! [:reset])}
    "Reset"]
   [:ul
    [li :h]
    [li :j]
    [li :k]
    [li :l]]
   [:p @(subscribe :http)]])

(def routes
  [["/" {:name ::home
         :page home-page}]
   ["/evil" {:name ::evil-page
             :page evil-page
             :controllers
             [{:start #(dispatch! [:init])
               :stop  #(dispatch! [:teardown])}]}]])

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
  ;; conditionally start your application based on the presence of an "app" element
  ;; this is particularly helpful for testing this ns without launching the app
  (rfe/start! router #(when % (dispatch! [:navigate %])) {:use-fragment true})
  (mount-app-element))

(init!)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
