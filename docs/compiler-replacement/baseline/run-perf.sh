#!/usr/bin/env bash
# Phase 1 performance baseline: tornado-benchmarks (all devices) + jitLLM bench.
# Usage: run-perf.sh [run-id]. Logs to $BASELINE_OUT (default ~/tc-baseline).
set -uo pipefail
run=${1:-1}
out=${BASELINE_OUT:-$HOME/tc-baseline}; mkdir -p "$out"
repo=$(cd "$(dirname "$0")/../../.." && pwd)
cd "$repo" && source setvars.sh > /dev/null
start=$(date +%s)
tornado-benchmarks.py --iterations 10 > "$out/bench-tornado-run$run.log" 2>&1
echo "exit=$? seconds=$(( $(date +%s) - start ))" >> "$out/bench-tornado-run$run.log"

jit=${JITLLM_ROOT:-$HOME/work/jitllm-jev}
export TORNADOVM_HOME=$(readlink -f "$repo/bin/sdk") JITLLM_ROOT=$jit
for m in "$HOME/models/Llama-3.2-1B-Instruct-Q8_0.gguf" "$HOME/models/Qwen3-0.6B-F16.gguf"; do
  name=$(basename "$m" .gguf)
  (cd "$jit" && ./jitllm bench -m "$m" --gpu --pp 128,512 --tg 64 --repetitions 5 --output json --verbose) \
    > "$out/bench-jitllm-$name-run$run.json" 2> "$out/bench-jitllm-$name-run$run.err"
  echo "$name exit=$?"
done
