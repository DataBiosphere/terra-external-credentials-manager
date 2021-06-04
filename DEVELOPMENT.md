# Developing ECM in the Broad Institute Environment

This document describes the nuts and bolts of developing on ECM in the Broad
environment. Processes here rely on access to the Broad Vault server to get secrets and to
the Broad Artifactory server to read and write libraries. There are dependencies on Broad
Dev Ops github repositories and practices. Some of those are locked down, because the
Broad deployment of Terra needs to maintain a FedRamp approval level in order to host US
Government data.


## Setup

### Prerequisites:

- Install Postgres 13.1: https://www.postgresql.org/download/
  - [The app](https://postgresapp.com/downloads.html) may be easier, just make sure to download the right version. It'll manage things for you and has a useful menulet where the server can be turned on and off. Don't forget to create a server if you go this route.
- Install AdoptOpenJDK Java 11 (Hotspot). Here's an easy way on Mac, using [jEnv](https://www.jenv.be/) to manage the active version:

    ```sh
    brew install jenv
    # follow postinstall instructions to activate jenv...
    
    # to add previously installed versions of Java to jEnv, list them:
    # /usr/libexec/java_home -V
    # and then add them:
    # jenv add /Library/Java/JavaVirtualMachines/<JAVA VERSION HERE>/Contents/Home

    # follow instructions from https://github.com/AdoptOpenJDK/homebrew-openjdk to install adoptopenjdk11:
    brew tap AdoptOpenJDK/openjdk
    brew cask install adoptopenjdk11

    jenv add /Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home
    ```

**NOTE**: You may encounter issues with the application when running an unexpected version of Java. So make sure you are running `AdoptOpenJDK Java 11 (Hotspot)` as specified above.


### Database Configuration
ECM relies on a Postgresql database server. There are two options for running the Postgres server:

#### Option A: Docker Postgres
##### Running the Postgres Container
To start a postgres container configured with the necessary databases:
```sh
./local-dev/run_postgres.sh start
```
To stop the container:
```sh
./local-dev/run_postgres.sh stop
```
Note that the contents of the database is not saved between container runs.

##### Connecting to the Postgres Container
Use `psql` to connect to databases within the started database container, e.g. for database `wm` users `wmuser` with password `wmpwd`:
```sh
PGPASSWORD=wmpwd psql postgresql://127.0.0.1:5432/wm -U wmuser
```

#### Option B: Local Postgres 
##### Database Configuration

To set up Workspace Manager's required database, run the following command, which will create the DB's and users for unit tests, Stairway, and the app itself:

```sh
psql -f local-dev/local-postgres-init.sql
```

At some point, we will connect this to a CloudSQL instance but for local dev purposes having the option to use a local DB instead makes sense.

### IntelliJ Setup

1. Open the repo normally (File > New > Project From Existing Sources). Select the folder, and then select Gradel as the external model. 
2. In project structure (the folder icon with a little tetromino over it in the upper
   right corner), make sure the project SDK is set to Java 11. If not, IntelliJ should
   detect it on your system in the dropdown, otherwise click "Add JDK..." and navigate to
   the folder from the last step.
3. Set up [google-java-format](https://github.com/google/google-java-format). We use the
   spotless checker to force code to a standard format. Installing the IntelliJ plug-in
   and library makes it easier to get it in the right format from the start.
4. See some optional tips below in the ["Tips"](#tips) section.

## Running

### Running Tests

Unit tests will run on build. 

### Running ECM Locally

Run in IntelliJ (recommended) or use the command line:

```sh
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