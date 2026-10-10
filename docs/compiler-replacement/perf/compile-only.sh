#!/usr/bin/env bash
# Cold compile times per benchmark (fresh JVM each, code cache off), 3 runs, Graal vs TC.
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
out=~/tc-perf/cold; mkdir -p $out
JVM="-Xms16G -Xmx16G -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipserial=True -Dtornado.blacklist.devices=0:0,0:1 -Dtornado.tc.compileTimes=true -Dtornado.tc.stats=true -Dtornado.cuda.codecache.enable=False -Dtornado.benchmarks.iterations=2"
for b in saxpy addImage stencil convolvearray convolveimage blackscholes montecarlo blurFilter renderTrack euler nbody sgemm dgemm mandelbrot dft juliaset; do
  for c in graal tc; do for r in 1 2 3; do
    timeout 600 tornado --enableProfiler silent --jvm="$JVM -Dtornado.compiler=$c" -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner --params="$b" 2>&1 | grep "compile-times" > $out/$b.$c.$r.log
  done; done
done
echo COLD DONE
