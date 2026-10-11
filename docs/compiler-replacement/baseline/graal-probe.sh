#!/usr/bin/env bash
# Counts the Graal and JVMCI classes the JVM loads while TornadoVM runs an application with TC
# (-Dtornado.compiler=tc, fallback off). The Graal-removal metric: it must reach 0 before Graal is
# deleted (docs/compiler-replacement/graal-removal.md). Exit status 1 while anything is loaded.
#   graal-probe.sh <classpath> <MainClass> [args...]      (TORNADOVM_HOME set: source setvars.sh)
set -uo pipefail
cp=$1; main=$2; shift 2
log=$(mktemp)
tornado --jvm="-Dtornado.compiler=tc -Dtornado.compiler.fallback=false -Xlog:class+load=info:file=$log" --classpath "$cp" "$main" "$@" > /dev/null 2>&1
total=$(grep -c 'source:' "$log")
graal=$(grep -c ' tornado\.graal\.' "$log")
jvmci=$(grep -c ' jdk\.vm\.ci\.' "$log")
graalvm=$(grep -c ' org\.graalvm\.' "$log")
tornado=$(grep -cE ' uk\.ac\.manchester\.tornado\.[a-z.]*\.graal\.' "$log")
tc=$(grep -c ' uk\.ac\.manchester\.tornado\.tc\.' "$log")
echo "classes $total: tornado.graal $graal, jdk.vm.ci $jvmci, org.graalvm $graalvm, TornadoVM graal packages $tornado (TC $tc)"
grep -oE ' uk\.ac\.manchester\.tornado\.[a-z.]*\.graal\.[a-z]+' "$log" | sed 's/ uk.ac.manchester.tornado.//' | sort | uniq -c | sort -rn | head -8
rm -f "$log"
[ $((graal + jvmci + graalvm + tornado)) -eq 0 ]
