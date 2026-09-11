#!/bin/bash
# Profile the mixed JIT + tile + cuBLAS pipeline with nsys.
#
# Profile the JVM directly, not the launcher: `nsys profile $(which tornado) ...` produces a
# report with no GPU rows, because the Python launcher execs a child JVM that the injection does
# not follow. Two details make the argfile work:
#   * drop the leading java binary from --printJavaFlags, or `java @argfile` treats it as the
#     main class;
#   * inline the nested @.../exportLists/... files, because Java does not expand an @argfile
#     recursively and would pass the reference through as a literal argument.
set -euo pipefail
: "${TORNADOVM_HOME:?source setvars.sh first}"
: "${JAVA_HOME:?set JAVA_HOME}"
NSYS=${NSYS:-$(command -v nsys || echo /usr/local/cuda/bin/nsys)}
OUT=${OUT:-tilechain}
ITERATIONS=${ITERATIONS:-300}

tornado --printJavaFlags > flags.raw
python3 - "$PWD/flags.raw" > tornadovm.args <<'PY'
import sys
tokens = open(sys.argv[1]).read().split()
out = []
for token in tokens:
    if token.endswith("/bin/java"):
        continue
    if token.startswith("@"):
        out.extend(open(token[1:]).read().split())
        continue
    out.append(token)
if "-m" in out:
    out = out[:out.index("-m")]
print("\n".join(out))
PY

"$NSYS" profile --trace=cuda,nvtx --cuda-graph-trace=node --force-overwrite true -o "$OUT" \
    "$JAVA_HOME/bin/java" @tornadovm.args \
    -m tornado.unittests/uk.ac.manchester.tornado.unittests.tile.TileChainBenchmark "$ITERATIONS"

"$NSYS" stats --report cuda_gpu_kern_sum --report cuda_gpu_mem_time_sum --force-export true "$OUT.nsys-rep"
