#!/usr/bin/env bash
# Compiles every .java file under src/ into out/ using nothing but javac.
# No Maven, no Gradle, no external libraries - plain Java only.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac was not found on your PATH." >&2
    echo "       Install any JDK 8 or newer and run this script again." >&2
    exit 1
fi

echo "Using: $(javac -version 2>&1)"

rm -rf out
mkdir -p out

# An @argfile keeps this working even with many source files or spaces in paths.
find src -name '*.java' -print > .sources.txt
javac -encoding UTF-8 -d out @.sources.txt
rm -f .sources.txt

echo "Build OK -> $(find out -name '*.class' | wc -l | tr -d ' ') class files in out/"
