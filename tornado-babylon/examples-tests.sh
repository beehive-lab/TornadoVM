#!/usr/bin/env bash
# Runs the self-checking examples of examples/src on TornadoVM devices and prints one line per run.
#   ./examples-tests.sh [driver:device ...]     (default: every device `tornado --devices` lists)
# Tile examples (CUDA Tile) run on CUDA devices only. Exit status: the number of failed runs.
set -uo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
DEVICES=("$@")
if [[ ${#DEVICES[@]} -eq 0 ]]; then
    mapfile -t DEVICES < <(tornado --devices 2>/dev/null | sed -n 's/.*Tornado device=\([0-9]*:[0-9]*\).*/\1/p')
fi
failed=0
run() {   # run <device> <label> <command...>: PASS when the command exits 0 and prints no FAIL
    local dev=$1 label=$2; shift 2
    local log=build/examples-tests/${dev/:/-}-${label//[^A-Za-z0-9]/_}.log
    mkdir -p build/examples-tests
    local start=$(date +%s)
    if "$@" > "$log" 2>&1 && ! grep -qE "FAIL|WRONG" "$log"; then
        printf "%-6s %-34s PASS  %3ds\n" "$dev" "$label" $(( $(date +%s) - start ))
    else
        failed=$((failed + 1))
        printf "%-6s %-34s FAIL  %3ds  (%s)\n" "$dev" "$label" $(( $(date +%s) - start )) "$log"
    fi
}
for dev in "${DEVICES[@]}"; do
    d=(${dev/:/ })
    backend=$(tornado --devices 2>/dev/null | grep -A1 "Tornado device=$dev\b" | tail -1)
    reflect=(java @build/tornado.args --enable-preview --add-modules=jdk.incubator.code -Dtornado.tc.frontend=babylon -Dreflect.device=$dev
             -cp build/tornado-babylon.jar:build/examples reflect.ReflectKernels)
    ./run.sh -d "$dev" build/examples hattasks.HatTasksChecks > /dev/null 2>&1 || true   # writes build/tornado.args
    run "$dev" "ReflectKernels" "${reflect[@]}"
    if [[ "$backend" == *CUDA* && "$backend" != *OPENCL* ]]; then
        run "$dev" "ReflectKernels (CUDA graph)" java @build/tornado.args --enable-preview --add-modules=jdk.incubator.code -Dtornado.tc.frontend=babylon \
            -Dreflect.device=$dev -Dreflect.cudaGraph=true -cp build/tornado-babylon.jar:build/examples reflect.ReflectKernels
    fi
    run "$dev" "HatTasksChecks" ./run.sh -d "$dev" build/examples hattasks.HatTasksChecks
    run "$dev" "NBodyTaskGraph" ./run.sh -d "$dev" build/examples hattasks.NBodyTaskGraph 2048 10
    run "$dev" "LifeBench (resident buffers)" ./run.sh -d "$dev" "$HAT_BUILD/hat-example-life-1.0.jar:build/examples" lifebench.LifeBench 256 256 20 3
    TC_JVM=-DHAT=MC run "$dev" "LifeBench (HAT=MC)" ./run.sh -d "$dev" "$HAT_BUILD/hat-example-life-1.0.jar:build/examples" lifebench.LifeBench 256 256 20 3
    if [[ "$backend" == *CUDA* && "$backend" != *OPENCL* ]]; then
        run "$dev" "TileExamples 256" ./run.sh -d "$dev" build/examples tiles.TileExamples 256
        run "$dev" "TileExamples 1000 (ragged edges)" ./run.sh -d "$dev" build/examples tiles.TileExamples 1000
    fi
done
echo "examples-tests: $failed failed"
exit $failed
