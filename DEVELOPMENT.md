# Developing ECM in the Broad Institute Environment

This document describes the nuts and bolts of developing on ECM in the Broad
environment. Processes here rely on access to the Broad Vault server to get secrets and to
the Broad Artifactory server to read and write libraries. There are dependencies on Broad
Dev Ops github repositories and practices. Some of those are locked down, because the
Broad deployment of Terra needs to maintain a FedRamp approval level in order to host US
Government data.


## Setup

### Prerequisites:

- Install Postgres 13: https://www.postgresql.org/download/
  - [The app](https://postgresapp.com/downloads.html) may be easier, just make sure to download the right version. It'll manage things for you and has a useful menulet where the server can be turned on and off. Don't forget to create a server if you go this route.
- Install Adoptium Java 17 (Temurin). Here's an easy way on Mac, using [jEnv](https://www.jenv.be/) to manage the active version:

    ```sh
    brew install jenv
    # follow postinstall instructions to activate jenv...

    # to add previously installed versions of Java to jEnv, list them:
    # /usr/libexec/java_home -V
    # and then add them:
    # jenv add /Library/Java/JavaVirtualMachines/<JAVA VERSION HERE>/Contents/Home

    brew install homebrew/cask-versions/temurin17

    jenv add /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
    ```

**NOTE**: You may encounter issues with the application when running an unexpected version of Java. So make sure you are running `Temurin-17` as specified above.


### Database Configuration
ECM relies on a Postgresql database server. There are two options for running the Postgres server:

- Manual setup:
  Setup Postgres using whatever method you like.
- Convenient app setup:
  Install [the convenient app](https://postgresapp.com/), and create a database called `ecm`.

#### Initialize your database:
```sh
psql -h 127.0.0.1 -U postgres -f ./common/postgres-init.sql
```
***N.B.*** If you used **the convenient app**, you should run `psql` as `/Applications/Postgres.app/Contents/Versions/latest/bin/psql`

### IntelliJ Setup

1. Open the repo normally (File > New > Project From Existing Sources). Select the folder, and then select Gradle as the external model.
2. In project structure (the folder icon with a little tetromino over it in the upper
   right corner), make sure the project SDK is set to Java 17. If not, IntelliJ should
   detect it on your system in the dropdown, otherwise click "Add JDK..." and navigate to
   the folder from the last step.
3. Set up [google-java-format](https://github.com/google/google-java-format). We use the
   spotless checker to force code to a standard format. Installing the IntelliJ plug-in
   and library makes it easier to get it in the right format from the start.
4. See some optional tips below in the ["Tips"](#tips) section.

## Running

### Human-Readable Logging
To enable human-readable logging, add `ECM_LOG_APPENDER=Console-Standard` to your env.

### Running Tests

Unit tests will run on build.  Integration tests can be run by following the instructions in the [integration README](/integration/README.md).

### Running ECM Locally

Run in IntelliJ (recommended) or use the command line:

```sh
./render_config.sh # only when building ECM for the first time
source ${PWD}/service/src/main/resources/rendered/secrets.env
cd service
../gradlew bootRun
```

Then navigate to the Swagger: `http://localhost:8080/`


## Tips
- Check out [gdub](https://github.com/gdubw/gdub), it'll save you typing `./gradlew` over
  and over, and also takes care of knowing when you're not in the root directory so you
  don't have to figure out the appropriate number of `../`s.
- In IntelliJ, instead of running the local server with `bootRun`, use the `ExternalCredsApplication` Spring
  Boot configuration that IntelliJ auto-generates. To edit it, click on it (in the upper
  right of the window), and click `Edit Configurations`.
    - For readable logs, put `human-readable-logging` in the `Active Profiles` field.
