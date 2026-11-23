#!/usr/bin/env bash
# Profiles a HAT program or a TornadoVM program run through tornado-babylon.
#
#   ./profile.sh jfr  [-d dev] <classpath> <main> [args...]   JDK Flight Recorder: HAT dispatches, code-model
#                                                             translations, CPU samples; summary printed
#   ./profile.sh nsys [-d dev] <classpath> <main> [args...]   Nsight Systems: CUDA API and GPU kernel tables
#   ./profile.sh ncu  [-d dev] <classpath> <main> [args...]   Nsight Compute: duration, throughput, occupancy,
#                                                             registers of each kernel's launches (NCU_KERNEL=regex
#                                                             to select, NCU_COUNT=n launches, default 20)
#
# Recordings go to build/profile/<main>.<tool>.*. NSYS / NCU override the tools' paths.
set -euo pipefail
cd "$(dirname "$0")"
TOOL=${1:?usage: profile.sh jfr|nsys|ncu [-d dev] <classpath> <main> [args...]}
shift
DEVICE=()
if [[ "${1:-}" == "-d" ]]; then DEVICE=(-d "$2"); shift 2; fi
CPATH=$1
MAIN=$2
shift 2
OUT=build/profile/$MAIN
mkdir -p build/profile
NSYS=${NSYS:-$(command -v nsys || echo /usr/local/cuda/bin/nsys)}
NCU=${NCU:-$(command -v ncu || echo /usr/local/cuda/bin/ncu)}
case "$TOOL" in
    jfr)
        TC_JVM="${TC_JVM:-} -XX:StartFlightRecording=filename=$PWD/$OUT.jfr,settings=$PWD/tools/profile.jfc" \
            ./run.sh "${DEVICE[@]}" "$CPATH" "$MAIN" "$@"
        python3 tools/jfr-summary.py "$OUT.jfr"
        ;;
    nsys)
        "$NSYS" profile --trace=cuda,nvtx,osrt --sample=none --cpuctxsw=none -f true -o "$OUT.nsys" \
            ./run.sh "${DEVICE[@]}" "$CPATH" "$MAIN" "$@"
        "$NSYS" stats --force-export=true --report cuda_gpu_kern_sum --report cuda_api_sum "$OUT.nsys.nsys-rep"
        ;;
    ncu)
        "$NCU" --target-processes all ${NCU_KERNEL:+-k "regex:$NCU_KERNEL"} -c "${NCU_COUNT:-20}" \
            --metrics gpu__time_duration.sum,sm__throughput.avg.pct_of_peak_sustained_elapsed,dram__throughput.avg.pct_of_peak_sustained_elapsed,sm__warps_active.avg.pct_of_peak_sustained_active,launch__registers_per_thread \
            --csv --log-file "$OUT.ncu.csv" ./run.sh "${DEVICE[@]}" "$CPATH" "$MAIN" "$@"
        python3 tools/ncu-summary.py "$OUT.ncu.csv"
        ;;
    *)
        echo "unknown tool $TOOL: jfr, nsys or ncu" >&2
        exit 2
        ;;
esac
