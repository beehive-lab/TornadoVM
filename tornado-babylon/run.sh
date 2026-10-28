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
ARGS=$PWD/build/tornado.args
sed "s|\${TORNADOVM_HOME}|$TORNADOVM_HOME|g" "$TORNADOVM_HOME/tornado-argfile.template" > "$ARGS"
CP=$PWD/build/tornado-babylon.jar:$HAT_BUILD/hat-core-1.0.jar:$HAT_BUILD/hat-optkl-1.0.jar:$EXTRA
exec java @"$ARGS" --enable-preview --add-modules=jdk.incubator.code -Dtornado.recover.bailout=False -Dheadless=true $DEVICE ${TC_JVM:-} \
    -cp "$CP" "$MAIN" "$@"
