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

# Also surface diagnostics as an annotation: Actions log/artifact download hosts
# may be inaccessible to a source-only sandbox, but check annotations use api.github.com.
python3 - <<'PYTHON'
from pathlib import Path
for name in ('sdk.log', 'gradle.log'):
    path = Path(name)
    if path.exists():
        tail = '\n'.join(path.read_text(errors='replace').splitlines()[-100:])
        tail = tail.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::error title=Gothwad Store {name} failure::{tail}')
PYTHON
