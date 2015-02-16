(defproject self-build "0.0.8"

  :description 
  "self-build is a simple build server currently focused on simplicity and easy setup,
   its main goal is to enable a continues build by just running lein self-build jobs.edn"

  :url "https://github.com/narkisr/self-build"

  :license  {
    :name "Apache License, Version 2.0" :url "http://www.apache.org/licenses/LICENSE-2.0.html"
  }

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-jgit "0.6.3"] 
                 [com.taoensso/timbre "3.1.1"]
                 [org.clojure/core.incubator "0.1.3"]
                 [substantiation "0.2.1"]
                 [me.raynes/fs "1.4.6"]
                 [ruiyun/tools.timer "1.0.1"]
                 [me.raynes/conch "0.8.0"]
                 [com.draines/postal "1.11.1"]
                 [formation "0.0.1"]
                 [org.clojure/tools.reader "0.8.13"]]

  :plugins [[lein-tag "0.1.0"] [lein-set-version "0.3.0"] 
            [lein-ancient "0.6.2" :exclusions [rewrite-clj]] [lein-cljfmt "0.1.9"]]

  :profiles {
    :dev { 
      :set-version {
        :updates [ 
          {:path "README.md" :search-regex #"\"\d+\.\d+\.\d+\""}
        ]
      }
    }
  }
 
  :aliases {"self-build" ["trampoline" "run" "fixtures/jobs.edn"] }

  :signing  {:gpg-key "narkisr@gmail.com"}
  
  :main self-build.core
  )
