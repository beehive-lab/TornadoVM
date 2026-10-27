#!/usr/bin/env bash
# Runs HAT's own unit tests (hat/tests, through HAT's test engine) on TornadoVM via TornadoHatBackend.
#   ./hat-tests.sh [-d <tornado device, e.g. 1:0>] [TestClass[#method] ...]     (default: every class)
# Results: build/hat-tests/<device>/<class>.log and build/hat-tests/<device>/summary.txt (one line per test).
# A class whose JVM dies (crash, timeout) is rerun one test method per JVM, so one crash costs one test.
set -uo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
DEVICE=1:0
if [[ "${1:-}" == "-d" ]]; then DEVICE=$2; shift 2; fi
TESTS_SRC=$BABYLON/hat/tests/src/main/java/hat/test
CLASSES=("$@")
if [[ ${#CLASSES[@]} -eq 0 ]]; then
  mapfile -t CLASSES < <(ls "$TESTS_SRC" | sed -n 's/^\(Test[A-Za-z0-9]*\)\.java$/\1/p' | sort)
fi
OUT=build/hat-tests/${DEVICE/:/-}
mkdir -p "$OUT"
ROOT=$PWD
ARGS=$ROOT/build/tornado.args
sed "s|\${TORNADOVM_HOME}|$TORNADOVM_HOME|g" "$TORNADOVM_HOME/tornado-argfile.template" > "$ARGS"
CP=$ROOT/build/tornado-babylon.jar:$HAT_BUILD/hat-optkl-1.0.jar:$HAT_BUILD/hat-core-1.0.jar:$HAT_BUILD/hat-tests-1.0.jar

run() { # run <test spec> <log>
  (cd "$OUT" && timeout "${HAT_TEST_TIMEOUT:-600}" java @"$ARGS" --enable-preview --add-modules=jdk.incubator.code \
    -Dtornado.recover.bailout=False -Dhat.t.device="$DEVICE" ${TC_JVM:-} \
    -cp "$CP" hat.test.engine.HATTestEngine "hat.test.$1") > "$2" 2>&1
}
methods() { sed -n '/@HatTest/,/(/p' "$TESTS_SRC/$1.java" | sed -n 's/.*public[^(]* \([A-Za-z0-9_]*\)(.*/\1/p'; }
strip() { sed 's/\x1b\[[0-9;]*m//g'; }

: > "$OUT/summary.txt"
for spec in "${CLASSES[@]}"; do
  cls=${spec%%#*}
  rm -f "$OUT/test_report.txt"
  run "$spec" "$OUT/$cls.log"; rc=$?
  if [[ $rc -eq 0 ]] && grep -q "passed: " "$OUT/$cls.log"; then
    strip < "$OUT/$cls.log" | sed -n 's/^ *Testing: #\([A-Za-z0-9_]*\) *\.* *\[\(.*\)\].*/\1 \2/p' | sed "s/^/$cls#/" >> "$OUT/summary.txt"
  else
    echo "$cls: JVM exit $rc, rerunning one method per JVM" >&2
    for m in $(methods "$cls"); do
      [[ "$spec" == *#* && "$spec" != "$cls#$m" ]] && continue
      run "$cls#$m" "$OUT/$cls#$m.log"; mrc=$?
      r=$(strip < "$OUT/$cls#$m.log" | sed -n 's/^ *Testing: #[A-Za-z0-9_]* *\.* *\[\(.*\)\].*/\1/p' | head -1)
      echo "$cls#$m ${r:-crashed (exit $mrc)}" >> "$OUT/summary.txt"
    done
  fi
done
echo "== $DEVICE: $(grep -c ' passed$' "$OUT/summary.txt") passed of $(wc -l < "$OUT/summary.txt")"
awk '{ $1=""; print }' "$OUT/summary.txt" | sort | uniq -c | sort -rn
