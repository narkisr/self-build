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
   [me.raynes.conch.low-level :as low]
   [me.raynes.conch.low-level :as sh]
   [clj-jgit.porcelain :as g :refer (with-identity git-clone-full git-checkout git-branch-current)])
  (:import 
    org.eclipse.jgit.api.errors.CheckoutConflictException 
    clojure.lang.ExceptionInfo))

(timbre/refer-timbre)

; a sink into which we collect error logs and grabbing them during mail send after an exception thrown
(def errors (atom {}))
(def statuses (atom {}))

(defn log-proc 
  "logging out stdout/stderr and collecting both of them to errors atom"
  [id out proc] 
  (swap! errors (fn [m] (assoc m id (str (m id) out "\n"))))
  (info out))

(defn- options [id args]
  (let [defaults {:verbose true :timeout 60 :out (partial log-proc id) :err (partial log-proc id)}]
    (if (map? (last args))
      [(butlast args) (merge defaults (last args))] 
      [args defaults])
    ))

(defn sh- 
  "Runs a command localy and logs its output streams"
  [id cmd args]
  (info cmd (join " " args))
  (let [[args opts] (update-in (options id args) [1 :timeout] (partial * 60 1000))]
    (try
      (case (deref (:exit-code (c/run-command cmd args opts)))
        :timeout (throw (ExceptionInfo. (<< "timed out while executing: ~{cmd}") opts))
        0 nil)
      (catch Throwable e
        (throw (ExceptionInfo. (.getMessage e) {:id id}))) 
      )))


(defn handle-success 
  [name smtp mail]
  (let [{:keys [status date]} (@statuses name)]
    (when (= status :fail)
      (send-message smtp
         (merge mail 
            {:subject (<< "Building ~{name} restored to succeful!") 
             :body (<< "The build which failed on ~{date} is now working")}))))
  (swap! statuses assoc name {:status :success :date (java.util.Date.)}))

(defn handle-error 
  [name smtp mail e]
  (swap! statuses assoc name {:status :fail :date (java.util.Date.)})
  (send-message smtp
         (merge mail 
            {:subject (<< "Building ~{name} failed!") 
             :body (<< "failed to build ~{name} due to:\n ~(.getMessage e) ~(@errors name)")}))
  (swap! errors assoc name ""))

(defn build 
  "runs build steps" 
  [{:keys [steps target name] :as job} {:keys [smtp mail] :as ctx}]
  (info "Starting to build" name)
  (try 
    (doseq [{:keys [cmd args timeout] :or {timeout 60}} steps] 
      (sh- name cmd (conj args {:dir target :timeout timeout})))
    (info "Finished building" name)
    (handle-success name smtp mail)
    (catch ExceptionInfo e (handle-error name smtp mail e))))

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

(defn safe-merge [repo c]
   (try 
     (when-not (.isSuccessful (.getMergeStatus (g/git-merge repo c)))
       (throw (ExceptionInfo. (<< "Failed to merge code on ~{name}") {:merged-failed true})))
      (catch CheckoutConflictException e
        (throw (ExceptionInfo. (<< "Failed to checkout code on ~(.getConflictingPaths e)") {:merged-failed true})))))

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
            (doseq [c advertisedRefs] (safe-merge repo c))
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
    (run-task! (periodic-check job ctx) :period poll)))

(def ctx-v
  {:ctx {:ssh-key #{:required :String} 
         :smtp {:host #{:required :String} :user #{:required :String} 
                :pass #{:required :String} :ssl #{:required :Keyword} }
         :mail {:from #{:required :String} :to #{:required :String}}}})

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

