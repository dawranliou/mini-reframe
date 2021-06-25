# mini-reframe

## Overview

After spending some time studying the wonderful [re-frame][1] source code, I
created the `mini-reframe` demo in the attempt to replicate what I think is the
core of the re-frame framework. Also, there are some tweaks to fit my
personal tastes. Since the core of the mini-reframe is so small, I don't think
I'd release it as a library/framework. If you think it's going to help your
project, feel free to copy and paste the `mini-reframe.event-loop` ns. That's
all you need :)

## Why mini?

Mini-reframe is mini in a few aspects:

- Core is mini: the core of mini-reframe is the `mini-reframe.event-loop` ns,
  which is 53 LoC with doc-string and comments at this point.
- Scope is mini: there's no global app-state or global handler registry.

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

Copyright © 2018 Daw-Ran Liou

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
