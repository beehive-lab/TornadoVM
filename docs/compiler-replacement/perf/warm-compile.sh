#!/usr/bin/env bash
# Per-kernel compile times over many kernels per JVM (C5b families), Graal vs TC, from the profiler.
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
out=~/tc-perf/warm; mkdir -p $out
for c in graal tc; do
  : > $out/$c.log
  for k in $(cat /tmp/claude-1005/-home-michalis/e42565b8-e271-4e3e-b85c-56088cc77878/scratchpad/c5b-classes.txt); do
    echo "CLASS $k" >> $out/$c.log
    timeout 900 tornado-test -J"-Dtornado.unittests.device=1:0 -Dtornado.profiler=True -Dtornado.log.profiler=True -Dtornado.tc.compileTimes=true -Dtornado.cuda.codecache.enable=False -Dtornado.compiler=$c" $k 2>&1 | grep "compile-times" >> $out/$c.log
  done
done
echo WARM DONE
