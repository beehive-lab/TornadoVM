#!/usr/bin/env bash
# One command to check this module against a Babylon JDK, HAT build and TornadoVM SDK: builds it, runs
# TornadoVM's checkstyle rules, every self-checking example on every device, and HAT's own unit tests
# on one device. Exit status 0 only when all of it holds.
#   ./check.sh [-d <tornado device for HAT's tests, default 1:0>] [-m <fewest HAT tests that must pass, default 154>]
# (154 of HAT's 156 tests pass on a CUDA device; on OpenCL fewer do, tiles and tensors being CUDA only: use -m.)
set -uo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
DEVICE=1:0
MIN=154
while getopts "d:m:" opt; do
    case $opt in
        d) DEVICE=$OPTARG ;;
        m) MIN=$OPTARG ;;
        *) exit 2 ;;
    esac
done
failed=0
step() {   # step <label> <command...>
    local label=$1; shift
    if "$@"; then echo "check: $label ok"; else echo "check: $label FAILED"; failed=$((failed + 1)); fi
}
step "build" ./build.sh
style() { local out; out=$(./checkstyle.sh 2>&1); echo "$out" | grep -E "WARN|ERROR" | head -20; ! echo "$out" | grep -qE "WARN|ERROR"; }
step "checkstyle" style
step "examples" ./examples-tests.sh
hat() {
    local passed
    passed=$(./hat-tests.sh -d "$DEVICE" 2>&1 | sed -n 's/^== .*: \([0-9]*\) passed of .*/\1/p')
    echo "HAT tests on $DEVICE: ${passed:-0} passed (at least $MIN expected)"
    [[ ${passed:-0} -ge $MIN ]]
}
step "HAT tests" hat
echo "check: $failed failed"
exit $failed
