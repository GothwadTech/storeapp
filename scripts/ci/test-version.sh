#!/usr/bin/env bash
# Tests for scripts/ci/version.sh - the guard that keeps every published
# version code above the last release, so a new build can update the old one.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION_SCRIPT="$SCRIPT_DIR/version.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { echo "::error::version.sh test failed: $1"; exit 1; }

run_version() {
  # run_version <git repo> <version code> <version name> -> prints "code name"
  local repo="$1" code="$2" name="$3"
  local out="$WORK/out.txt"
  : > "$out"
  (
    cd "$repo"
    VERSION_CODE="$code" VERSION_NAME="$name" GITHUB_OUTPUT="$out" \
      GITHUB_RUN_NUMBER="${GITHUB_RUN_NUMBER:-7}" bash "$VERSION_SCRIPT" >/dev/null
  )
  local v_code v_name
  v_code="$(sed -n 's/^v_code=//p' "$out")"
  v_name="$(sed -n 's/^v_name=//p' "$out")"
  echo "$v_code $v_name"
}

# A repository with the release tags of the last published versions.
REPO="$WORK/repo"
mkdir -p "$REPO"
(
  cd "$REPO"
  git init -q .
  git config user.email ci@example.com
  git config user.name ci
  touch file
  git add file
  git commit -qm init
  git tag v1.0.9
  git tag v1.0.13
)

read -r code name <<< "$(run_version "$REPO" "" "")"
[[ "$code" -gt 13 ]] || fail "expected a code above the last release (13), got $code"
[[ "$name" == "1.0.$code" ]] || fail "unexpected version name $name"

# An explicit code is honoured when it is high enough.
read -r code name <<< "$(run_version "$REPO" 42 "2.5.0")"
[[ "$code" == "42" ]] || fail "explicit code was not used: $code"
[[ "$name" == "2.5.0" ]] || fail "explicit name was not used: $name"

# An explicit code that is too low is raised above the last release.
read -r code name <<< "$(run_version "$REPO" 5 "")"
[[ "$code" -gt 13 ]] || fail "a too low code was not raised: $code"

# A repository without any release tag still produces a usable code.
EMPTY="$WORK/empty"
mkdir -p "$EMPTY"
(
  cd "$EMPTY"
  git init -q .
  git config user.email ci@example.com
  git config user.name ci
  touch file
  git add file
  git commit -qm init
)
read -r code name <<< "$(run_version "$EMPTY" "" "")"
[[ "$code" =~ ^[0-9]+$ ]] || fail "no code was produced: $code"
[[ "$name" == "1.0.$code" ]] || fail "unexpected version name $name"

echo "version.sh tests passed"
