#!/bin/bash

# write-config.sh extracts configuration information from vault and writes it to a set of files
# in a directory. This simplifies access to the secrets from other scripts and applications.
#
# We want to avoid writing into the source tree. Instead, this will write into a subdirectory of the
# root directory.
#
# We want to use this in a gradle task, so it takes arguments both as command line
# options and as envvars. For automations, like GHA, the command line can be specified.
# For developer use, we can set our favorite envvars and let gradle ensure the directory is properly populated.
#
# The environment passed in is used to configure several other parameters, including the target for running
# the integration tests.
#
# The output directory includes the following files:
#   ---------------------------+-------------------------------------------------------------------------
#   testrunner-sa.json         | SA for running TestRunner - this is always taken from integration/common
#   ---------------------------+-------------------------------------------------------------------------
#   user-delegated-sa.json     | Firecloud SA used to masquerade as test users
#   ---------------------------+-------------------------------------------------------------------------


# Get the inputs with defaulting
ECM_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null  && pwd )"
TARGET=${1:-${WSM_WRITE_CONFIG:-local}}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/../integration/src/main/resources/rendered"

ENV=dev

# Read a vault path into an output file, decoding from base64
# To detect missing tokens, we need to capture the docker result before
# doing the rest of the pipeline.
function vaultget {
    vaultpath=$1
    filename=$2
    decodeFromBase64=$3

    OUTPUT=$(docker run --rm -e VAULT_TOKEN="${VAULT_TOKEN}" broadinstitute/dsde-toolbox:consul-0.20.0 \
           vault read -format=json "${vaultpath}")

    # result=$?
    # if [ $result -ne 0 ]; then return $result; fi

    # read the data, decode if necessary, and write to file
    if [[ -n "$decodeFromBase64" ]]; then
      echo "$OUTPUT" | jq -r .data.key | base64 -d > "${filename}";
    else
      echo "$OUTPUT" | jq -r .data > "${filename}";
    fi
}

vaultget "secret/dsde/firecloud/${ENV}/common/firecloud-account.json" "${INTEGRATION_OUTPUT_LOCATION}/user-delegated-sa.json"
vaultget "secret/dsde/terra/kernel/${ENV}/common/testrunner/testrunner-sa" "${INTEGRATION_OUTPUT_LOCATION}/testrunner-sa.json" decode
