(ns self-build.core
  (:gen-class true)
  (:require 
    [clojure.tools.reader.edn :as edn]
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
   (info "Starting to build" name)
   (try 
     (doseq [{:keys [cmd args]} steps] 
       (sh- cmd (conj args {:dir target})))
     (info "Finished building" name)
     (catch Throwable e
       (error e)
       (send-message smtp
         (merge mail 
           {:subject (<< "building ~{name} failed!") 
            :body (<< "failed to build ~{name} due to ~(.getMessage e)")})))))

(defn initialize 
   "init build" 
   [{:keys [repo target] :as job} {:keys [ssh-key] :as ctx}]
   (when-not (.exists (file target))
     (with-identity {:ssh-prvkey ssh-key}
       (info "Cloned" repo)
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
          (build job ctx))
        ))))

(defn run-jobs 
  "run all build jobs" 
  [jobs ctx]
  (doseq [{:keys [poll name] :as job} jobs] 
    (info "Setting up job" name)
    (initialize job ctx)
    (run-task! (periodic-check job ctx) :period poll)))

(defn -main [f & args]
  (let [{:keys [ctx jobs]} (edn/read-string (slurp f))]
    (run-jobs jobs ctx)))

