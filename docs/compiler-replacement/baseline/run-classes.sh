#!/bin/bash
# Run a list of test classes under TC on CUDA 1:0. Usage: run-classes.sh <label> <fallback true|false> <class-list-file>
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
log=~/tc-baseline/classes-$1.log; : > $log
for c in $(cat $3); do
  echo "tornado-test --params \"$c\"" >> $log
  timeout 900 tornado-test --verbose -J"-Dtornado.unittests.device=1:0 -Dtornado.compiler=tc -Dtornado.compiler.fallback=$2 -Dtornado.tc.stats=true" $c >> $log 2>&1
done
echo DONE >> $log
