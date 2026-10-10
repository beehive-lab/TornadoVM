#!/bin/bash
# jitLLM bench, Graal vs TC: both models, two rounds in alternating order.
S=/tmp/claude-1005/-home-michalis/e42565b8-e271-4e3e-b85c-56088cc77878/scratchpad/jit
cd ~/work/jitllm-jev; export JITLLM_ROOT=$PWD JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open PATH=~/.sdkman/candidates/java/21.0.2-open/bin:$PATH
source ~/tornadovm-compiler/setvars.sh >/dev/null
for round in 1 2; do
  order="graal tc"; [ $round = 2 ] && order="tc graal"
  for model in Llama-3.2-1B-Instruct-Q8_0 Qwen3-0.6B-F16; do
    for comp in $order; do
      JAVA_TOOL_OPTIONS="-Dtornado.compiler=$comp -Dtornado.compiler.fallback=false -Dtornado.cuda.priority=100" \
        timeout 1200 ./jitllm bench -m ~/models/$model.gguf --gpu --pp 128,512 --tg 64 --repetitions 5 --output json > $S/bench-$model-$comp-r$round.json 2> $S/bench-$model-$comp-r$round.err
      echo "$(date +%T) $model $comp r$round exit=$?"
    done
  done
done
echo DONE
