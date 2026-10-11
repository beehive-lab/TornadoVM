#!/usr/bin/env bash
# TC on tornado-benchmarks (CUDA RTX 4090, device 1:0): profiler kernel times + compile times and nsys
# GPU kernel summaries, written to ~/tc-perf/run as <bench>.<label>.* next to the Graal results that
# perf-compare.sh recorded before Graal was removed (label "graal"), so collect.py compares them.
# Usage: perf-tc.sh <label> [benchmarks...]     (TORNADOVM_HOME: source setvars.sh of the checkout first)
set -uo pipefail
label=$1; shift
out=~/tc-perf/run; mkdir -p $out
benches=${*:-"saxpy addImage stencil convolvearray convolveimage blackscholes montecarlo blurFilter renderTrack euler nbody sgemm dgemm mandelbrot dft juliaset"}
JVM="-Xms16G -Xmx16G -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipserial=True -Dtornado.blacklist.devices=0:0,0:1 -Dtornado.tc.compileTimes=true -Dtornado.tc.stats=true -Dtornado.cuda.codecache.enable=False"
RUN="-m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner"
for b in $benches; do
  for r in 1 2 3; do
    timeout 1200 tornado --enableProfiler silent --jvm="$JVM" $RUN --params="$b" > $out/$b.$label.prof$r.log 2>&1
  done
  timeout 1800 nsys profile -t cuda --force-overwrite true -o $out/$b.$label tornado --jvm="$JVM" $RUN --params="$b" > $out/$b.$label.nsys.log 2>&1
  nsys stats --force-overwrite true --report cuda_gpu_kern_sum --format csv --output $out/$b.$label $out/$b.$label.nsys-rep > /dev/null 2>&1
  echo "$b done $(date +%T)"
done
echo ALL DONE
