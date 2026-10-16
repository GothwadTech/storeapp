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
TEST_HEAD = re.compile(r'^(\S.*?) > (.*?) FAILED$')
TEST_MSG = re.compile(r'^\s{4,}\S')
LINT_ISSUE = re.compile(r'^(\S+\.(?:kt|java|xml)):(\d+): (Error|Warning|Information): (.*?)(?: \[(\S+)\])?$')
LINT_TOTAL = re.compile(r'^\d+ errors?, \d+ warnings?$')


def emit(title: str, lines: list[str], limit: int = 20) -> None:
    if not lines:
        return
    body = '\n'.join(lines[:limit])
    body = body.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
    print(f'::error title={title}::{body[:6500]}')


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
        digest.extend('  ' + message for message in messages[:4])
    emit('Gothwad Store Kotlin errors', digest)

    failures = []
    index = 0
    while index < len(text):
        match = TEST_HEAD.match(text[index])
        if match:
            failures.append(f'{match.group(1)} > {match.group(2)}')
            for extra in text[index + 1:index + 4]:
                if TEST_MSG.match(extra):
                    failures.append('    ' + extra.strip()[:160])
                else:
                    break
        index += 1
    emit('Gothwad Store unit test failures', [f'{len(failures)} detail line(s)'] + failures)

    lint = []
    totals = ''
    for line in text:
        issue = LINT_ISSUE.match(line)
        if issue:
            lint.append(f'{issue.group(1).split("/app/")[-1]}:{issue.group(2)} {issue.group(3)}: {issue.group(4)}')
        elif LINT_TOTAL.match(line):
            totals = line
    emit('Gothwad Store lint issues', ([totals] if totals else []) + lint)

    other = [line for line in text if re.search(r'^\* What went wrong:|^Execution failed for task|^FAILURE: ', line)]
    emit('Gothwad Store gradle failure', other)

sdk = Path('sdk.log')
if sdk.exists():
    emit('Gothwad Store sdk errors', sdk.read_text(errors='replace').splitlines()[-6:])
PYTHON
