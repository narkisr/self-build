(ns self-build.core
  (:gen-class true)
  (:require 
    [clj-time.core :as t]
    [clj-time.periodic :refer  [periodic-seq]]
    [chime :refer  [chime-at]]
    [clojure.core.strint :refer (<<)]
    [clojure.string :refer (join split)]
    [me.raynes.conch :as c]
    [clj-jgit.porcelain :as g :refer (with-identity git-clone-full)]
    )
  (:import 
    clojure.lang.ExceptionInfo
    ))


(defn log-res 
  "Logs a cmd result"
  [out]
  (when-not (empty? out) 
    (doseq [line (.split out "\n")] (println line))))

(defn- options [args]
  (let [log-proc (fn [out proc] (println out))
        defaults {:verbose true :timeout (* 60 1000) :out log-proc :err log-proc}]
    (if (map? (last args))
      [(butlast args) (merge defaults (last args))] 
      [args defaults])))


(defn sh- 
  "Runs a command localy and logs its output streams"
  [cmd args]
  (let [[args opts] (options args) ]
    (println cmd (join " " args))
    (case (deref (:exit-code (c/run-command cmd args opts)))
      :timeout (throw (ExceptionInfo. (<< "timed out while executing: ~{cmd}") opts))
      0 nil
      (throw (ExceptionInfo. (<< "Failed to execute: ~{cmd}") opts)))))

(defn build 
   "runs build steps" 
   [{:keys [steps target] :as job} ]
   (doseq [{:keys [cmd args]} steps] (sh- cmd (conj args {:dir target}))))

(defn initialize 
   "init build" 
   [{:keys [repo target] :as job}]
   (with-identity {:ssh-prvkey "/home/ronen/.ssh/id_rsa"}
     (git-clone-full repo target)
     (build job)))

(defn periodic-check [{:keys [path] :as job}]
  (fn []
    (println "checking build status")
    (with-identity {:ssh-prvkey "/home/ronen/.ssh/id_rsa"}
      (let [repo (g/load-repo path) 
            {:keys [trackingRefUpdates advertisedRefs]} (bean (g/git-fetch repo))]
        (when (> (.size trackingRefUpdates) 0)
          (doseq [c advertisedRefs] (g/git-merge repo c))
          (println "Change detected running the build:"))
        ))))

(defn run-jobs 
  "run all build jobs" 
  [jobs]
  (doseq [{:keys [poll] :as job} jobs] 
    ;; (initialize job)
    (chime-at (periodic-seq (t/now) (-> 1 t/minutes)) (periodic-check job))))

(chime-at (periodic-seq (t/now) (-> 1 t/minutes)) #(println %))

(defn -main [& args]
  (run-jobs [
    {:name "celestial"
     :repo "git@github.com:celestial-ops/celestial-core.git" 
     :target "/tmp/celestial" 
     :steps [{:cmd "lein" :args ["runtest"]}]
     :poll 5000
    }]))

