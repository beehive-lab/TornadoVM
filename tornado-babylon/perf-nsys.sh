#!/usr/bin/env bash
# Kernel-only time (nsys cuda_gpu_kern_sum, mean per launch) of HAT's matmul kernels compiled by TC on
# TornadoVM CUDA vs HAT's own CUDA backend. Usage: ./perf-nsys.sh [size] [iterations]  -> build/nsys/kernels.json
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
H=$HAT_BUILD; SIZE=${1:-1024}; IT=${2:-30}; OUT=build/nsys; mkdir -p $OUT
NSYS=${NSYS:-nsys}
mean_us() {   # report -> mean ns of the kernel with the largest total time, in µs
    $NSYS stats --report cuda_gpu_kern_sum --format csv --force-export=true "$1" 2>/dev/null | python3 -c '
import sys, csv
rows = [x for x in csv.DictReader(l for l in sys.stdin if "," in l)]
rows.sort(key=lambda x: -float(x["Total Time (ns)"]))
r = rows[0]
print("%.1f %s %s" % (float(r["Avg (ns)"]) / 1e3, r["Instances"], r["Name"][:50]))'
}
for k in 1D 2D 2DLI 2DTILING 2DREGISTERTILING 2DREGISTERTILING_FP16; do
    rm -f table-results-*.csv
    $NSYS profile --trace=cuda -f true -o $OUT/tc-$k ./run.sh -d 1:0 $H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar matmul.Main --kernel=$k --size=$SIZE --iterations=$IT > $OUT/tc-$k.log 2>&1
    $NSYS profile --trace=cuda -f true -o $OUT/hat-$k java --enable-preview --add-modules=jdk.incubator.code --enable-native-access=ALL-UNNAMED -Djava.library.path=$H \
        -cp $H/hat-backend-ffi-cuda-1.0.jar:$H/hat-backend-ffi-shared-1.0.jar:$H/hat-core-1.0.jar:$H/hat-optkl-1.0.jar:$H/hat-example-matmul-1.0.jar:$H/hat-example-shared-1.0.jar \
        matmul.Main --kernel=$k --size=$SIZE --iterations=$IT > $OUT/hat-$k.log 2>&1
    echo "$k $(mean_us $OUT/tc-$k.nsys-rep) | $(mean_us $OUT/hat-$k.nsys-rep)"
done
rm -f table-results-*.csv
