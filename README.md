# TODO

* Write README.md (used on jenkins build page and github project main page)

* Benchmark Manager
    * Identify contexts running inside a benchmark
    * Storage information (in which db should results be stored, etc.)
    * "Benchmark ID", BenchmarkEntry in a separate HC map

        class BenchmarkEntry {
            String benchmarkTaskId;
            Map<String, String> storage;
            List<String> containedContextIds;
        }

	* api "runBenchmark" in BEEN-API

	* Implement "fail-safe storage" (stored in benchmark entry in HC) to allow
      restart of Benchmark after node failure

* Tasks & Contexts
	* for "submitTask" and "submitBenchmark" check type of task descriptor and throw
	  exception when wrong

* Results & Storage
	* Retrieving results from a user task
	* Allow a special user task (evaluator) to return a "file" (ZIP, PNG, CSV, ...)
	* Allow persistence & retrieval of "big files" generated by user tasks
* Dependencies
	* "Run a task after another has finished"
* Software Repository
	* Implement "SNAPSHOT" versioning (don't cache)
	* Implement "empty cache" feature
* Web Interface
	* When Hazelcast gets disconnect, show an error to the user and on next page
	  redirect to the login page again
	* \*.\*

* Win32 support
* Native task API (task, benchmark)
