#!/usr/bin/env bash
# Runs atm.SelfTest, the dependency-free test harness.
# Exits non-zero if any check fails, so it is safe to use in CI.
set -euo pipefail
cd "$(dirname "$0")"

./build.sh
echo
exec java -ea -cp out atm.SelfTest
