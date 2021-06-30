ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds"

VAULT_COMMAND="vault read"

OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

$VAULT_COMMAND -field=providers "$VAULT_PATH/providers" > "$OUTPUT_LOCATION/providers.yaml"
$VAULT_COMMAND -field=swagger-client-id "$VAULT_PATH/swagger-client-id" > "$OUTPUT_LOCATION/swagger-client-id"
