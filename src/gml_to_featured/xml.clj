(ns gml-to-featured.xml
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as zf])
  (:import [java.io ByteArrayOutputStream]
           [javax.xml.namespace QName]
           [javax.xml.stream XMLInputFactory XMLOutputFactory XMLEventReader
            XMLStreamConstants XMLEventWriter]
           [javax.xml.stream.events XMLEvent EndElement]))

(def ^XMLInputFactory input-factory (XMLInputFactory/newFactory))

(def ^XMLOutputFactory output-factory
  (let [f (XMLOutputFactory/newInstance)
        _ (doto f (.setProperty XMLOutputFactory/IS_REPAIRING_NAMESPACES true))]
    f))

(declare xml-> as-xml tree->seq)

(defn xml
  [loc]
  (as-xml (tree->seq (zip/node loc))))

(defn tag [loc]
  ((fnil name "") (:tag (zip/node loc))))

(defn attr-with-name [loc attrname]
  "Get the attribute with attrname. Alternative implementation for attr"
  (some #(if (= (.getLocalPart (.getName %)) attrname) (.getValue %)) (:attrs (zip/node loc))))

(defn id-attr [loc]
  (attr-with-name loc "id"))

(defn attr
  "Returns the xml attribute named attrname, of the xml node at location loc."
  ([attrname]     (fn [loc] (attr loc attrname)))
  ([loc attrname] (when (zip/branch? loc) (-> loc zip/node :attrs attrname))))

(defn attr=
  "Returns a query predicate that matches a node when it has an
  attribute named attrname whose value is attrval."
  [attrname attrval] (fn [loc] (= attrval (attr loc attrname))))

(defn tag=
  "Returns a query predicate that matches a node when its is a tag
  named tagname."
  [tagname]
  (fn [loc]
    (filter #(and (zip/branch? %) (= tagname (:tag (zip/node %))))
            (if (zf/auto? loc)
              (zf/children-auto loc)
              (list (zf/auto true loc))))))

(defn text
  "Returns the textual contents of the given location, similar to
  xpaths's value-of"
  [loc]
  (.replaceAll
   ^String (apply str (xml-> loc zf/descendants zip/node #(:content %) #(:content %)))
   (str "[\\s" (char 160) "]+") " "))

(defn text=
  "Returns a query predicate that matches a node when its textual
  content equals s."
  [s] (fn [loc] (= (text loc) s)))

(defn seq-test
  "Returns a query predicate that matches a node when its xml content
  matches the query expresions given."
  ^{:private true}
  [preds] (fn [loc] (and (seq (apply xml-> loc preds)) (list loc))))

(defn xml->
  [loc & preds]
  (zf/mapcat-chain loc preds
                   #(cond (keyword? %) (tag= %)
                          (string?  %) (text= %)
                          (vector?  %) (seq-test %))))

(defn xml1->
  "Returns the first item from loc based on the query predicates
  given.  See xml->"
  [loc & preds] (let [result (apply xml-> loc preds)]
                  (if (= 1 (count result))
                    (first result)
                    result)))

(defn pull-seq
  [^XMLEventReader reader]
  (lazy-seq
   (loop []
     (let [event (.nextEvent reader)]
       (condp #(%1 %2) event
         #(.isStartElement %)
         (cons event (pull-seq reader))
         #(.isEndElement %)
         (cons event (pull-seq reader))
         #(.isCharacters %)
         (if (re-find #"^[\s]*$" (-> event .asCharacters .getData))
           (recur)
           (cons event (pull-seq reader)))
         #(.isEndDocument %)
         nil
         (recur))))))

(defn seq->tree
  "COPIED FROM clojure.data.xml
  Takes a seq of events that logically represents
  a tree by each event being one of: enter-sub-tree event,
  exit-sub-tree event, or node event.

  Returns a lazy sequence whose first element is a sequence of
  sub-trees and whose remaining elements are events that are not
  siblings or descendants of the initial event.

  The given exit? function must return true for any exit-sub-tree
  event.  parent must be a function of two arguments: the first is an
  event, the second a sequence of nodes or subtrees that are children
  of the event.  parent must return nil or false if the event is not
  an enter-sub-tree event.  Any other return value will become
  a sub-tree of the output tree and should normally contain in some
  way the children passed as the second arg.  The node function is
  called with a single event arg on every event that is neither parent
  nor exit, and its return value will become a node of the output tree.

  (seq->tree #(when (= %1 :<) (vector %2)) #{:>} str
            [1 2 :< 3 :< 4 :> :> 5 :> 6])
  ;=> ((\"1\" \"2\" [(\"3\" [(\"4\")])] \"5\") 6)"
 [parent exit? node coll]
  (lazy-seq
    (when-let [[event] (seq coll)]
      (let [more (rest coll)]
        (if (exit? event)
          (cons nil more)
          (let [tree (seq->tree parent exit? node more)]
            (if-let [p (parent event (lazy-seq (first tree)))]
              (let [subtree (seq->tree parent exit? node (lazy-seq (rest tree)))]
                (cons (cons p (lazy-seq (first subtree)))
                      (lazy-seq (rest subtree))))
              (cons (cons (node event) (lazy-seq (first tree)))
                    (lazy-seq (rest tree))))))))))

(defn event->tree
  [events]
  (ffirst
   (seq->tree
    (fn [^XMLEvent event contents]
      (when (.isStartElement event)
        (let [start (.asStartElement event)]
          {:tag     (keyword (.getLocalPart (.getName start)))
           :attrs   (doall (iterator-seq (.getAttributes start)))
           :content contents
           :raw     event})))
    (fn [^XMLEvent event]
      (.isEndElement event))
    (fn [^XMLEvent event]
      {:raw event
       :content (-> event .asCharacters .getData str)})
    events)))

(defn start->end-event [start]
  (reify EndElement
    (getEventType [_] XMLStreamConstants/END_ELEMENT)
    (getName [_]
      (-> start .asStartElement .getName))
    (getNamespaces [_]
      (.iterator []))))

(defn tree->seq [tree]
  (if (seq? tree)
    (mapcat tree->seq tree)
    (if-let [content (and (seq? (:content tree)) (:content tree))]
      (cons (:raw tree) (concat (tree->seq content)
                                [(start->end-event (:raw tree))]))
      (list (:raw tree)))))

(defn as-xml [events]
  (when (not= nil events)
    (let [out (ByteArrayOutputStream.)]
      (with-open [^XMLEventWriter writer (.createXMLEventWriter output-factory out)]
        (doseq [^XMLEvent event events]
          (.add writer event)))
      (String. (.toByteArray out)))))
