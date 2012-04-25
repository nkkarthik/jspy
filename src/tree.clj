(ns tree
  (:use [nodes :only (node-type)]))

(defn entry? [method] (= (node-type method) :entry))
(defn exit? [method] (= (node-type method) :exit))

(defn method-node [method parent children]
  (conj method {:parent parent :children children}))

(defn add-child [parent child]
  (conj parent {:children (conj (:children parent) child)}))

(defn with-return-val [entry-method exit-method]
  (conj entry-method (select-keys exit-method [:return :return-type])))

(defn make-method-tree
  ([methods]
     (make-method-tree {:children []} methods))
  ([parent [m & ms]]
     (cond (not m)
           (if (not (:parent parent))
             parent
             (recur (add-child (:parent parent) parent) ms))
           (entry? m)
           (recur (method-node m parent [])
                  ms)
           (exit? m)
           (if (not (= (:method m) (:method parent)))
             (recur parent ms) ; skip unknow exit event from exception
             (let [cur parent
                   parent (:parent parent)]
               (recur (add-child parent
                                 (with-return-val cur m))
                      ms))))))