# Intro

self-build is a simple build server currently focused on simplicity and easy setup, its main goal is to enable a continues build by just running:

```bash
$ lein self-build jobs.edn
```

Right from within a lein project thus saving us from the need to setup Jenkins or other more complex solutions.

self-build will support only Git as SCM.

# Usage

Add to your project file:

```clojure
:plugins [[self-build "0.0.4"]]
```

Define jobs edn file:

```clojure 
{
 :jobs [
    {:name "play"
     :repo "git@github.com:narkisr/play.git" 
     :target "/tmp/play" 
     :steps [{:cmd "foo" :args ["help"]}]
     :poll 3000
    }

    {:name "celestial"
     :clear-merge-fail true
     :repo "git@github.com:celestial-ops/celestial-core.git" 
     :target "/tmp/celestial" 
     :steps [{:cmd "lein" :args ["runtest"] :timeout 180}]
     :poll 3000
    }
  ]
 
  :ctx {
    :ssh-key "/home/foo/.ssh/id_rsa"
    :smtp {
      :host "smtp.gmail.com"
      :user "foo"
      :pass ""
      :ssl :yes!!!11
     } 
    :mail {
      :from "foo@gmail.com" :to "youremail@gmail.com"
    }
  }
}
```

Now run builds (possibly in a tmux session):

```clojure
$ lein self-build jobs.edn
2014-Feb-28 21:06:18 +0200 nucleus INFO [self-build.core] - Setting up job play
2014-Feb-28 21:06:18 +0200 nucleus INFO [self-build.core] - Cloned git@github.com:narkisr/play.git
2014-Feb-28 21:06:25 +0200 nucleus INFO [self-build.core] - Starting to build play
```
# Copyright and license

Copyright [2013] [Ronen Narkis]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

