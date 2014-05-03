(ns self-build.core
  (:gen-class true)
  (:require 
    [subs.core :refer (validate! validation when-not-nil every-kv)]
    [formation.core :as form]
    [me.raynes.fs :as fs]
    [clojure.tools.reader.edn :as edn]
    [postal.core :as p :refer (send-message)]
    [clojure.java.io :as io :refer (file)]
    [taoensso.timbre :as timbre]
    [ruiyun.tools.timer :refer (run-task! deamon-timer)]
    [clojure.core.strint :refer (<<)]
    [clojure.string :refer (join split)]
    [me.raynes.conch :as c]
    [clj-jgit.porcelain :as g :refer (with-identity git-clone-full git-checkout git-branch-current)])
  (:import clojure.lang.ExceptionInfo))

(timbre/refer-timbre)

(defn log-res 
  "Logs a cmd result"
  [out]
  (when-not (empty? out) 
    (doseq [line (.split out "\n")] (info line))))

(defn- options [args]
  (let [log-proc (fn [out proc] (info out))
        defaults {:verbose true :timeout 60 :out log-proc :err log-proc}]
    (if (map? (last args))
      [(butlast args) (merge defaults (last args))] 
      [args defaults])
    ))

(defn sh- 
  "Runs a command localy and logs its output streams"
  [cmd args]
  (let [[args opts] (update-in (options args) [1 :timeout] (partial * 60 1000))]
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
    (doseq [{:keys [cmd args timeout] :or {timeout 60}} steps] 
      (sh- cmd (conj args {:dir target :timeout timeout})))
    (info "Finished building" name)
    (catch Throwable e
      (error e)
      (send-message smtp
                    (merge mail 
                           {:subject (<< "building ~{name} failed!") 
                            :body (<< "failed to build ~{name} due to ~(.getMessage e)")})))))

(defn initialize 
  "init build" 
  [{:keys [branch repo target] :as job} {:keys [ssh-key] :as ctx}]
  (when-not (.exists (file target))
    (with-identity {:ssh-prvkey ssh-key}
      (info "Cloned" repo)
      (git-clone-full repo target)))
  (let [repo (g/load-repo target)]
    (debug "Currently in branch" (git-branch-current repo))
    (when  (and branch (not= branch (git-branch-current repo)))
      (info "Checkout" branch)
      (git-checkout repo branch true true (<< "origin/~{branch}"))))
  (build job ctx))

(defn re-initialize 
  "re-checkout code descarding existing source (mainly useful on merge conflicts)." 
  [{:keys [target name] :as job} ctx]
  (fs/delete-dir target)
  (initialize job ctx))

(defn periodic-check 
  "periodical check of build status"
  [{:keys [target name clear-merge-fail] :as job} {:keys [ssh-key] :as ctx}]
  (fn []
    (info "Checking build status")
    (with-identity {:ssh-prvkey ssh-key}
      (try
        (let [repo (g/load-repo target) 
              {:keys [trackingRefUpdates advertisedRefs]} (bean (g/git-fetch repo))]
          (when (> (.size trackingRefUpdates) 0)
            (doseq [c advertisedRefs] 
              (when-not (.isSuccessful (.getMergeStatus (g/git-merge repo c)))
                (throw (ExceptionInfo. (<< "Failed to merge code on ~{name}") {:merged-failed true}))))
            (info "Change detected running the build:")
            (build job ctx)))
        (catch ExceptionInfo e 
          (when (and clear-merge-fail (:merged-failed (.getData e)))
            (warn "Merge failed, clearing source and starting from scratch")
            (re-initialize job ctx)))
        (catch Throwable e (error e))))))

(defn run-jobs 
  "run all build jobs" 
  [jobs ctx]
  (doseq [{:keys [poll name] :as job} jobs] 
    (info "Setting up job" name)
    (initialize job ctx)
    (run-task! (periodic-check job ctx) :period poll )))

(def ctx-v
  {:ctx {
     :ssh-key #{:required :String} 
     :smtp {
       :host #{:required :String} 
       :user #{:required :String} 
       :pass #{:required :String} 
       :ssl #{:required :Keyword}
     }
     :mail {
       :from #{:required :String} :to #{:required :String} 
     }
   }})

(defn ctx-validation 
   "Validating ctx" 
   [ctx]
   (validate! ctx ctx-v))

(defn locknload
  "load jobs and run them" 
  [f]
  (let [{:keys [jobs]} (edn/read-string (slurp f)) 
        {:keys [ctx]} (form/config "self-build" ctx-validation)]
    (run-jobs jobs ctx)))

(defn -main [f & args]
  (locknload f))

