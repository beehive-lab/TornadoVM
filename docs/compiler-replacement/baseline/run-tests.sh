#!/usr/bin/env bash
# Full tornado-test baseline on one device. Usage: run-tests.sh <driver:device> <label> [run-id]
# Writes logs to $BASELINE_OUT (default ~/tc-baseline). Uses the SDK of the current checkout.
set -uo pipefail
dev=$1; label=$2; run=${3:-1}
out=${BASELINE_OUT:-$HOME/tc-baseline}; mkdir -p "$out"
cd "$(dirname "$0")/../../.." && source setvars.sh > /dev/null
log="$out/tests-$label-run$run.log"
start=$(date +%s)
tornado-test --verbose -J"-Dtornado.unittests.device=$dev" > "$log" 2>&1
echo "exit=$? seconds=$(( $(date +%s) - start )) device=$dev label=$label run=$run java=$(java -version 2>&1 | head -1)" >> "$log"
tail -1 "$log"
