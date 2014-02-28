(defproject self-build "0.0.1"

  :description 
  "self-build is a simple build server currently focused on simplicity and easy setup,
   its main goal is to enable a continues build by just running lein self-build jobs.edn"

  :url "https://github.com/narkisr/self-build"

  :license  {
    :name "Apache License, Version 2.0" :url "http://www.apache.org/licenses/LICENSE-2.0.html"
  }

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-jgit "0.6.3"] 
                 [com.taoensso/timbre "3.1.1"]
                 [org.clojure/core.incubator "0.1.2"]
                 [ruiyun/tools.timer "1.0.1"]
                 [me.raynes/conch "0.5.0"]
                 [com.draines/postal "1.11.1"]
                 [org.clojure/tools.reader "0.8.3"]]

  :plugins [[lein-tag "0.1.0"] [lein-set-version "0.3.0"]]
  
  :main self-build.core
  )
