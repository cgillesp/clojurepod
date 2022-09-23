(ns clojurepod.util)

(defn longest [& ins]
  (loop [yet nil left ins]
    (if (seq left)
      (if  (< (count yet) (count (first left)))
        (recur (first left) (rest left))
        (recur yet (rest left)))
      yet
      )

    ))
