# mini-reframe

## Overview

After spending some time studying the wonderful [`re-frame`][1] source code, I
created this `mini-reframe` demo in the attempt to replicate what I think the
core of `re-frame` is. Since the core of the mini-reframe is so small, I have no
intention to release it as a library/framework. If you think it's going to help
your project, feel free to copy and paste the `mini-reframe.event-loop`
ns. That's all you need :)

## Mini?

Mini-reframe is mini in a few aspects:

- __Core is mini__
  - The core of mini-reframe is the `mini-reframe.event-loop` ns, which has 53
    LoC including doc-string and comments at this point.
- __Scope is mini__
  - There's no global app-state or global handler registry.
- __Feature set is mini__
  - This is for study purpose so I didn't include many awesome features from
    re-frame, such as interceptors.

## Self-guided source code tour

- `mini-reframe.event-loop`: This is the core of `mini-reframe`. Everything else
  is for demo purpose.
- `mini-reframe.app`: This is the starting point of the demo app. If you just
  want to see the usage of `mini-reframe`, start from here. It has the global
  event loop defined. Checkout the router controller that kicks off the local
  event loop and dispatches the `:init` local event.
- `mini-reframe.global`: This namespace contains the global state, the global
  event channel, the global event handler, and the global fx handler. Also the
  syntactic sugar APIs - `dispatch!` and `subscribe`.
- `mini-reframe.home-page`: The stateless home page.
- `mini-reframe.evil-page`: The stateful evil page that contains its local
  event-loop. See the definition of `event-handler`, `effect-handler`, and
  `subscribe`. Also checkout the way to handle the `:http` fx. It's inspired by
  the [re-frame-http-fx][2] library.

## Usage

To start a event loop in the background, you'll need to prepare a few things:

1. An state reagent atom `state`
2. A core.async channel `events-ch`
3. A map of `event-handler`
4. A map of `fx-handler`

For example, the `mini-reframe.global` ns defines:

```clojure
(defonce state (atom {}))

(def events-ch (a/chan))

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
```

And the `mini-reframe.app/init!` starts the event loop with:

```clojure
(event-loop/start-event-loop! global/events-ch
                              global/state
                              global/event-handler
                              global/fx-handler)
```

## Development

To get an interactive development environment run:

    clojure -A:fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    rm -rf target/public

To create a production build run:

	rm -rf target/public
	clojure -A:fig:min


## License

Copyright © 2021 Daw-Ran Liou

Distributed under the MIT License.

[1]:https://github.com/day8/re-frame/
[2]:https://github.com/day8/re-frame-http-fx
