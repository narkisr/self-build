(defproject self-build "0.1.0-SNAPSHOT"
  :description "A simple build server for Clojure apps"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-jgit "0.6.3"] 
                 [org.clojure/core.incubator "0.1.2"]
                 [jarohen/chime "0.1.5"]
                 [me.raynes/conch "0.5.0"]
                 ]
  
  :main self-build.core
  ;; :aliases {""  [ "with-profile" "prod" "do" "compile," "trampoline" "run"]}
  )
