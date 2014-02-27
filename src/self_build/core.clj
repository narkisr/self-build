(ns self-build.core
  (:gen-class true)
  (:require 
    [postal.core :as p :refer (send-message)]
    [clojure.java.io :as io :refer (file)]
    [taoensso.timbre :as timbre]
    [ruiyun.tools.timer :refer (run-task!)]
    [clojure.core.strint :refer (<<)]
    [clojure.string :refer (join split)]
    [me.raynes.conch :as c]
    [clj-jgit.porcelain :as g :refer (with-identity git-clone-full)])
  (:import clojure.lang.ExceptionInfo))

(timbre/refer-timbre)

(defn log-res 
  "Logs a cmd result"
  [out]
  (when-not (empty? out) 
    (doseq [line (.split out "\n")] (info line))))

(defn- options [args]
  (let [log-proc (fn [out proc] (info out))
        defaults {:verbose true :timeout (* 60 1000) :out log-proc :err log-proc}]
    (if (map? (last args))
      [(butlast args) (merge defaults (last args))] 
      [args defaults])))

(defn sh- 
  "Runs a command localy and logs its output streams"
  [cmd args]
  (let [[args opts] (options args) ]
    (info cmd (join " " args))
    (case (deref (:exit-code (c/run-command cmd args opts)))
      :timeout (throw (ExceptionInfo. (<< "timed out while executing: ~{cmd}") opts))
      0 nil
      (throw (ExceptionInfo. (<< "Failed to execute: ~{cmd}") opts)))))

(defn build 
   "runs build steps" 
   [{:keys [steps target name] :as job} {:keys [smtp mail] :as ctx}]
   (info "starting to build" name)
   (try 
     (doseq [{:keys [cmd args]} steps] 
       (sh- cmd (conj args {:dir target})))
     (catch Throwable e
       (error e)
       (send-message smtp
         (merge mail 
           {:subject (<< "building ~{name} failed!") 
            :body (<< "failed to build ~{name} due to ~(.getMessage e)")})) 
       )
     )
     (info "finished building" name))

(defn initialize 
   "init build" 
   [{:keys [repo target] :as job} {:keys [ssh-key] :as ctx}]
   (when-not (.exists (file target))
     (with-identity {:ssh-prvkey ssh-key}
       (info "Cloned " repo)
       (git-clone-full repo target)
       (build job ctx))))

(defn periodic-check 
  "periodical check of build status"
  [{:keys [target] :as job} {:keys [ssh-key] :as ctx}]
  (fn []
    (trace "checking build status")
    (with-identity {:ssh-prvkey ssh-key}
      (let [repo (g/load-repo target) 
            {:keys [trackingRefUpdates advertisedRefs]} (bean (g/git-fetch repo))]
        (when (> (.size trackingRefUpdates) 0)
          (doseq [c advertisedRefs] (g/git-merge repo c))
          (info "Change detected running the build:")
          (build job))
        ))))

(defn run-jobs 
  "run all build jobs" 
  [jobs ctx]
  (doseq [{:keys [poll] :as job} jobs] 
    (initialize job ctx)
    (run-task! (periodic-check job ctx) :period poll)))

(defn -main [& args]
  (run-jobs 
    [{:name "play"
      :repo "git@github.com:narkisr/play.git" 
      :target "/tmp/play" 
      :steps [{:cmd "foo" :args ["help"]}]
      :poll 3000
     }
     {:name "celestial"
      :repo "git@github.com:celestial-ops/celestial-core.git" 
      :target "/tmp/celestial" 
      :steps [{:cmd "lein" :args ["runtest"]}]
      :poll 3000
     }]
     {:ssh-key "/home/ronen/.ssh/id_rsa"
      :smtp 
        {:host "smtp.gmail.com"
         :user "gookup"
         :pass ""
         :ssl :yes!!!11} 
      :mail {:from "gookup@gmail.com" :to "narkisr@gmail.com"}
     }))

