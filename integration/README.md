# `integration` Project

This Gradle project contains Test Runner integration tests for the ECM service.


### Running Integration Tests

The test runner task `runTest` can be used to launch tests in two different modes
* Run individual test
* Run a test suite

To run the tests locally:

1. Run the ExternalCredsApplication (in IntelliJ). 
2. Override the server spec to use the local server by running: `export TEST_RUNNER_SERVER_SPECIFICATION_FILE="workspace-local.json"`
3. Then, run the integration test or test suite using the `runTest` command:
    - To run a test suite (ex. Full Integration suite): 
   `./gradlew runTest --args="suites/FullIntegration.json /tmp/TR`
    - To run a single test (ex. Service Status test)
   `./gradlew runTest --args="configs/integration/ServiceStatus.json /tmp/TR"`
    - To debug, add `--stacktrace`.
