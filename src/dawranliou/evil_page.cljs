(ns dawranliou.evil-page
  (:require
   [goog.dom :as gdom]
   [goog.events :as gevents]
   [reagent.core :as reagent :refer [atom]]
   [reagent.ratom]
   [reagent.dom :as rdom]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.controllers :as rfc]
   [dawranliou.global :as global]
   [clojure.core.async :as a]))

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
  (global/dispatch! event-v))

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

(defn main []
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
