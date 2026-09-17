#!/usr/bin/env bash
# Builds libtornado-cudf.so, the extern "C" shim the tornado-cudf module binds.
#
# Deliberately not part of `make BACKEND=cuda`. Building it needs RAPIDS libcudf, which almost
# nobody has installed, and a TornadoVM build must not start depending on it. Without the shim the
# tornado-cudf module still loads and reports itself unavailable, exactly as cuSPARSE does on a
# machine with no CUDA toolkit.
#
#   CUDF_HOME=/path/to/libcudf ./build.sh [output-dir]
#
# CUDF_HOME must contain include/ and lib64/ (or lib/). The quickest way to get one without
# building cuDF from source is the RAPIDS wheel, which ships both:
#
#   python3 -m venv /tmp/cudfenv
#   /tmp/cudfenv/bin/pip install --extra-index-url=https://pypi.nvidia.com libcudf-cu12
#   CUDF_HOME=/tmp/cudfenv/lib/python3.*/site-packages/libcudf ./build.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-${HERE}}"

if [[ -z "${CUDF_HOME:-}" ]]; then
    echo "CUDF_HOME must point at a libcudf install containing include/ and lib64/" >&2
    exit 1
fi

CUDF_INCLUDE="${CUDF_HOME}/include"
# librmm ships as its own wheel; point at it when it is not beside libcudf.
RMM_INCLUDE="${RMM_INCLUDE:-${CUDF_HOME}/../librmm/include}"
[[ -d "${RMM_INCLUDE}/rmm" ]] || RMM_INCLUDE=""
CUDF_LIB="${CUDF_HOME}/lib64"
[[ -d "${CUDF_LIB}" ]] || CUDF_LIB="${CUDF_HOME}/lib"

if [[ ! -d "${CUDF_INCLUDE}/cudf" || ! -f "${CUDF_LIB}/libcudf.so" ]]; then
    echo "no libcudf under ${CUDF_HOME} (looked for include/cudf and lib64/libcudf.so)" >&2
    exit 1
fi

NVCC="${NVCC:-${CUDA_HOME:-/usr/local/cuda}/bin/nvcc}"
# sm_70 upward covers every card cuDF itself supports; -arch=native would pin the build to the
# machine that made it, which is wrong for something installed next to a TornadoVM SDK.
ARCHS="${CUDF_ARCHS:--gencode arch=compute_70,code=sm_70 -gencode arch=compute_80,code=sm_80 -gencode arch=compute_90,code=sm_90}"

# include/rapids carries the CCCL that this libcudf was built against. The one in the CUDA
# toolkit is usually older, and RMM refuses to compile against it -- "RMM requires CCCL version
# 3.3 or newer" -- so the vendored one has to come first.
set -x
"${NVCC}" -O3 -std=c++20 --shared -Xcompiler -fPIC ${ARCHS} \
    -I"${CUDF_INCLUDE}" \
    -I"${CUDF_INCLUDE}/rapids" \
    ${RMM_INCLUDE:+-I"${RMM_INCLUDE}"} \
    -o "${OUT}/libtornado-cudf.so" \
    "${HERE}/tornado_cudf.cu" \
    -L"${CUDF_LIB}" -lcudf -lcudart
set +x

echo "built ${OUT}/libtornado-cudf.so"
echo "put its directory on LD_LIBRARY_PATH, or copy it next to the TornadoVM SDK's lib/"
