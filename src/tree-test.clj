
(defn make-mnode [name ret parent children]
  {:name name
   :ret ret
   :parent parent
   :children children})

(defn mname [mnode] (:name mnode))
(defn mret [mnode] (:ret mnode))
(defn mparent [mnode] (:parent mnode))
(defn mchildren [mnode] (:children mnode))

(defn enter [name] {:type :enter :name name})
(defn exit [name ret] {:type :exit :name name :ret ret})
(defn enter? [mevent] (= (:type mevent) :enter))
(defn exit? [mevent] (= (:type mevent) :exit))

(defn add-child-node [parent child]
  (make-mnode (mname parent)
              (mret parent)
              (mparent parent)
              (conj (mchildren parent) child)))

(defn make-mtree [parent [m & ms]]
  (cond (not m)
        parent
        (enter? m)
        (recur (make-mnode (:name m) nil parent [])
               ms)
        (exit? m)
        (let [cur parent
              parent (mparent parent)]
          (recur (add-child-node parent
                                 (make-mnode (mname cur)
                                             (:ret m)
                                             (mparent cur)
                                             (mchildren cur)))
                 ms))))

;; m1
;;   m2
;;     m3
;;     m4
;;   m5

(def mlist (list (enter 'm1)
                 (enter 'm2)
                 (enter 'm3)
                 (exit 'm3 'm3-val)
                 (enter 'm4)
                 (exit 'm4 'm4-val)
                 (exit 'm2 'm2-val)
                 (enter 'm5)
                 (exit 'm5 'm5-val)
                 (exit 'm1 'm1-val)))

(def mtree (make-mtree (make-mnode 'root nil nil nil)
                       mlist))

(defn print-mtree [t]
  (let [pr-tree (fn prt [node indent]
                  (println indent (mname node) "=>" (mret node))
                  (doseq [c (mchildren node)]
                    (prt c (str indent "  "))))]
    (pr-tree t "")))

(print-mtree mtree)
