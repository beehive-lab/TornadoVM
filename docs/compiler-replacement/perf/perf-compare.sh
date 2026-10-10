#!/usr/bin/env bash
# Graal vs TC on tornado-benchmarks (CUDA RTX 4090, device 1:0): profiler kernel times + compile times,
# nsys GPU kernel summaries, ncu metrics for a subset. Usage: perf-compare.sh [benchmarks...]
set -uo pipefail
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
out=~/tc-perf/run; mkdir -p $out
benches=${*:-"saxpy addImage stencil convolvearray convolveimage blackscholes montecarlo blurFilter renderTrack euler nbody sgemm dgemm mandelbrot dft juliaset"}
JVM="-Xms16G -Xmx16G -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipserial=True -Dtornado.blacklist.devices=0:0,0:1 -Dtornado.tc.compileTimes=true -Dtornado.tc.stats=true -Dtornado.cuda.codecache.enable=False"
RUN="-m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner"
for b in $benches; do
  for c in graal tc; do
    # 1. profiler: kernel min/avg (ns) and per-task compile times; 3 runs (fresh JVM each)
    for r in 1 2 3; do
      timeout 1200 tornado --enableProfiler silent --jvm="$JVM -Dtornado.compiler=$c" $RUN --params="$b" > $out/$b.$c.prof$r.log 2>&1
    done
    # 2. nsys: GPU kernel time distribution
    timeout 1800 nsys profile -t cuda --force-overwrite true -o $out/$b.$c tornado --jvm="$JVM -Dtornado.compiler=$c" $RUN --params="$b" > $out/$b.$c.nsys.log 2>&1
    nsys stats --force-overwrite true --report cuda_gpu_kern_sum --format csv --output $out/$b.$c $out/$b.$c.nsys-rep > /dev/null 2>&1
  done
  echo "$b done $(date +%T)"
done
echo ALL DONE
