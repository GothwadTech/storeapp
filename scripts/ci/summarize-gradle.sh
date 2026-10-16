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

# Also surface diagnostics as a check annotation: Actions log/artifact download
# hosts may be inaccessible to a source-only sandbox, but annotations are read
# through api.github.com. Keep the message small so GitHub does not truncate the
# interesting lines away.
python3 - <<'PYTHON'
import re
from pathlib import Path

INTERESTING = re.compile(
    r'^\s*e: |^FAILURE:|Unresolved reference|What went wrong|^> Task .*FAILED'
    r'|^Execution failed for task|tests? (completed|failed)|^error: |^Caused by: '
)

def emit(title: str, lines: list[str]) -> None:
    if not lines:
        return
    body = '\n'.join(lines[:25])
    body = body.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
    print(f'::error title={title}::{body[:6000]}')

log = Path('gradle.log')
if log.exists():
    hits = [line for line in log.read_text(errors='replace').splitlines() if INTERESTING.search(line)]
    # Kotlin reports the same unresolved symbol repeatedly; keep the first hits.
    emit('Gothwad Store gradle errors', hits)

sdk = Path('sdk.log')
if sdk.exists():
    emit('Gothwad Store sdk errors', sdk.read_text(errors='replace').splitlines()[-6:])
PYTHON
