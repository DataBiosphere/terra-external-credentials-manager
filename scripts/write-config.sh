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
#   testrunner-k8s-sa-token.txt| Credentials for TestRunner to manipulate the Kubernetes cluster under
#   testrunner-k8s-sa-key.txt  | test. Not all environments have this SA configured. If the k8env is
#                              | integration and there is no configured SA, then the wsmtest one will be
#                              | retrieved. It won't work for all test runner tests. TODO: what does this mean??
#   ---------------------------+-------------------------------------------------------------------------
#   user-delegated-sa.json     | Firecloud SA used to masquerade as test users
#   ---------------------------+-------------------------------------------------------------------------
#   externalcreds-sa.json      | SA that the externalcreds application runs as
#   ---------------------------+-------------------------------------------------------------------------

function usage {
  cat <<EOF
Usage: $0 [<target>] [<vaulttoken>] [<outputdir>]"

  <target> can be:
    local - for testing against a local (bootRun) WSM
    dev - uses secrets from the dev environment
    help or ? - print this help
    clean - removes all files from the output directory
  If <target> is not specified, then use the envvar WSM_WRITE_CONFIG
  If WSM_WRITE_CONFIG is not specified, then use local

  <vaulttoken> defaults to the token found in ~/.vault-token.

  <outputdir> defaults to "../config/" relative to the script. When run from the gradle rootdir, it will be
  in the expected place for automation.
EOF
 exit 1
}

# Get the inputs with defaulting
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null  && pwd )"
default_outputdir="${script_dir}/config"
default_target=${WSM_WRITE_CONFIG:-local}
target=${1:-$default_target}
vaulttoken=${2:-$(cat "$HOME"/.vault-token)}
outputdir=${3:-$default_outputdir}

# The vault paths are irregular, so we map the target into three variables:
# k8senv    - the kubernetes environment: dev or integration
# namespace - the namespace in the k8s env: dev or wsmtest
# fcenv     - the firecloud delegated service account environment: dev

case $target in
    help | ?)
        usage
        ;;

    clean)
        rm "${outputdir}"/* &> /dev/null
        exit 0
        ;;

    local)
        k8senv=integration
        namespace=wsmtest # TODO: add ecmtest to vault??
        fcenv=dev
        ;;

    dev)
        k8senv=dev
        namespace=dev
        fcenv=dev
        ;;
esac

# Create the output directory if it doesn't already exist
mkdir -p "${outputdir}"

# Read a vault path into an output file, decoding from base64
# To detect missing tokens, we need to capture the docker result before
# doing the rest of the pipeline.
function vaultget {
    vaultpath=$1
    filename=$2
    decodeFromBase64=$3
    fntmpfile=$(mktemp)
    docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
           vault read -format=json "${vaultpath}" > "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi

    # read the data, decode if necessary, and write to file
    if [[ -n "$decodeFromBase64" ]]; then jq -r .data.key "${fntmpfile}" | base64 -d > "${filename}";
    else jq -r .data "${fntmpfile}" > "${filename}";
    fi

}

# Read database data from a vault path into a set of files
function vaultgetdb {
    vaultpath=$1
    fileprefix=$2
    fntmpfile=$(mktemp)
    docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
        vault read -format=json "${vaultpath}" > "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    datafile=$(mktemp)
    jq -r .data "${fntmpfile}" > "${datafile}"
    jq -r '.db' "${datafile}" > "${outputdir}/${fileprefix}-name.txt"
    jq -r '.password' "${datafile}" > "${outputdir}/${fileprefix}-password.txt"
    jq -r '.username' "${datafile}" > "${outputdir}/${fileprefix}-username.txt"
}

vaultget "secret/dsde/firecloud/${fcenv}/common/firecloud-account.json" "${outputdir}/user-delegated-sa.json"
vaultget "secret/dsde/terra/kernel/${k8senv}/${namespace}/workspace/app-sa" "${outputdir}/externalcreds-sa.json" decode

# Test Runner SA
vaultget "secret/dsde/terra/kernel/${fcenv}/common/testrunner/testrunner-sa" "${outputdir}/testrunner-sa.json" decode

# Test Runner Kubernetes SA
#
# The testrunner K8s secret has a complex structure. At secret/.../testrunner-k8s-sa we have the usual base64 encoded object
# under data.key. When that is pulled out and decoded we get a structure with:
# { "data":  { "ca.crt": <base64-cert>, "token": <base64-token> } }
# The cert is left base64 encoded, because that is how it is used in the K8s API. The token is decoded.
tmpfile=$(mktemp)
vaultget "secret/dsde/terra/kernel/${k8senv}/${namespace}/testrunner-k8s-sa" "${tmpfile}" decode
result=$?
if [ $result -ne 0 ]; then
    echo "No test runner credentials for target ${target}."
else
    jq -r ".data[\"ca.crt\"]" "${tmpfile}" > "${outputdir}/testrunner-k8s-sa-key.txt"
    jq -r .data.token "${tmpfile}" | base64 --decode > "${outputdir}/testrunner-k8s-sa-token.txt"
fi

