(ns mini-reframe.event-loop
  (:require
   [clojure.core.async :as a]))

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
  "Creates a transducer that takes in an event and makes the effects."
  [state event-handler fx-handler]
  (comp (map #(handle-event @state event-handler %))
        (map #(handle-fx state fx-handler %))))

(def event-history-ch
  "A channel that aggregates the event history."
  (a/chan (a/sliding-buffer 10)))

(comment
  ;; Evaluate this to print out the events
  (a/go-loop []
    (js/console.log (a/<! event-history-ch))
    (recur)))

(defn -start-event-loop!
  "For every event put onto the in-ch, run the stateful event-xf to generate the
  side effects and put the event onto the event-history-ch. The event-xf is
  created by the state, the event-handler map, and the fx-handler map."
  [in-ch state event-handler fx-handler]
  (a/pipeline 1
              event-history-ch
              (event-xf state event-handler fx-handler)
              in-ch))

(def start-event-loop!
  "The memoized version of -start-event-loop!.

  For every event put onto the in-ch, run the stateful event-xf to generate the
  side effects and put the event onto the event-history-ch. The event-xf is
  created by the state, the event-handler map, and the fx-handler map."
  (memoize -start-event-loop!))
