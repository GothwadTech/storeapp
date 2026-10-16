#!/usr/bin/env bash
set -euo pipefail
{
  echo '## Gothwad Store build diagnostics'
  echo '```text'
  if [[ -f gradle.log ]]; then
    grep -E -A 8 '^e:|^FAILURE:|^\* What went wrong:|^Execution failed|^.*error:|^> Task .*FAILED|^.*tests completed' gradle.log | head -160 || true
  fi
  echo '```'
  echo 'See the android-reports artifact for complete Gradle, lint and test reports.'
} >> "${GITHUB_STEP_SUMMARY:?}"
