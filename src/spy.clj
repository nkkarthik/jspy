(ns spy
  (:use [nodes]
        [tree]
        [gui]
        [print :as p :only ()])
  (:import
   [nodes ArgumentNode MethodEntryNode MethodExitNode
    ThrowExceptionNode CatchExceptionNode]
   [com.sun.jdi ArrayReference ClassObjectReference
    ObjectReference PrimitiveValue StringReference VoidValue
    VMDisconnectedException]
   [com.sun.jdi.request MethodEntryRequest EventRequest
    MethodExitRequest StepRequest]
   [com.sun.jdi.event MethodEntryEvent MethodExitEvent
    VMDeathEvent VMDisconnectEvent VMStartEvent
    ExceptionEvent StepEvent]
   [com.sun.tools.jdi SocketAttachingConnector]))

(def opts (atom {:includes nil
                 :excludes nil
                 :host "localhost"
                 :port 5002
                 :exclude-methods nil}))

(defn spy-on [& {:keys [host port classes
                        exclude exclude-methods]}]
  {:pre [host port classes]}
  (let [excludes (set exclude)]
    (swap! opts conj {:host host
                      :port port
                      :includes [classes]
                      :excludes excludes
                      :exclude-methods exclude-methods})))

(defn port [p]
  (swap! opts conj {:port p}))

