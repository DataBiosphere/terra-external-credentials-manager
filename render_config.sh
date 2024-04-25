ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}
LIVE_DB=${3:-false}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
if [ $ENV == 'prod' ]; then
  ECM_VAULT_PATH="secret/suitable/terra/kernel/prod/prod/externalcreds"
else
  ECM_VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds"
fi
COMMON_VAULT_PATH="secret/dsde/terra/kernel/$ENV/common"

VAULT_COMMAND="vault read"

SERVICE_OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"
SECRET_ENV_VARS_LOCATION="${SERVICE_OUTPUT_LOCATION}/secrets.env"
INTEGRATION_OUTPUT_LOCATION="$(dirname "$0")/integration/src/main/resources/rendered"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

if [ $ENV == 'prod' ]; then
  gcloud container clusters get-credentials --zone us-central1 --project broad-dsde-prod terra-prod
else
  gcloud container clusters get-credentials --zone us-central1-a --project broad-dsde-$ENV terra-$ENV
fi


if [ -f "${SECRET_ENV_VARS_LOCATION}" ]; then
  rm "${SECRET_ENV_VARS_LOCATION}"
fi

{
  if $LIVE_DB; then
    echo export DATABASE_NAME="$(kubectl -n terra-$ENV get secret externalcreds-postgres-db-creds -o 'go-template={{index .data "db"}}' | base64 -d)"
    echo export DATABASE_USER="$(kubectl -n terra-$ENV get secret externalcreds-postgres-db-creds -o 'go-template={{index .data "username"}}' | base64 -d)"
    echo export DATABASE_USER_PASSWORD="$(kubectl -n terra-$ENV get secret externalcreds-postgres-db-creds -o 'go-template={{index .data "password"}}' | base64 -d)"
  fi

  if [ $ENV != 'prod' ]; then
      echo export RAS_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-providers -o 'go-template={{index .data "ras-client-id"}}' | base64 -d)"
      echo export RAS_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-providers -o 'go-template={{index .data "ras-client-secret"}}' | base64 -d)"
  fi

  echo export GITHUB_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-providers -o 'go-template={{index .data "github-client-id"}}' | base64 -d)"
  echo export GITHUB_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-providers -o 'go-template={{index .data "github-client-secret"}}' | base64 -d)"
  echo export ANVIL_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "anvil-client-id"}}' | base64 -d)"
  echo export ANVIL_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "anvil-client-secret"}}' | base64 -d)"
  echo export FENCE_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "fence-client-id"}}' | base64 -d)"
  echo export FENCE_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "fence-client-secret"}}' | base64 -d)"
  echo export DCF_FENCE_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "dcf-fence-client-id"}}' | base64 -d)"
  echo export DCF_FENCE_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "dcf-fence-client-secret"}}' | base64 -d)"
  echo export KIDS_FIRST_CLIENT_ID="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "kids-first-client-id"}}' | base64 -d)"
  echo export KIDS_FIRST_CLIENT_SECRET="$(kubectl -n terra-$ENV get secret externalcreds-fence -o 'go-template={{index .data "kids-first-client-secret"}}' | base64 -d)"

  echo export DEPLOY_ENV=$ENV
  echo export SAM_ADDRESS=https://sam.dsde-${ENV}.broadinstitute.org
} >> "${SECRET_ENV_VARS_LOCATION}"

$VAULT_COMMAND -field=swagger-client-id "$ECM_VAULT_PATH/swagger-client-id" >"$SERVICE_OUTPUT_LOCATION/swagger-client-id"

$VAULT_COMMAND -field=data -format=json "secret/dsde/firecloud/$ENV/common/firecloud-account.json" >"$INTEGRATION_OUTPUT_LOCATION/user-delegated-sa.json"

if [ $ENV == perf ]; then
  $VAULT_COMMAND -field=key "$COMMON_VAULT_PATH/testrunner/testrunner-sa" | base64 -d > "$INTEGRATION_OUTPUT_LOCATION/testrunner-sa.json"
  else
    $VAULT_COMMAND -field=key "$ECM_VAULT_PATH/app-sa" | base64 -d > "$SERVICE_OUTPUT_LOCATION/ecm-sa.json"
fi
