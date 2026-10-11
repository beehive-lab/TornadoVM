#!/usr/bin/env bash
# Leyden AOT cache (JDK 25, JEP 514 one-step training): one training compile writes tc25.aot; later JVMs
# start with -XX:AOTCache=tc25.aot. Usage: aot-cache.sh <tc-jdk25.jar> <harness.jar>
set -euo pipefail
J25=${J25:-$HOME/.sdkman/candidates/java/25.0.2-open}
"$J25/bin/java" -XX:AOTCacheOutput=tc25.aot -cp "$1:$2" Cold uk.ac.manchester.tornado.benchmarks.ComputeKernels blackscholes 3
"$J25/bin/java" -XX:AOTCache=tc25.aot -cp "$1:$2" Cold uk.ac.manchester.tornado.benchmarks.ComputeKernels saxpy 1
