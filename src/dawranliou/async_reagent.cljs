(ns ^:figwheel-hooks dawranliou.async-reagent
  (:require
   [goog.dom :as gdom]
   [goog.events :as gevents]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [clojure.core.async :as a]))

(defn multiply [a b] (* a b))

(def events-ch (a/chan))

(defn dispatch [event]
  (a/put! events-ch event))

(defonce app-state (atom {:state :start :db {}}))

(comment
  @app-state)

(def fsm {:start        {:init :loading}
          :loading      {:received     :ready
                         :status-error :server-error
                         :reset :start}
          :server-error {:retry :loading
                         :reset :start}
          :ready        {:reset :start}})

(defonce keydown-listener
  (gevents/listen js/document "keydown"
                  #(dispatch [:keydown (.-key %)])))

(defmulti handle-event (fn [_db event] (first event)))

(defmethod handle-event :init
  [_db _event]
  (println "Retrieving data...")
  {:transition :init
   :http       {:method     :get
                :url        "http://localhost:1111"
                :on-success [:good-http-result]
                :on-failure [:bad-http-result]}})

(defmethod handle-event :good-http-result
  [db [_event-type data]]
  (println "Retrieved data" data)
  {:db (assoc db :http data)
   :transition :received})

(defmethod handle-event :bad-http-result
  [db [_event-type data]]
  (println "Error retrieving data" data)
  {:db (assoc db :http data)
   :transition :status-error})

(defmethod handle-event :clicked
  [db [_event-type {:keys [element] :as _data}]]
  (println _data)
  {:db (update db element inc)})

(defmethod handle-event :keydown
  [db [_event-type key]]
  (when (#{"h" "j" "k" "l"} key)
    (println key)
    {:db (update db (keyword key) inc)}))

(defmethod handle-event :reset
  [_db _event]
  (println "Reset")
  {:transition :reset
   :db {}
   :dispatch [:init]})

(defmulti handle-effect
  (fn [effect _effect-data] effect))

(defmethod handle-effect :transition
  [_effect-key transition]
  (let [old-state (:state @app-state)
        new-state (get-in fsm [old-state transition])]
    (println "From state" old-state "to state" new-state)
    (swap! app-state assoc :state new-state)))

(defmethod handle-effect :db
  [_effect-key new-db]
  (when-not (identical? new-db (:db @app-state))
    (swap! app-state assoc :db new-db)))

(defmethod handle-effect :http
  [_effect-key {:keys [_method _url on-success on-failure]}]
  (if (zero? (rand-int 3))
    (js/setTimeout
      #(dispatch (conj on-failure {:message :server-error}))
      (+ 2000 (rand-int 1000)))
    (js/setTimeout
      #(dispatch (conj on-success {:message :server-good}))
      (+ 2000 (rand-int 1000)))))

(defmethod handle-effect :dispatch
  [_effect-key [event-type event-data :as event-v]]
  (println ":dispatch event" event-type "with data" event-data)
  (dispatch event-v))

(defn do-effects!
  [{:keys [db transition] :as effects}]
  (when db
    (handle-effect :db db))
  (doseq [[effect-key effect-value] (dissoc effects :db :transition)]
    (handle-effect effect-key effect-value))
  (when transition
    (handle-effect :transition transition)))

(a/go-loop []
  (let [event   (a/<! events-ch)
        effects (handle-event (:db @app-state) event)]
    (do-effects! effects))
  (recur))

(dispatch [:init {}])

(defn app []
  [:main
   [:h1 "Evil navigation"]
   [:button
    {:on-click #(dispatch [:clicked {:element :h}])}
    "H"]
   [:button
    {:on-click #(dispatch [:clicked {:element :j}])}
    "J"]
   [:button
    {:on-click #(dispatch [:clicked {:element :k}])}
    "K"]
   [:button
    {:on-click #(dispatch [:clicked {:element :l}])}
    "L"]
   [:button
    {:on-click #(dispatch [:reset])}
    "Reset"]
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
