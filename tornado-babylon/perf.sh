#!/usr/bin/env bash
# End-to-end time per Accelerator.compute (the examples' own timers) on TornadoVM (TC) vs HAT's own
# native backends, plus first-call (compile) time. Usage: ./perf.sh [size] [iterations]
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
H=$HAT_BUILD; SIZE=${1:-1024}; IT=${2:-50}
OUT=build/perf; mkdir -p $OUT
hat_native() {   # backend(cuda|opencl) jar main args...
    local be=$1 jar=$2 main=$3; shift 3
    java --enable-preview --add-modules=jdk.incubator.code --enable-native-access=ALL-UNNAMED -Djava.library.path=$H -Dheadless=true \
        -cp $H/hat-backend-ffi-$be-1.0.jar:$H/hat-backend-ffi-shared-1.0.jar:$H/hat-core-1.0.jar:$H/hat-optkl-1.0.jar:$H/hat-example-$jar-1.0.jar:$H/hat-example-shared-1.0.jar \
        $main "$@"
}
stats() {   # csv -> "first_ms median_ms" (median over the second half of the iterations)
    python3 - "$1" <<'PY'
import sys, statistics
v=[int(x) for x in open(sys.argv[1]).read().split()[1:] if x.strip().isdigit()]
print(f"{v[0]/1e6:.1f} {statistics.median(v[len(v)//2:])/1e6:.3f}" if v else "- -")
PY
}
printf '%-24s %-14s %10s %12s %s\n' kernel backend first_ms median_ms check
for k in 1D 2D 2DLI 2DTILING 2DREGISTERTILING 2DREGISTERTILING_FP16; do
    for be in tornado-cuda tornado-opencl hat-cuda hat-opencl; do
        log=$OUT/$k.$be.log; rm -f table-results-mxm-*.csv
        case $be in
            tornado-cuda) ./run.sh -d 1:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=$k --check --size=$SIZE --iterations=$IT > $log 2>&1 ;;
            tornado-opencl) TC_JVM=-Dtornado.hat.strictFP=true ./run.sh -d 0:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=$k --check --size=$SIZE --iterations=$IT > $log 2>&1 ;;
            hat-cuda) hat_native cuda matmul matmul.Main --kernel=$k --check --size=$SIZE --iterations=$IT > $log 2>&1 ;;
            hat-opencl) hat_native opencl matmul matmul.Main --kernel=$k --check --size=$SIZE --iterations=$IT > $log 2>&1 ;;
        esac
        csv=$(ls table-results-mxm-*.csv 2>/dev/null | head -1)
        check=$(grep -oE "is correct|is wrong" $log | sort -u | tr '\n' ' ')
        [[ -n $csv ]] && { mv $csv $OUT/$k.$be.csv; s=$(stats $OUT/$k.$be.csv); } || s="- -"
        printf '%-24s %-14s %10s %12s %s\n' $k $be $s "${check:-error}"
    done
done
