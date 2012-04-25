(ns nodes)

(defprotocol HasType (node-type [node]))

(defrecord ArgumentNode [name type value])

(defrecord MethodEntryNode [method thread class args]
  HasType (node-type [node] :entry))

(defrecord MethodExitNode [method thread class return-type return]
  HasType (node-type [node] :exit))

(defrecord ThrowExceptionNode [thread message stack]
  HasType (node-type [node] :throw))

(defrecord CatchExceptionNode [thread stack]
  HasType (node-type [node] :catch))

