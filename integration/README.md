# `integration` Project

This Gradle project contains Test Runner integration tests for the External Credentials Manager.


### Adding New Tests

1. Create a new script in the [test scripts directory](src/main/java/scripts/testscripts).
2. Add a reference to the test script in our FullIntegration test suite [here](src/main/resources/suites/FullIntegration.json).
3. Debug the test locally using the instructions below.


### Running Tests

The test runner task `runTest` can be used to launch individual tests or entire test suites.

To run the tests locally:

1. Follow the initial setup instructions described in the [DEVELOPMENT.md](../DEVELOPMENT.md).
2. Run the `ExternalCredsApplication` (in IntelliJ).
3. Then, run the integration tests in IntelliJ using the "Run local Integration" run configuration, or on the command line using the `runTest` command:
   - To run a test suite (ex. Full Integration suite):
     `./gradlew runTest --args="suites/FullIntegration.json /tmp/test-results"`
   - To run a single test (ex. Service Status test)
     `./gradlew runTest --args="configs/integration/GetStatus.json /tmp/test-results"`
   - To debug, add `--stacktrace`.
