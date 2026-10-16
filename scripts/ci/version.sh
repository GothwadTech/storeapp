#!/usr/bin/env bash
set -euo pipefail
# Inputs are environment variables, never interpolated into executable shell.
V_CODE="${VERSION_CODE:-${GITHUB_RUN_NUMBER:-1}}"
V_NAME="${VERSION_NAME:-1.0.${V_CODE}}"
if [[ ! "$V_CODE" =~ ^[1-9][0-9]{0,9}$ ]] || ((10#$V_CODE > 2100000000)); then
  echo '::error::Version code must be an integer between 1 and 2100000000.'
  exit 1
fi
if [[ ! "$V_NAME" =~ ^[0-9][0-9A-Za-z.+_-]{0,63}$ ]]; then
  echo '::error::Version name must start with a digit and contain only letters, numbers, dots, +, _ or - (max 64 characters).'
  exit 1
fi
printf 'v_code=%s\nv_name=%s\n' "$V_CODE" "$V_NAME" >> "${GITHUB_OUTPUT:?}"
echo "Gothwad Store version: $V_NAME ($V_CODE)"
