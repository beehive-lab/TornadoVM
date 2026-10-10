#!/bin/bash
# nsys per-kernel GPU time for jitLLM, Graal vs TC: a prefill-heavy run (pp512) and a decode-heavy run (tg256).
S=/tmp/claude-1005/-home-michalis/e42565b8-e271-4e3e-b85c-56088cc77878/scratchpad/jit
cd ~/work/jitllm-jev; export JITLLM_ROOT=$PWD JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open PATH=~/.sdkman/candidates/java/21.0.2-open/bin:$PATH
source ~/tornadovm-compiler/setvars.sh >/dev/null
mkdir -p $S/nsys
for model in Llama-3.2-1B-Instruct-Q8_0 Qwen3-0.6B-F16; do
  for phase in prefill decode; do
    args="--pp 512 --tg 1"; [ $phase = decode ] && args="--pp 16 --tg 256"
    for comp in graal tc; do
      o=$S/nsys/$model.$phase.$comp
      JAVA_TOOL_OPTIONS="-Dtornado.compiler=$comp -Dtornado.compiler.fallback=false -Dtornado.cuda.priority=100" \
        timeout 1800 nsys profile -t cuda --force-overwrite true -o $o ./jitllm bench -m ~/models/$model.gguf --gpu $args --repetitions 3 --output json > $o.log 2>&1
      nsys stats --force-overwrite true --report cuda_gpu_kern_sum --format csv --output $o $o.nsys-rep > /dev/null 2>&1
      echo "$(date +%T) $model $phase $comp exit=$?"
    done
  done
done
echo DONE
