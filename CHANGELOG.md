### 1.2.0-proxy.10

* [fix: optimize startup; attempt to download all cohorts from cache](https://github.com/amplitude/experiment-jvm-server/commit/fbf4c6a5a5e49c8ea3e0227405907c844fc0bf37)
  * Prior to downloading full, fresh cohorts from the network API attempt to download from the CDN cache. If all cohorts are downloaded from the CDN cache, trigger full cohort download asynchronously and return from the `start()` function
  * Only supported when not operating in proxy mode.
* [fix: use daemon threads; support concurrent cohort downloads](https://github.com/amplitude/experiment-jvm-server/commit/fd1041eb31d7e2e4592acffc119185e2ce7e9d93)
  * Use daemon threads for all executors.
  * Always download all cohorts concurrently.

### 1.2.0-proxy.9

* [fix: support local group cohorts from proxy](https://github.com/amplitude/experiment-jvm-server/commit/24100c444decefeb005cb27b52a2af85267d1db7)
  * Fixes bug where operating in proxy mode meant that all group cohorts were being returned empty.

### 1.2.0-proxy.8

* [fix: enrich user with cohort IDs even without userId or group cohorts](https://github.com/amplitude/experiment-jvm-server/commit/c6f256055eb876855967135ce4d3d68b86fb1b29)
  * Fixes bug where user was not enriched with cohorts if the user ID was null or if no group cohorts needed to be downloaded.  

### 1.2.0-proxy.7

* [fix: support falling back on cached cohort downloads](https://github.com/amplitude/experiment-jvm-server/commit/ef7ae818e1e0eb8c496030bb53518e159c407d42)
  * If a cohort fails to download fully, fall back on the CDN cache for a stale version of the cohort.

### 1.2.0-proxy.6

* [fix: cohort id parsing segments overriding previous segments](https://github.com/amplitude/experiment-jvm-server/commit/06eeaf5023c468b18b04469a713e63c69872d042)
  * Fixes bug with flags targeting different cohorts in multiple segments would not guarantee that all required cohorts are downloaded.
