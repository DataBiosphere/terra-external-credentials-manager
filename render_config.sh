ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
ECM_VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds"
COMMON_VAULT_PATH="secret/dsde/terra/kernel/$ENV/common"

VAULT_COMMAND="vault read"

SERVICE_OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"
SECRET_ENV_VARS_LOCATION="${SERVICE_OUTPUT_LOCATION}/secrets.env"
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/integration/src/main/resources/rendered"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

{
  echo export RAS_CLIENT_ID="$($VAULT_COMMAND -field=ras_client_id "$ECM_VAULT_PATH/providers")"
  echo export RAS_CLIENT_SECRET="$($VAULT_COMMAND -field=ras_client_secret "$ECM_VAULT_PATH/providers")"
  echo export GITHUB_CLIENT_ID="$($VAULT_COMMAND -field=github_client_id "$ECM_VAULT_PATH/providers")"
  echo export GITHUB_CLIENT_SECRET="$($VAULT_COMMAND -field=github_client_secret "$ECM_VAULT_PATH/providers")"
} >> "${SECRET_ENV_VARS_LOCATION}"

$VAULT_COMMAND -field=swagger-client-id "$ECM_VAULT_PATH/swagger-client-id" >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"

$VAULT_COMMAND -field=data -format=json "secret/dsde/firecloud/$ENV/common/firecloud-account.json" >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"

if [ $ENV == perf ]; then
  $VAULT_COMMAND -field=key "$COMMON_VAULT_PATH/testrunner/testrunner-sa" | base64 -d > "$INTEGRATION_OUTPUT_LOCATION/testrunner-sa.json"
  else
    $VAULT_COMMAND -field=key "$ECM_VAULT_PATH/app-sa" | base64 -d > "$SERVICE_OUTPUT_LOCATION/ecm-sa.json"
fi