(defn remove-exclude [e]
  (swap! opts
         conj
         {:excludes (remove #{e} (:excludes @opts))}))

(defn exclude [e]
  (swap! opts
         conj
         {:excludes (conj (:excludes @opts) e)}))

(defn attach [host port]
  (let [conn (SocketAttachingConnector.)
        args (.defaultArguments conn)]
    (.setValue (.get args "hostname") host)
    (.setValue (.get args "port") port)
    (.attach conn args)))

(defn enable-request [req]
  (doto req
    (.setSuspendPolicy EventRequest/SUSPEND_EVENT_THREAD)
    (.enable)))

(defn add-filter [fs fn]
  (doall (map fn fs)))

(defn entry-filters [includes excludes req]
  (add-filter includes
              #(.addClassFilter #^MethodEntryRequest req #^String %))
  (add-filter excludes
              #(.addClassExclusionFilter #^MethodEntryRequest req %))
  req)

(defn exit-filters [includes excludes req]
  (add-filter includes
              #(.addClassFilter #^MethodExitRequest req #^String %))
  (add-filter excludes
              #(.addClassExclusionFilter #^MethodExitRequest req %))
  req)

(defn entry-request [vm filter]
  (enable-request
   (filter (.createMethodEntryRequest (.eventRequestManager vm)))))

(defn exit-request [vm filter]
  (enable-request
   (filter (.createMethodExitRequest (.eventRequestManager vm)))))

(defn exception-request [vm]
  (enable-request
   (.createExceptionRequest (.eventRequestManager vm) nil true true)))

(defn step-request [thread]
  (let [req-manager (.eventRequestManager (.virtualMachine thread))
        sr (.createStepRequest req-manager
                               thread
                               StepRequest/STEP_LINE
                               StepRequest/STEP_OVER)]
    (.addCountFilter sr 1)
    (enable-request sr)))

(defn delete-step-request [thread]
  (let [req-manager (.eventRequestManager (.virtualMachine thread))]
    (.deleteEventRequests req-manager
                          (.stepRequests req-manager))))

(defn event-requests [vm includes excludes]
  [(exception-request vm)
   (entry-request vm (partial entry-filters includes excludes))
   (exit-request vm (partial exit-filters includes excludes))])

(defn attach-events [vm includes excludes]
  (event-requests vm includes excludes)
  vm)

(defn make-vm
  ([]
     (make-vm (:host @opts)
              (:port @opts)
              (:includes @opts)
              (:excludes @opts)))
  ([host port includes excludes]
     (println port includes excludes)
     (attach-events (attach host port) includes excludes)))

(defn- event-itr-seq [event-itr]
  (lazy-seq
   (if (.hasNext event-itr)
     (let [ne (.nextEvent event-itr)]
       (lazy-cat [ne] (event-itr-seq event-itr)))
     [])))

(defn- event-set-seq [event-set]
  (when event-set
    (lazy-cat (event-itr-seq (.eventIterator event-set))
              (do
                (.resume event-set)
                []))))

(defn event-queue-seq [queue]
  (lazy-cat (event-set-seq (.remove queue 10000))
            (event-queue-seq queue)))

(defn event-queue [vm]
  (event-queue-seq (.eventQueue vm)))

(defn enable-event-requests [vm enabled]
  (doseq [r (.methodEntryRequests (.eventRequestManager vm))]
    (.setEnabled r enabled))
  (doseq [r (.methodExitRequests (.eventRequestManager vm))]
    (.setEnabled r enabled))
  (doseq [r (.exceptionRequests (.eventRequestManager vm))]
    (.setEnabled r enabled)))

(defn invoke-method [value thread method args]
  (try
    (.invokeMethod value thread method args 1)
    (catch Exception e nil)))

(defn to-string [thread value]
  (let [vm (.virtualMachine thread)
        to-string-method (first
                          (.methodsByName (.referenceType value)
                                          "toString"
                                          "()Ljava/lang/String;"))]
    (enable-event-requests vm false)
    (let [val (invoke-method value
                             thread
                             to-string-method
                             (java.util.ArrayList.))]
      (enable-event-requests vm true)
      (if val (.value val) (.toString value)))))

(defn- get-value [t v]
  (if (not v)
    nil
    (cond (instance? VoidValue v)
          "void"
          (instance? ArrayReference v)
          (into [] (map (partial get-value t) (.getValues v)))
          (or (instance? PrimitiveValue v)
              (instance? StringReference v))
          (.value v)
          (instance? ClassObjectReference v)
          (.toString (.reflectedType v))
          (instance? ObjectReference v)
          (to-string t v)
          :else
          (.toString v))))

(defn exit-method [e]
  (let [m (.method #^MethodExitEvent e)
        t (.thread #^MethodExitEvent e)]
    (MethodExitNode. m
                     (.name t)
                     (.name (.declaringType m))
                     (.returnTypeName m)
                     (get-value t (.returnValue #^MethodExitEvent e)))))

(defn entry-method [e]
  (let [m (.method #^MethodEntryEvent e)
        t (.thread #^MethodEntryEvent e)
        f (.frame t 0)
        args (try
               (.arguments m)
               (catch Exception e []))
        arg-values (doall (map #(.getValue f %) args))
        arg-types (doall
                   (map (fn [arg val]
                          (if val
                            (-> val .type .name)
                            (.typeName arg)))
                        args
                        arg-values))]
    (MethodEntryNode. m
                      (.name t)
                      (.name (.declaringType m))
                      (doall
                       (map (fn [arg val type]
                              (ArgumentNode. (.name arg)
                                             type
                                             (get-value t val)))
                            args
                            arg-values
                            arg-types)))))

(defn methods-on-stack [thread]
  (doall
   (map #(-> % .location .method)
        (.frames thread))))

(defn handle-exception [e]
  (let [t (.thread #^ExceptionEvent e)
        msg (get-value t (.exception #^ExceptionEvent e))]
    (step-request t)
    (ThrowExceptionNode. (.name t) msg (methods-on-stack t))))

(defn handle-step [e]
  (let [t (.thread #^StepEvent e)]
    (delete-step-request t)
    (CatchExceptionNode. (.name t) (methods-on-stack t))))

(defn make-method [e]
  (cond (instance? MethodEntryEvent e)
        (entry-method e)
        (instance? MethodExitEvent e)
        (exit-method e)
        (instance? ExceptionEvent e)
        (handle-exception e)
        (instance? StepEvent e)
        (handle-step e)
        :else (throw (Exception. (str "Unknown event:" e)))))

(defn collect-events [events]
  (let [data (atom [])
        es (atom events)
        start? #(instance? VMStartEvent %)
        death? #(instance? VMDeathEvent %)
        disconnect? #(instance? VMDisconnectEvent %)]
    (try
      (while (not (disconnect? (first @es)))
        (when (not (or (start? (first @es))
                       (death? (first @es))))
          (let [m (make-method (first @es))]
            (swap! data conj m)))
        (swap! es rest))
      (catch Exception e))
    @data))

(defn throw-catch->exit-events [events]
  "Converts throw/catch events to method exit events"
  (let [throw? #(= :throw (node-type %))]
    (lazy-seq
     (cond (empty? events)
           []
           (not (throw? (first events)))
           (cons (first events) (throw-catch->exit-events (rest events)))
           :else
           (let [throw (first events)
                 catch (second events)
                 poped-methods (drop-last (count (:stack catch))
                                          (:stack throw))]
             (concat (map (fn [m]
                            (MethodExitNode. m
                                             (:thread throw)
                                             nil
                                             "Exception"
                                             (:message throw)))
                          poped-methods)
                     (throw-catch->exit-events (rest (rest events)))))))))

(defn remove-excluded-methods [events excluded]
  (filter (fn [e]
            (when (:method e)
              (not (some #{(.name (:method e))} excluded))))
          events))

(def recorder (atom nil))

(defn make-recorder
  ([] (make-recorder (make-vm)))
  ([vm]
     (let [queue (event-queue vm)
           data (future-call #(collect-events queue))]
       {:stop #(try
                 (.dispose vm)
                 (catch VMDisconnectedException e))
        :data (fn []
                (remove-excluded-methods (throw-catch->exit-events @data)
                                         (:exclude-methods @opts)))})))

(defn group-by-thread [grouped methods]
  (if (empty? methods)
    grouped
    (let [m (first methods)
          k (keyword (:thread m))
          v (vec (k grouped))]
      (recur (assoc grouped k (conj v m))
             (next methods)))))                       

(defn thread-grouped-method-tree [methods]
  (let [thread-grouped-methods (group-by-thread nil methods)]
    (reduce conj
            {}
            (map (fn [[thread methods]]
                   [thread (make-method-tree methods)])
                 thread-grouped-methods))))

(defn print-data [data]
  (doall
   (map (fn [[k v]]
          (println "Thread:" k)
          (p/print-methods v))
        (thread-grouped-method-tree data))))

(defn load-spy-config []
  (try
    (load-file ".spy.clj")
    (catch Exception e (println "Info: .spy not loaded" e))))

(defn stop []
  (when @recorder
    ((:stop @recorder))))

(defn start
  ([]
     (stop)
     (load-spy-config)
     (reset! recorder (make-recorder))))

(defn data []
  (when @recorder
    ((:data @recorder))))

(defn save []
  (binding [*out* (java.io.FileWriter. "spy-tape")]
    (print-data (data))))

(defn show []
  (print-data (data)))

(defn gui []
  (stop)
  (when (data)
    (try
      (show-gui-tree
       (thread-grouped-method-tree (data)))
      (catch Throwable t (.printStackTrace t)))))

(load-spy-config)

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn launch []
  (let [con (com.sun.tools.jdi.SunCommandLineLauncher.)
        args (.defaultArguments con)]
    (.setValue (.get args "main") "spy.test.Main")
    (.setValue (.get args "options")
               (str "-cp " (System/getProperty "java.class.path")))
    (.launch con args)))

(defn istart []
  (let [vm (attach-events (launch)
                          ["spy.test.*"]
                          (:excludes @opts))]
    (reset! recorder (make-recorder vm))
    (.resume vm)))

;; ;; TESTING LOOP
;; ;; (let [vm (make-vm)
;; ;;       q (atom (event-queue vm))]
;; ;;   (try
;; ;;     (dotimes [i 1000]
;; ;;       (println "REPL: before make method")
;; ;;       (make-method (first @q))
;; ;;       (println "REPL: after make method")
;; ;;       (swap! q rest)
;; ;;       (println "REPL: swap rest"))
;; ;;     (finally (.dispose vm))))

