#!/usr/bin/env bash
# Runs a HAT program on TornadoVM: ./run.sh [-d <tornado device, e.g. 1:0>] <extra classpath> <main class> [args...]
# The TornadoHatBackend is first on the class path, so Backend.FIRST picks it.
set -euo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
DEVICE=""
if [[ "${1:-}" == "-d" ]]; then DEVICE="-Dhat.t.device=$2"; shift 2; fi
EXTRA=$1; MAIN=$2; shift 2
CP=build/tornado-babylon.jar:$HAT_BUILD/hat-core-1.0.jar:$HAT_BUILD/hat-optkl-1.0.jar:$EXTRA
PARAMS=()
if [[ $# -gt 0 ]]; then PARAMS=(--params "$*"); fi   # tornado.py takes program arguments through --params
exec tornado --jvm="--enable-preview --add-modules=jdk.incubator.code -Dtornado.recover.bailout=False -Dheadless=true $DEVICE ${TC_JVM:-}" \
    -cp "$CP" "${PARAMS[@]}" "$MAIN"
