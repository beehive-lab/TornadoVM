#!/usr/bin/env bash
# ncu metrics, Graal vs TC, one launch per benchmark kernel (after warm-up launches).
set -uo pipefail
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
out=~/tc-perf/ncu; mkdir -p $out
benches=${*:-"saxpy blackscholes nbody sgemm dgemm mandelbrot dft juliaset montecarlo convolveimage"}
JVM="-Xms16G -Xmx16G -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipserial=True -Dtornado.blacklist.devices=0:0,0:1"
RUN="-m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner"
M="gpu__time_duration.sum,launch__registers_per_thread,sm__warps_active.avg.pct_of_peak_sustained_active,sm__throughput.avg.pct_of_peak_sustained_elapsed,dram__throughput.avg.pct_of_peak_sustained_elapsed,smsp__inst_executed.sum,launch__grid_size,launch__block_size"
for b in $benches; do
  for c in graal tc; do
    timeout 1800 ncu --target-processes all --launch-skip 5 --launch-count 1 --metrics $M --csv tornado --jvm="$JVM -Dtornado.compiler=$c" $RUN --params="$b" > $out/$b.$c.csv 2> $out/$b.$c.err
  done
  echo "$b ncu done $(date +%T)"
done
