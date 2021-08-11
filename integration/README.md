# `integration` Project

This Gradle project contains Test Runner integration tests for the External Credentials Manager. 
    

### Adding New Tests

1. Create a new script in the [test scripts directory](src/main/java/scripts/testscripts). 
2. Reference the script from any test suites that you would like the test to be included in (this probably at least includes the [full default suite](src/main/resources/suites/FullIntegration.json) and the [full dev suite](src/main/resources/suites/dev/FullIntegration.json)).
3. Debug the test locally using the instructions below. 


### Running Integration Tests

The test runner task `runTest` can be used to launch individual tests or entire test suites. 

To run the tests locally:

1. Run the `ExternalCredsApplication` (in IntelliJ).
3. Then, run the integration test or test suite using the `runTest` command:
   - To run a test suite (ex. Full Integration suite):
     `./gradlew runTest --args="suites/dev/FullIntegration.json /tmp/TR`
   - To run a single test (ex. Service Status test)
     `./gradlew runTest --args="configs/integration/ServiceStatus.json /tmp/TR"`
   - To debug, add `--stacktrace`.
