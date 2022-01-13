ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds"

VAULT_COMMAND="vault read"

SERVICE_OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/integration/src/main/resources/rendered"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

$VAULT_COMMAND -field=providers "$VAULT_PATH/providers" >"$SERVICE_OUTPUT_LOCATION/providers.yaml"
$VAULT_COMMAND -field=swagger-client-id "$VAULT_PATH/swagger-client-id" >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"
$VAULT_COMMAND -field=data -format=json "secret/dsde/firecloud/$ENV/common/firecloud-account.json" >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"
