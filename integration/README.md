# Using Test Runner in ECM

ECM uses the [Terra Test Runner](https://github.com/DataBiosphere/terra-test-runner) to automate integration and performance tests. At the moment:
* Integration tests are run on PRs only (but may eventually be run nightly).
* Performance tests are run nightly in the perf environment and results are uploaded to the [QA Dashboard](https://trdash.dsp-eng-tools.broadinstitute.org/).

## Test Runner Overview

There are a few concepts relevant to test runner that it's helpful to be familiar with:
* **Test Scripts**: The code for an individual test, including setup and teardown steps ([example](src/main/java/scripts/testscripts/GetVersion.java), [more info](https://github.com/DataBiosphere/terra-test-runner#Test-Script))
* **Test Configs**: Describes the environment, number of threads, etc. to use for a test ([example](src/main/resources/configs/perf/GetStatus.json), [more info](https://github.com/DataBiosphere/terra-test-runner#test-configuration))
* **Suites**: A set of test configs that should be run together (ex. a perf or integration test suite) ([example](src/main/resources/suites/FullPerf.json), [more info](https://github.com/DataBiosphere/terra-test-runner#test-suite))
* **Servers**: The environment in which the test suite should be run (ex. local, or perf deployment) ([more info](https://github.com/DataBiosphere/terra-test-runner#add-a-new-server-specification))

Results from nightly runs are uploaded to the [testrunner dashboard](https://trdash.dsp-eng-tools.broadinstitute.org/#) (which requires the non-split VPN to view).

## Adding New Tests

1. Create a new script in the [test scripts directory](src/main/java/scripts/testscripts).
2. Make a config file for the test.
2. Add a reference to the test script in our test suite [here](src/main/resources/suites/FullIntegration.json) or [here](src/main/resources/suites/FullPerf.json).
3. Debug the test locally using the instructions below.


## Running Tests


To run the tests locally:

1. Follow the initial setup instructions described in the [DEVELOPMENT.md](../DEVELOPMENT.md).
2. Run the `ExternalCredsApplication` (in IntelliJ).
3. Then, run the integration tests in IntelliJ using the "Run local Integration" run configuration, or on the command line using the `runTest` command:
   - To run a test suite (ex. Full Integration suite):
     `./gradlew runTest --args="suites/FullIntegration.json /tmp/test-results"`
   - To run a single test (ex. Service Status test)
     `./gradlew runTest --args="configs/integration/GetStatus.json /tmp/test-results"`
   - To debug, add `--stacktrace`.

To run performance tests against the [perf environment](https://externalcreds.dsde-perf.broadinstitute.org/):

1. Connect to the Non-split VPN.
2. Run the following:
```
./render_config.sh perf
git clone https://github.com/broadinstitute/terra-helmfile integration/terra-helmfile/
./gradlew :integration:runTest --args="suites/FullPerf.json /tmp/test-results"
```

## Other Notes

* To reset the data in the perf database, download the [psql-connect script](https://github.com/broadinstitute/dsp-scripts/blob/master/firecloud/psql-connect.sh) from the DSP Scripts repo. Use it to run the `regenerate_perf_data.sql` script on the perf database:  
  ```
  ./psql-connect.sh perf externalcreds some/relative/path/to/resources/datageneration/regenerate_perf_data.sql
  ```

* Dependency locking is turned on, to update the lockfiles run `./gradlew dependencies --write-locks`
