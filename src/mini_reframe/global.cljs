(ns mini-reframe.global
  (:require
   [clojure.core.async :as a]
   [reagent.core :as reagent :refer [atom]]
   [reagent.ratom]
   [reitit.frontend.controllers :as rfc]))

;; App global state

(defonce state (atom {}))

(def events-ch (a/chan))

(defn dispatch! [event]
  (a/put! events-ch event))

(def event-handler
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

(def fx-handler
  {:db  (fn [state _effect-key new-db]
          (when-not (identical? new-db @state)
            (reset! state new-db)))
   :log (fn [_state _effect-key data]
          (js/console.log data))})

(def subscribe
  {:current-route (reagent.ratom/make-reaction
                    #(get-in @state [:current-route]))})
