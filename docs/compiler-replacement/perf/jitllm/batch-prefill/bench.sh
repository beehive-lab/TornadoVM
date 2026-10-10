#!/bin/bash
# jitLLM batched (MMA) prefill, Graal vs TC, --batch-prefill-size 32; two rounds in alternating order.
O=~/tc-perf/jitllm-batch
cd ~/work/jitllm-jev; export JITLLM_ROOT=$PWD JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open PATH=~/.sdkman/candidates/java/21.0.2-open/bin:$PATH
source ~/tornadovm-compiler/setvars.sh >/dev/null
for round in 1 2; do
  order="graal tc"; [ $round = 2 ] && order="tc graal"
  for model in Llama-3.2-1B-Instruct-f16 Qwen3-0.6B-F16 Llama-3.2-1B-Instruct-Q8_0; do
    for comp in $order; do
      JAVA_TOOL_OPTIONS="-Dtornado.compiler=$comp -Dtornado.compiler.fallback=false -Dtornado.cuda.priority=100" \
        timeout 1500 ./jitllm bench -m ~/models/$model.gguf --gpu --with-prefill-decode --batch-prefill-size 32 --pp 128,512 --tg 64 --repetitions 5 --output json > $O/bench-$model-$comp-r$round.json 2> $O/bench-$model-$comp-r$round.err
      echo "$(date +%T) $model $comp r$round exit=$?"
    done
  done
done
echo BENCH-DONE
