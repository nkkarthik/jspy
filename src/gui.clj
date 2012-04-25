(ns gui
  (use [clojure.string :as s :only ()]
       [print :only (class-name method-name)])
  (import [javax.swing JPanel JFrame JTree JScrollPane
           JTable JTextArea JLabel JTextField]
          [javax.swing.tree DefaultMutableTreeNode
           DefaultTreeCellRenderer TreeSelectionModel
           TreeNode]
          [javax.swing.event TreeSelectionListener]
          [javax.swing.table DefaultTableModel]
          [java.awt BorderLayout Dimension GridLayout]
          [java.awt.event ActionListener]))

(def height 700)
(def width (* height 1.618))
(def truncate-size 25)

(defn trunc [s]
  (if (instance? String s)
    (if (<= (.length s) truncate-size)
      s
      (str (.substring s 0 truncate-size) "..."))
    s))

(defn- escape-html [s]
  (if (instance? String s)
    (.replaceAll s "<" "&lt;")
    s))

(defn format-value [name value type value-color trunc?]
  (format " %s: <font color=%s><b>%s</b><font> <i>(%s)</i>"
          name
          value-color
          (if trunc? (escape-html (trunc value)) value)
          type))

(defn format-return [return return-type trunc?]
  (if (= return "void")
    ""
    (format-value "<em>returned</em>"
                  return
                  (class-name return-type)
                  (if (= "Exception" return-type)
                    "red"
                    "green")
                  trunc?)))
                     
(defn format-arg [{:keys [name value type]} trunc?]
  (format-value name value (class-name type) "black" trunc?))

(defn format-args [args join-by trunc?]
  (if (empty? args)
    ""
    (s/join join-by
            (doall (map #(format-arg % trunc?) args)))))

(defn format-method-name [class method]
  (format "%s/<b><font color=blue>%s</font></b>"
          (class-name class)
          (method-name method)))

(defn format-for-node [{:keys [method args class return return-type]}]
  (str (format "%s(%s) %s"
               (format-method-name class method)
               (format-args args "," true)
               (format-return return return-type true))))

(defn- make-node [node-obj parent]
  (let [n (DefaultMutableTreeNode. node-obj)]
    (when parent
      (.add parent n))
    n))

(defn- add-later [parent child]
  (fn []
    [child (make-node child parent)]))

(defn- make-method-nodes [queue]
  (when (first queue)
    (let [[method node] ((first queue))]
      (recur (concat (rest queue)
                     (map #(add-later node %)
                          (:children method)))))))

(defn- make-thread-nodes [data-map]
  (let [root (make-node "Root Thread" nil)]
    (make-method-nodes
     (map (fn [[thread method-map]]
            (fn []
              [method-map (make-node (name thread) root)]))
          data-map))
    root))

(defn- wrap-html [text]
  (str "<html>" text "</html"))

(defn- make-tree [node]
  (let [tree
        (proxy [JTree] [node]
          (convertValueToText [val sel? exp? leaf? row focus?]
            (let [o (.getUserObject val)]
              (wrap-html
               (if (instance? String o)
                 o
                 (format-for-node o))))))]
    (doto tree
      (-> .getSelectionModel
          (.setSelectionMode
           TreeSelectionModel/SINGLE_TREE_SELECTION)))))

(defn- add-to-frame-and-show [tree in-out find]
  (doto (JFrame.)
    (#(doto (.getContentPane %)
        (.add find BorderLayout/PAGE_START)
        (.add (JScrollPane. tree) BorderLayout/CENTER)
        (.add in-out BorderLayout/PAGE_END)))
    (.setSize width height)
    (.setVisible true)))

(defn selected-model [tree]
  (let [sel (.getLastSelectedPathComponent tree)]
    (if (instance? TreeNode sel)
      sel
      (get sel 0))))

(defn- on-method-select [tree handler]
  (.addTreeSelectionListener
   tree
   (proxy [TreeSelectionListener] []
     (valueChanged [e]
       (let [sel (selected-model tree)]
         (when sel
           (handler (.getUserObject sel))))))))

(defn- get-method-input-model [method]
  (let [args (:args method)]
    (proxy [DefaultTableModel] []
      (getColumnClass [at] String)
      (getColumnCount [] 3)
      (getColumnName [at] (nth ["Type" "Var" "Value"] at))
      (getRowCount [] (count args))
      (getValueAt [r c]
        (let [col (nth [:type :name :value] c)
              val (col (nth args r))]
          (if (= col :type)
            (class-name val)
            val)))
      (setValueAt [v r c]))))

(defn- in-pane [tree]
  (let [table (JTable.)]
    (.setFillsViewportHeight table true)
    (on-method-select
     tree
     (fn [method]
       (.setModel table
                  (get-method-input-model method))))
    (JScrollPane. table)))

(defn- out-pane [tree]
  (let [out (JTextArea.)]
    (on-method-select
     tree
     (fn [method]
       (.setText out
                 (str (class-name (:return-type method))
                      ":\n"
                      (:return method)))))
    (JScrollPane. out)))

(defn- in-out-pane [in out]
  (doto (JPanel.)
    (.setLayout (GridLayout. 0 2))
    (.add in)
    (.add out)
    (.setPreferredSize
     (Dimension. width (int (/ height 3.618))))))

(defn- find-pane [find-fn]
  (let [field (JTextField. 25)
        changed #(find-fn (.getText field))]
    (-> field (.addActionListener
               (proxy [ActionListener] []
                 (actionPerformed [e]
                   (changed)))))
    (doto (JPanel.)
      (.add (JLabel. "Find:"))
      (.add field))))

(defn match-text [model text]
  (let [user-obj (.getUserObject model)]
    (.contains (if (instance? String user-obj)
                 user-obj
                 (str (class-name (:class user-obj))
                      "/"
                      (method-name (:method user-obj))))
               text)))

(defn nodes-list [node]
  (flatten
   (conj (map nodes-list
              (enumeration-seq (.children node)))
         node)))

(defn rotate-till-selected-node-on-top
  [nodes current-selection]
  (if (= (first nodes) current-selection)
    nodes
    (rotate-till-selected-node-on-top
     (concat (rest nodes) [(first nodes)])
     current-selection)))

(defn find-model-with-text [models text]
  (let [model (first models)]
    (cond (empty? models) nil
          (match-text model text) model
          :else (find-model-with-text (rest models) text))))
      
(defn select-tree-node [tree node]
  (when node
    (let [path (spy.Utils/nodeToPath node)]
      (.setSelectionPath tree path)
      (.scrollPathToVisible tree path))))

(defn find-in-tree [tree text]
  (let [selected (selected-model tree)
        nodes (nodes-list (.getRoot (.getModel tree)))]
    (select-tree-node
     tree
     (find-model-with-text
       (if selected
         (rest
          (rotate-till-selected-node-on-top nodes selected))
         nodes)
       text))))

(defn show-gui-tree [data-map]
  (let [tree (make-tree (make-thread-nodes data-map))]
    (add-to-frame-and-show
     tree
     (in-out-pane (in-pane tree)
                  (out-pane tree))
     (find-pane (partial find-in-tree tree)))
    tree))
