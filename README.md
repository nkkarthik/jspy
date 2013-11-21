# spy

A simple java trace utility

## Requirements

1. Lein
2. JDK 1.6

## Usage

- Checkout into a directory (say *spy*)
 -- `mvn install:install-file -Dfile=tools.jar -DgroupId=com.sun -DartifactId=tools -Dversion=1.6.0 -Dpackaging=jar`
- `project.clj`: point `tools.jar` to the one in your JDK
- Change `.spy.clj`
  - Change `:host` and `:port` to point to the remote debug session
  - Set `:classes` to the base package of the classes to trace
  - Optionally, change `exclude-classes` list to packages/classes you want to exclude from trace
- Start repl (in the shell): `spy$ bin/lein repl`
- Start spy: `spy=> (spy)`

    This is assuming remote debug session is already started with jvm args `-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=<port-number>`

- Show trace (and stop spy): `spy=> (gui)`
- Exit with `Ctrl-C`

## License
    
    Copyright (C) 2011 BMLs
    Distributed under the Eclipse Public License, the same as Clojure.
