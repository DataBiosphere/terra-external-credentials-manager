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
  echo export ANVIL_CLIENT_ID="$($VAULT_COMMAND -field=anvil-client-id "$ECM_VAULT_PATH/fence")"
  echo export ANVIL_CLIENT_SECRET="$($VAULT_COMMAND -field=anvil-secret "$ECM_VAULT_PATH/fence")"
  echo export FENCE_CLIENT_ID="$($VAULT_COMMAND -field=client-id "$ECM_VAULT_PATH/fence")"
  echo export FENCE_CLIENT_SECRET="$($VAULT_COMMAND -field=client-secret "$ECM_VAULT_PATH/fence")"
  echo export DCF_FENCE_CLIENT_ID="$($VAULT_COMMAND -field=dcf-fence-client-id "$ECM_VAULT_PATH/fence")"
  echo export DCF_FENCE_CLIENT_SECRET="$($VAULT_COMMAND -field=dcf-fence-client-secret "$ECM_VAULT_PATH/fence")"
  echo export KIDS_FIRST_CLIENT_ID="$($VAULT_COMMAND -field=kids-first-client-id "$ECM_VAULT_PATH/fence")"
  echo export KIDS_FIRST_CLIENT_SECRET="$($VAULT_COMMAND -field=kids-first-client-secret "$ECM_VAULT_PATH/fence")"
  echo export DATABASE_USER_PASSWORD="$($VAULT_COMMAND -field=password "$ECM_VAULT_PATH/postgres/db-creds")"
  echo export DATABASE_NAME="$($VAULT_COMMAND -field=db "$ECM_VAULT_PATH/postgres/db-creds")"
  echo export DATABASE_USER="$($VAULT_COMMAND -field=username "$ECM_VAULT_PATH/postgres/db-creds")"
} >> "${SECRET_ENV_VARS_LOCATION}"

$VAULT_COMMAND -field=swagger-client-id "$ECM_VAULT_PATH/swagger-client-id" >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"

$VAULT_COMMAND -field=data -format=json "secret/dsde/firecloud/$ENV/common/firecloud-account.json" >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"

if [ $ENV == perf ]; then
  $VAULT_COMMAND -field=key "$COMMON_VAULT_PATH/testrunner/testrunner-sa" | base64 -d > "$INTEGRATION_OUTPUT_LOCATION/testrunner-sa.json"
  else
    $VAULT_COMMAND -field=key "$ECM_VAULT_PATH/app-sa" | base64 -d > "$SERVICE_OUTPUT_LOCATION/ecm-sa.json"
fi
