ENV=${1:-dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds/providers"

VAULT_COMMAND="vault"

OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered/providers.yaml"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.3 $VAULT_COMMAND"
fi

$VAULT_COMMAND read -field=providers "$VAULT_PATH" > "$OUTPUT_LOCATION"
