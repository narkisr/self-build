(ns leiningen.self-build
  (:require [self-build.core :refer (locknload)]))

(defn self-build [project & [conf]]
  (locknload conf)
  (while true (Thread/sleep (* 60 1000))))

