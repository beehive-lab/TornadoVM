#!/usr/bin/env bash
# Runs HAT's examples on TornadoVM devices through TornadoHatBackend and reports one line each.
# Validation is the example's own (--check against sequential Java), or a diff against HAT's own
# CUDA backend (mandel, squares). Usage: ./hat-sweep.sh [device ...]   (default: 1:0 0:0 0:1)
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
H=$HAT_BUILD
OUT=${OUT:-build/hat-sweep}; mkdir -p "$OUT"
DEVICES=("$@"); [[ ${#DEVICES[@]} -eq 0 ]] && DEVICES=(1:0 0:0 0:1)

reference() {   # HAT's own CUDA (FFI) backend, for examples without a --check
    local jar=$1 main=$2
    [[ -f $OUT/$jar.hat-cuda ]] || java --enable-preview --add-modules=jdk.incubator.code --enable-native-access=ALL-UNNAMED \
        -Djava.library.path=$H -Dheadless=true \
        -cp $H/hat-backend-ffi-cuda-1.0.jar:$H/hat-backend-ffi-shared-1.0.jar:$H/hat-core-1.0.jar:$H/hat-optkl-1.0.jar:$H/hat-example-$jar-1.0.jar:$H/hat-example-shared-1.0.jar \
        $main 2>/dev/null | grep -v '^\s*$' > $OUT/$jar.hat-cuda
}

run() {   # name jar main mode args...
    local name=$1 jar=$2 main=$3 mode=$4; shift 4
    local log=$OUT/$name.$DEV.log
    timeout 600 ./run.sh -d $DEV $H/hat-example-$jar-1.0.jar:$H/hat-example-shared-1.0.jar $main "$@" > $log 2>&1
    local rc=$? verdict
    if [[ $mode == check ]]; then
        if [[ $rc -eq 0 ]] && grep -qiE "is correct" $log && ! grep -qiE "incorrect|is wrong|not correct" $log; then verdict=PASS; else verdict=FAIL; fi
    else
        reference $jar $main
        if [[ $rc -eq 0 ]] && diff -q <(grep -v '^WARNING\|^\s*$' $log | tail -n $(wc -l < $OUT/$jar.hat-cuda)) $OUT/$jar.hat-cuda >/dev/null; then verdict=PASS; else verdict=FAIL; fi
    fi
    local why=""
    [[ $verdict == FAIL ]] && why=$( (sed "s/\x1b\[[0-9;]*m//g" $log | grep -oE "TC-E[0-9]+ [^\"]*" ; sed "s/\x1b\[[0-9;]*m//g" $log | grep -oE "[A-Za-z.]*Exception: [^\"]*") | head -1 | cut -c1-160)
    printf '%-6s %-28s %-4s %s\n' "$DEV" "$name" "$verdict" "$why"
}

for DEV in "${DEVICES[@]}"; do
    run squares squares squares.Main diff
    run mandel mandel mandel.Main diff
    for k in MT 1D 1DFC 2D 2DLI 2DTILING 2DREGISTERTILING 2DREGISTERTILING_V 2DLIF16 2DREGISTERTILING_FP16; do
        run matmul-$k matmul matmul.Main check --kernel=$k --check --size=256 --iterations=2
    done
    # Half precision with strict FP: no multiply-add contraction before rounding to half (OpenCL defaults to -cl-mad-enable)
    TC_JVM=-Dtornado.hat.strictFP=true run matmul-FP16-strictFP matmul matmul.Main check --kernel=2DREGISTERTILING_FP16 --check --size=256 --iterations=2
    run dft dft dft.Main check --check --size=1024 --iterations=2
    run tensors tensors tensors.Main check --check --iterations=2
done
