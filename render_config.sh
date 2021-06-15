ENV=${1:dev}
VAULT_TOKEN=${2:-$(cat "$HOME"/.vault-token)}

VAULT_ADDR="https://clotho.broadinstitute.org:8200"
VAULT_PATH="secret/dsde/terra/kernel/$ENV/$ENV/externalcreds/providers"

VAULT_COMMAND="vault"

OUTPUT_LOCATION="$(dirname "$0")/service/src/main/resources/rendered/providers.yaml"

if ! [ -x "$(command -v vault)" ]; then
  VAULT_COMMAND="docker run --rm -e VAULT_TOKEN=$VAULT_TOKEN -e VAULT_ADDR=$VAULT_ADDR vault:1.7.2 $VAULT_COMMAND"
fi

echo > "$OUTPUT_LOCATION"

for PROVIDER in $($VAULT_COMMAND list -format=yaml $VAULT_PATH | sed  's/- //g')
do
  echo "externalcreds.providers.services.$PROVIDER:" >> "$OUTPUT_LOCATION"
  $VAULT_COMMAND read -format=yaml -field=data "$VAULT_PATH/$PROVIDER" | while read -r YAML_LINE; do
    echo "  $YAML_LINE" >> "$OUTPUT_LOCATION"
  done
  echo >> "$OUTPUT_LOCATION"
done
