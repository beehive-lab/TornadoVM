#!/bin/bash
# jitLLM bench on Metal (M3 Pro), Graal vs TC: two models, two rounds in alternating order.
S=${S:-./bench}
cd ~/work/jitllm; export JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open PATH=~/.sdkman/candidates/java/21.0.2-open/bin:$PATH
source ~/tornadovm-compiler/setvars.sh >/dev/null
for round in 1 2; do
  order="graal tc"; [ $round = 2 ] && order="tc graal"
  for model in Llama-3.2-1B-Instruct-f16 models/Qwen3-1.7B-f16; do
    n=$(basename $model)
    for comp in $order; do
      JAVA_TOOL_OPTIONS="-Dtornado.compiler=$comp -Dtornado.compiler.fallback=false" \
        ./jitllm bench -m ~/models/$model.gguf --gpu --metal --fp32-kv-cache --pp 128,512 --tg 64 --repetitions 5 --output json > $S/bench-$n-$comp-r$round.json 2> $S/bench-$n-$comp-r$round.err
      echo "$(date +%T) $n $comp r$round exit=$?"
    done
  done
done
echo DONE
