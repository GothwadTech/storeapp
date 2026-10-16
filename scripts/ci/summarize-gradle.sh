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
from collections import OrderedDict
from pathlib import Path

ERROR = re.compile(r'^\s*e: .*?/(app/src/[^:]+):(\d+):(\d+) (.*)$')

def emit(title: str, lines: list[str]) -> None:
    if not lines:
        return
    body = '\n'.join(lines[:40])
    body = body.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
    print(f'::error title={title}::{body[:7000]}')

log = Path('gradle.log')
if log.exists():
    text = log.read_text(errors='replace').splitlines()
    errors = []
    for line in text:
        match = ERROR.match(line)
        if match:
            errors.append((match.group(1), match.group(2), match.group(3), match.group(4)))
    digest = [f'{len(errors)} Kotlin error(s)']
    per_file = OrderedDict()
    for path, line_no, col, message in errors:
        per_file.setdefault(path, []).append(f'{line_no}:{col} {message}')
    for path, messages in per_file.items():
        digest.append(path)
        digest.extend('  ' + message for message in messages[:3])
        if len(messages) > 3:
            digest.append(f'  … {len(messages) - 3} more in this file')
    emit('Gothwad Store Kotlin errors', digest)

    other = [line for line in text if re.search(r'^\* What went wrong:|^Execution failed for task|^FAILURE:|error: ', line)]
    emit('Gothwad Store gradle failure', other)

sdk = Path('sdk.log')
if sdk.exists():
    emit('Gothwad Store sdk errors', sdk.read_text(errors='replace').splitlines()[-6:])
PYTHON
