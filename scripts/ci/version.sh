#!/usr/bin/env bash
# Version code/name for a build.
#
# The version code must always be higher than the one of the last published
# release, otherwise Android refuses the update ("version downgrade"). The
# GitHub run number alone is not enough: a tag-triggered release re-uses the
# run number of the calling workflow, which can be much lower. The highest
# published release tag is therefore used as the lower bound.
set -euo pipefail
: "${GITHUB_OUTPUT:?}"

# Highest published patch version, e.g. 1.0.13 -> 13
latest_published_code() {
  local tag
  tag="$(git tag --list 'v[0-9]*' 2>/dev/null | sed 's/^v//' | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -n 1 || true)"
  [[ -n "$tag" ]] || { echo 0; return; }
  local last="${tag##*.}"
  [[ "$last" =~ ^[0-9]+$ ]] || last=0
  echo "$last"
}

RUN_CODE="${GITHUB_RUN_NUMBER:-1}"
PUBLISHED_CODE="$(latest_published_code)"
if [[ ! "$PUBLISHED_CODE" =~ ^[0-9]+$ ]]; then PUBLISHED_CODE=0; fi

V_CODE="${VERSION_CODE:-${RUN_CODE}}"
if [[ ! "$V_CODE" =~ ^[0-9]+$ ]]; then
  echo '::error::The version code must be a positive integer.'
  exit 1
fi
# Never publish a build that cannot update the previous release.
if ((10#$V_CODE <= 10#$PUBLISHED_CODE)); then
  V_CODE=$((10#$PUBLISHED_CODE + 1))
fi
if ((10#$V_CODE > 2100000000)); then
  echo '::error::The version code must be 2100000000 or lower.'
  exit 1
fi

V_NAME="${VERSION_NAME:-1.0.${V_CODE}}"
if [[ ! "$V_NAME" =~ ^[0-9][0-9A-Za-z.+_-]{0,63}$ ]]; then
  echo '::error::The version name must start with a digit and may only contain letters, numbers, dots, +, _ or - (max 64 characters).'
  exit 1
fi

printf 'v_code=%s\nv_name=%s\n' "$V_CODE" "$V_NAME" >> "$GITHUB_OUTPUT"
echo "Kiwi Store version: $V_NAME ($V_CODE) - last published code was $PUBLISHED_CODE"
