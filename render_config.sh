ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
ECM_VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds"
COMMON_VAULT_PATH="secret/dsde/terra/kernel/$ENV/common"

VAULT_COMMAND="vault read"

SERVICE_OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/integration/src/main/resources/rendered"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

$VAULT_COMMAND -field=providers "$ECM_VAULT_PATH/providers" >"$SERVICE_OUTPUT_LOCATION/providers.yaml"
$VAULT_COMMAND -field=swagger-client-id "$ECM_VAULT_PATH/swagger-client-id" >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"
$VAULT_COMMAND -field=data -format=json "secret/dsde/firecloud/$ENV/common/firecloud-account.json" >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"

# # get testrunner SA info
# if [ $ENV == perf ]; then
#   $VAULT_COMMAND -field=key "$COMMON_VAULT_PATH/testrunner/testrunner-sa" | base64 -d > "$INTEGRATION_OUTPUT_LOCATION/testrunner-sa.json"
#   $VAULT_COMMAND -field=ca.crt-b64 "$ECM_VAULT_PATH/testrunner-k8s-sa" >"$INTEGRATION_OUTPUT_LOCATION/testrunner-k8s-sa-key"
#   $VAULT_COMMAND -field=token  "$ECM_VAULT_PATH/testrunner-k8s-sa" >"$INTEGRATION_OUTPUT_LOCATION/testrunner-k8s-sa-token"
# fi

############################
# SIMPLIFIED BASH FROM WSM #
############################

# tmpfile=$(mktemp)
# OUTPUT=$($VAULT_COMMAND -format=json "secret/dsde/terra/kernel/dev/dev/testrunner-k8s-sa")
# echo "$OUTPUT" | jq -r .data.key | base64 -d > "${tmpfile}";
# jq -r ".data[\"ca.crt\"]" "${tmpfile}" > "$INTEGRATION_OUTPUT_LOCATION/testrunner-k8s-sa-key.txt"
# jq -r .data.token "${tmpfile}" | base64 --decode > "$INTEGRATION_OUTPUT_LOCATION/testrunner-k8s-sa-token.txt"