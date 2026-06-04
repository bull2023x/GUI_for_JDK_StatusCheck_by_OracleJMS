#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/export-jms-data.sh --compartment-id <ocid> [--fleet-name <name>] [options]
  scripts/export-jms-data.sh --compartment-id <ocid> --fleet-id <ocid> [options]

Options:
  --compartment-id <ocid>   OCI compartment OCID that contains JMS fleets.
  --fleet-name <name>       JMS fleet display name or name. Required when multiple fleets exist.
  --fleet-id <ocid>         JMS fleet OCID. Skips fleet-name lookup.
  --output-dir <path>       Directory for exported JSON. Default: ./jms-json-export
  --copy-to-resources       Also copy JSON into backend/src/main/resources/jms-data.
  --profile <name>          OCI CLI profile.
  --region <name>           OCI region override.
  --help                    Show this help.

Environment variable fallbacks:
  COMPARTMENT_ID, FLEET_NAME, FLEET_ID, OUTPUT_DIR, OCI_CLI_PROFILE, OCI_REGION
EOF
}

log() {
  printf '[jms-export] %s\n' "$*"
}

fail() {
  printf '[jms-export] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"

compartment_id="${COMPARTMENT_ID:-}"
fleet_name="${FLEET_NAME:-}"
fleet_id="${FLEET_ID:-}"
output_dir="${OUTPUT_DIR:-$project_root/jms-json-export}"
copy_to_resources=false
oci_profile="${OCI_CLI_PROFILE:-}"
oci_region="${OCI_REGION:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compartment-id)
      compartment_id="${2:-}"
      shift 2
      ;;
    --fleet-name)
      fleet_name="${2:-}"
      shift 2
      ;;
    --fleet-id)
      fleet_id="${2:-}"
      shift 2
      ;;
    --output-dir)
      output_dir="${2:-}"
      shift 2
      ;;
    --copy-to-resources)
      copy_to_resources=true
      shift
      ;;
    --profile)
      oci_profile="${2:-}"
      shift 2
      ;;
    --region)
      oci_region="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$compartment_id" ]] || fail "--compartment-id or COMPARTMENT_ID is required."

require_command oci
require_command jq

mkdir -p "$output_dir"

oci_cmd=(oci)
if [[ -n "$oci_profile" ]]; then
  oci_cmd+=(--profile "$oci_profile")
fi
if [[ -n "$oci_region" ]]; then
  oci_cmd+=(--region "$oci_region")
fi

fleets_json="$output_dir/fleets.json"
managed_instances_json="$output_dir/managed-instances.json"
tmp_fleets="$(mktemp "${TMPDIR:-/tmp}/jms-fleets.XXXXXX.json")"
tmp_managed="$(mktemp "${TMPDIR:-/tmp}/jms-managed.XXXXXX.json")"

cleanup() {
  [[ -n "$tmp_fleets" ]] && rm -f "$tmp_fleets"
  [[ -n "$tmp_managed" ]] && rm -f "$tmp_managed"
}
trap cleanup EXIT

log "Exporting JMS fleets from compartment: $compartment_id"
"${oci_cmd[@]}" jms fleet list \
  --compartment-id "$compartment_id" \
  --all \
  --output json > "$tmp_fleets"

jq -e '.data.items | type == "array"' "$tmp_fleets" >/dev/null \
  || fail "OCI fleet list did not return a data.items array."

mv "$tmp_fleets" "$fleets_json"
tmp_fleets=""

if [[ -z "$fleet_id" ]]; then
  fleet_count="$(jq '.data.items | length' "$fleets_json")"

  if [[ -n "$fleet_name" ]]; then
    fleet_id="$(
      jq -r --arg name "$fleet_name" '
        first(.data.items[] | select(."display-name" == $name or .name == $name) | .id) // ""
      ' "$fleets_json"
    )"
    [[ -n "$fleet_id" ]] || fail "Fleet not found: $fleet_name"
  elif [[ "$fleet_count" == "1" ]]; then
    fleet_id="$(jq -r '.data.items[0].id' "$fleets_json")"
    fleet_name="$(jq -r '.data.items[0]."display-name" // .data.items[0].name // ""' "$fleets_json")"
  else
    log "Multiple fleets found. Re-run with --fleet-name or --fleet-id."
    jq -r '.data.items[] | "- " + (."display-name" // .name // "<unnamed>") + " (" + .id + ")"' "$fleets_json"
    exit 2
  fi
fi

[[ -n "$fleet_id" && "$fleet_id" != "null" ]] || fail "Could not resolve fleet ID."

log "Exporting managed instance usage for fleet: $fleet_id"
"${oci_cmd[@]}" jms managed-instance-usage summarize \
  --fleet-id "$fleet_id" \
  --output json > "$tmp_managed"

jq -e '.data.items | type == "array"' "$tmp_managed" >/dev/null \
  || fail "OCI managed-instance-usage summarize did not return a data.items array."

mv "$tmp_managed" "$managed_instances_json"
tmp_managed=""

if [[ "$copy_to_resources" == true ]]; then
  resources_dir="$project_root/backend/src/main/resources/jms-data"
  mkdir -p "$resources_dir"
  cp "$fleets_json" "$resources_dir/fleets.json"
  cp "$managed_instances_json" "$resources_dir/managed-instances.json"
  log "Copied JSON into: $resources_dir"
fi

managed_count="$(jq '.data.items | length' "$managed_instances_json")"
resolved_fleet_name="$(
  jq -r --arg id "$fleet_id" '
    first(.data.items[] | select(.id == $id) | ."display-name" // .name) // ""
  ' "$fleets_json"
)"

log "Done."
log "Fleet: ${resolved_fleet_name:-$fleet_name}"
log "Managed instances: $managed_count"
log "Generated: $fleets_json"
log "Generated: $managed_instances_json"
log "Upload these files from the JMS JSON Upload panel, or use --copy-to-resources for classpath loading."
