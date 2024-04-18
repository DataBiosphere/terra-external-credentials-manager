# terra-external-credentials-manager
ECM jakarta-based Client Library for compatibility with Java clients running Spring Boot 3.

## Publish a new version
To publish a new version of this client library:

1. Optionally, update `subprojects.version` in [the top-level build.gradle](../build.gradle)
2. `cd client-resttemplate`
3. run `./publish.sh`

## Build a local JAR for development
This can be useful when developing contract tests (e.g. when adding new Provider States)

1. Navigate to the `client-resttemplate` directory (where this README is located)
2. Run `../gradlew assemble`
3. Find the generated JAR file in `./build/libs`
4. Replace the dependency in your other project with the newly generated JAR
