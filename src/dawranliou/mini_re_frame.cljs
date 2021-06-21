(ns ^:figwheel-hooks dawranliou.mini-re-frame
  (:require
   [goog.dom :as gdom]
   [goog.events :as gevents]
   [reagent.core :as reagent :refer [atom]]
   [reagent.ratom]
   [reagent.dom :as rdom]
   [clojure.string :as str]
   [clojure.core.async :as a]))

(def events-ch (a/chan))

(defn dispatch! [event]
  (a/put! events-ch event))

(defonce app-state (atom {:state :start :db {}}))

(comment
  @app-state)

(def fsm {:start        {:init :loading}
          :loading      {:received     :ready
                         :status-error :server-error
                         :reset        :start}
          :server-error {:retry :loading
                         :reset :start}
          :ready        {:reset :start}})

(defonce keydown-listener
  (gevents/listen js/document "keydown"
                  #(dispatch! [:keydown (.-key %)])))

(defn handle-init
  [_db _event]
  (println "Retrieving data...")
  {:transition :init
   :http       {:method     :get
                :url        "http://localhost:1111"
                :on-success [:good-http-result]
                :on-failure [:bad-http-result]}})

(defn handle-good-http-result
  [db [_event-type data]]
  (println "Retrieved data" data)
  {:db         (assoc db :http (:body data))
   :transition :received})

(defn handle-bad-http-result
  [db [_event-type data]]
  (println "Error retrieving data" data)
  {:db         (assoc db :http (:body data))
   :transition :status-error})

(defn handle-clicked
  [db [_event-type {:keys [element] :as _data}]]
  (println _data)
  {:db (update db element inc)})

(defn handle-keydown
  [db [_event-type key]]
  (when (#{"h" "j" "k" "l"} key)
    (println key)
    {:db (update db (keyword key) inc)}))

(defn handle-reset
  [_db _event]
  (println "Reset")
  {:transition :reset
   :db         {}
   :dispatch   [:init]})

(def event-handler
  {:init             handle-init
   :good-http-result handle-good-http-result
   :bad-http-result  handle-bad-http-result
   :clicked          handle-clicked
   :keydown          handle-keydown
   :reset            handle-reset})

(defn handle-event
  [event-handler db [event-type :as event]]
  ((event-handler event-type) db event))

(defn do-transition!
  [_effect-key transition]
  (let [old-state (:state @app-state)
        new-state (get-in fsm [old-state transition])]
    (println "From state" old-state "to state" new-state)
    (swap! app-state assoc :state new-state)))

(defn do-db!
  [_effect-key new-db]
  (when-not (identical? new-db (:db @app-state))
    (swap! app-state assoc :db new-db)))

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
  [_effect-key [event-type event-data :as event-v]]
  (println ":dispatch! event" event-type "with data" event-data)
  (dispatch! event-v))

(def fx-handler
  {:transition do-transition!
   :db         do-db!
   :http       do-http!
   :dispatch   do-dispatch!})

(defn do-effects!
  [fx-handler {:keys [db] :as effects}]
  (when db
    ((fx-handler :db) :db db))
  (doseq [[effect-key effect-value] (dissoc effects :db)]
    ((fx-handler effect-key) effect-key effect-value)))

(a/go-loop []
  (let [event   (a/<! events-ch)
        effects (handle-event event-handler (:db @app-state) event)]
    (do-effects! fx-handler effects))
  (recur))

(def subscribe
  {:h    (reagent.ratom/make-reaction
           #(str "H - " (or (get-in @app-state [:db :h]) 0)))
   :j    (reagent.ratom/make-reaction
           #(str "j - " (or (get-in @app-state [:db :j]) 0)))
   :k    (reagent.ratom/make-reaction
           #(str "k - " (or (get-in @app-state [:db :k]) 0)))
   :l    (reagent.ratom/make-reaction
           #(str "L - " (or (get-in @app-state [:db :l]) 0)))
   :http (reagent.ratom/make-reaction
           #(str "Server status - " (name (or (get-in @app-state [:db :http])
                                              :unknown))))})

(dispatch! [:init {}])

(defn button [element-key]
  [:button
   {:on-click #(dispatch! [:clicked {:element element-key}])}
   (str/upper-case (name element-key))])

(defn li [element-key]
  [:li @(subscribe element-key)])

(defn app []
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
   [:p
    @(subscribe :http)]
   [:pre
    {}
    @app-state]])

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount [el]
  (rdom/render [app] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
