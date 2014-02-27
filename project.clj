(defproject self-build "0.1.0-SNAPSHOT"
  :description "A simple build server for Clojure apps"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-jgit "0.6.3"] 
                 [com.taoensso/timbre "3.1.1"]
                 [org.clojure/core.incubator "0.1.2"]
                 [ruiyun/tools.timer "1.0.1"]
                 [me.raynes/conch "0.5.0"]
                 [com.draines/postal "1.11.1"]
                 ]
  
  :main self-build.core
  )
