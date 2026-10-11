#!/usr/bin/env bash
# Cold TC compile, fresh JVM per run: prints "<label> <kernel> <tc ms> <jvm ms>". Usage: ab.sh label cpfile [java flags...]
label=$1; cp=$(cat $2); shift 2
B=uk.ac.manchester.tornado.benchmarks
for k in "$B.ComputeKernels blackscholes" "$B.ComputeKernels nBody" "$B.LinearAlgebraArrays saxpy" "$B.LinearAlgebraArrays sgemm"; do
  for r in $(seq 1 10); do
    out=$($JAVA_HOME/bin/java "$@" -cp "$cp:$HOME/tc-perf/coldstart/harness.jar" Cold $k 1 2>/dev/null)
    t=$(echo "$out" | head -1 | sed -E 's/.*ok=([a-z]+).*tc=([0-9.]+) ms.*/\1 \2/')
    j=$(echo "$out" | grep jvm-to-done | sed -E 's/jvm-to-done ([0-9.]+) ms/\1/')
    echo "$label ${k##* } $t $j"
  done
done
