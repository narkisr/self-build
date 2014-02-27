(ns self-build.core
  (:gen-class true)
  (:require 
    [clojure.java.io :as io :refer (as-file)]
    [taoensso.timbre :as timbre]
    [ruiyun.tools.timer :refer (run-task!)]
    [clojure.core.strint :refer (<<)]
    [clojure.string :refer (join split)]
    [me.raynes.conch :as c]
    [clj-jgit.porcelain :as g :refer (with-identity git-clone-full)])
  (:import 
    clojure.lang.ExceptionInfo
    ))

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
   [{:keys [steps target] :as job} ]
   (doseq [{:keys [cmd args]} steps] 
     (sh- cmd (conj args {:dir target}))))

(defn initialize 
   "init build" 
   [{:keys [repo target] :as job}]
   (when-not (.exists (as-file target))
     (with-identity {:ssh-prvkey "/home/ronen/.ssh/id_rsa"}
       (git-clone-full repo target)
       (build job))))

(defn periodic-check [{:keys [target] :as job}]
  (fn []
    (trace "checking build status")
    (with-identity {:ssh-prvkey "/home/ronen/.ssh/id_rsa"}
      (let [repo (g/load-repo target) 
            {:keys [trackingRefUpdates advertisedRefs]} (bean (g/git-fetch repo))]
        (when (> (.size trackingRefUpdates) 0)
          (doseq [c advertisedRefs] (g/git-merge repo c))
          (info "Change detected running the build:")
          (build job))
        ))))

(defn run-jobs 
  "run all build jobs" 
  [jobs]
  (doseq [{:keys [poll] :as job} jobs] 
    (initialize job)
    (run-task! (periodic-check job) :period poll)))

(defn -main [& args]
  (run-jobs [
    {:name "play"
     :repo "git@github.com:narkisr/play.git" 
     :target "/tmp/play" 
     :steps [{:cmd "lein" :args ["help"]}]
     :poll 3000
    }]))

