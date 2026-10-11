#!/bin/bash
# jitLLM bench on Metal after the Graal removal: TC only, same settings as perf/jitllm/metal/bench.sh.
S=${S:-./bench}
cd ~/work/jitllm; export JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open PATH=~/.sdkman/candidates/java/21.0.2-open/bin:$PATH
source ~/tornadovm-compiler/setvars.sh >/dev/null
./mvnw -q clean package -DskipTests
for model in Llama-3.2-1B-Instruct-f16 models/Qwen3-1.7B-f16; do
  n=$(basename $model)
  ./jitllm bench -m ~/models/$model.gguf --gpu --metal --fp32-kv-cache --pp 128,512 --tg 64 --repetitions 5 --output json > $S/bench-$n-tconly.json 2> $S/bench-$n-tconly.err
  echo "$(date +%T) $n exit=$?"
done
echo DONE
