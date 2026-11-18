#!/usr/bin/env bash
# Runs the multi-threaded ATM demo. Builds first if out/ is missing.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f out/atm/ATMSystem.class ]; then
    echo "(compiling first - out/ was missing)"
    ./build.sh
    echo
fi

exec java -cp out atm.ATMSystem
