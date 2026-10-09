#!/bin/bash
# C5b vertical slice: run the family classes under TC on CUDA. $1 = label, $2 = fallback true|false
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
log=~/tc-baseline/c5b-$1.log; : > $log
for c in $(cat /tmp/claude-1005/-home-michalis/e42565b8-e271-4e3e-b85c-56088cc77878/scratchpad/c5b-classes.txt); do
  echo "tornado-test --params \"$c\"" >> $log
  timeout 600 tornado-test --verbose -J"-Dtornado.unittests.device=1:0 -Dtornado.compiler=tc -Dtornado.compiler.fallback=$2 -Dtornado.tc.stats=true" $c >> $log 2>&1
done
echo DONE >> $log
