cmake_minimum_required (VERSION 3.18)

# See the matching note in cuda-jni/CMakeLists.txt: must be set before
# project() to affect CMAKE_MSVC_RUNTIME_LIBRARY below.
cmake_policy(SET CMP0091 NEW)

# Prefer the /usr/local/cuda toolkit so the shim is compiled by the same toolkit
# the CUDA backend JIT uses (see the matching note in
# cuda-jni/src/main/cpp/CMakeLists.txt).
#
# Expressed through CUDACXX rather than by setting CMAKE_CUDA_COMPILER, which
# would defeat the check below: check_language() skips its probe entirely when
# CMAKE_CUDA_COMPILER is already defined, and an nvcc that cannot run would then
# reach enable_language() and abort. CUDACXX steers the probe instead of
# bypassing it.
if(NOT DEFINED ENV{CUDACXX})
    if(DEFINED ENV{CUDA_PATH} AND EXISTS "$ENV{CUDA_PATH}/bin/nvcc")
        set(ENV{CUDACXX} "$ENV{CUDA_PATH}/bin/nvcc")
    elseif(EXISTS "/usr/local/cuda/bin/nvcc")
        set(ENV{CUDACXX} "/usr/local/cuda/bin/nvcc")
    endif()
endif()

project (TornadoCudf CXX)

# libcudf has no Windows build -- RAPIDS ships Linux packages only -- so there is
# nothing to look for on Windows and a find_path failure there would be reported
# as a missing install rather than an unsupported platform.
if(CMAKE_HOST_WIN32)
    message(WARNING "TornadoVM cuDF: RAPIDS libcudf has no Windows build. Skipping the tornado-cudf native library (cuDF tasks will report UNSUPPORTED at runtime).")
    return()
endif()

# Enable CUDA only if nvcc can actually run, rather than naming it in project()
# and aborting the whole build when it cannot. cutlass-jni takes the shorter
# route and pays for it: `project(... CXX CUDA)` fails hard before its own
# toolkit-version guard is ever reached, so an unusable nvcc -- an unsupported
# host gcc, say -- breaks the CUDA build instead of skipping one optional
# library. check_language() runs the same compiler probe and reports rather
# than aborting.
include(CheckLanguage)
check_language(CUDA)
if(NOT CMAKE_CUDA_COMPILER)
    message(WARNING "TornadoVM cuDF: no usable CUDA compiler. Skipping the tornado-cudf native library (cuDF tasks will report UNSUPPORTED at runtime).")
    return()
endif()
enable_language(CUDA)

# cuDF requires CUDA 12+. Rather than break the whole CUDA build on an older
# toolkit, skip the native library entirely: the FFM lookup in CudfNativeLib
# then finds nothing and the provider declines. Mirrors the CUDA-version guard
# in cutlass-jni/CMakeLists.txt.
if(CMAKE_CUDA_COMPILER_VERSION VERSION_LESS 12.0)
    message(WARNING "TornadoVM cuDF: nvcc ${CMAKE_CUDA_COMPILER_VERSION} is older than CUDA 12.0; cuDF requires CUDA 12+. Skipping the tornado-cudf native library (cuDF tasks will report UNSUPPORTED at runtime).")
    return()
endif()

# The shim contains no __global__ kernels -- every kernel it runs lives inside
# libcudf, already compiled. nvcc is needed for the headers (RMM and CCCL
# annotate host code with __host__ __device__), not to emit device code, so the
# architecture list only has to be one the toolkit accepts; it is read when the
# target below is created. sm_70 is the floor cuDF supports, but CUDA 13 dropped
# Volta and rejects compute_70 outright. -arch=native would pin the build to the
# machine that made it, which is wrong for something installed next to a
# TornadoVM SDK. Override with CUDA_ARCH as the cutlass-jni build does.
if(DEFINED ENV{CUDA_ARCH})
    set(CMAKE_CUDA_ARCHITECTURES $ENV{CUDA_ARCH})
elseif(CMAKE_CUDA_COMPILER_VERSION VERSION_LESS 13.0)
    set(CMAKE_CUDA_ARCHITECTURES 70-real 80-real 90-virtual)
else()
    set(CMAKE_CUDA_ARCHITECTURES 75-real 80-real 90-virtual)
endif()

# libcudf is a binary distribution, so it is found rather than fetched -- the
# cutlass-jni and cudnn-jni builds fetch CUTLASS and cudnn-frontend by pinned tag
# because those are header-only repositories. RMM and CCCL are header-only and
# could be fetched the same way, but they must match the libcudf that is
# installed: RAPIDS versions all three together and a mismatched CCCL fails to
# compile against RMM. So they are taken from the install too, and the whole
# module is skipped if any of them is missing.
#
# CUDF_HOME points at a prefix containing include/ and lib64/ (or lib/). Both
# layouts RAPIDS ships are covered: a conda prefix, where everything is merged
# under $CONDA_PREFIX, and the pip wheels, where libcudf and librmm install as
# sibling site-packages directories.
find_path(CUDF_INCLUDE_DIR cudf/table/table_view.hpp
    HINTS
        $ENV{CUDF_HOME}/include
        $ENV{CONDA_PREFIX}/include
        /usr/local/include
)

find_library(CUDF_LIB cudf
    HINTS
        $ENV{CUDF_HOME}/lib64
        $ENV{CUDF_HOME}/lib
        $ENV{CONDA_PREFIX}/lib
        /usr/local/lib64
        /usr/local/lib
)

if(NOT CUDF_INCLUDE_DIR OR NOT CUDF_LIB)
    message(WARNING "TornadoVM cuDF: no RAPIDS libcudf found (looked for cudf/table/table_view.hpp and libcudf under $ENV{CUDF_HOME}, $ENV{CONDA_PREFIX} and /usr/local). Skipping the tornado-cudf native library (cuDF tasks will report UNSUPPORTED at runtime). Set CUDF_HOME to enable it.")
    return()
endif()

# librmm ships as its own wheel, so its headers are not always under the same
# prefix as libcudf's.
find_path(RMM_INCLUDE_DIR rmm/cuda_stream_view.hpp
    HINTS
        $ENV{RMM_HOME}/include
        ${CUDF_INCLUDE_DIR}
        $ENV{CUDF_HOME}/../librmm/include
        $ENV{CONDA_PREFIX}/include
)

if(NOT RMM_INCLUDE_DIR)
    message(WARNING "TornadoVM cuDF: libcudf was found at ${CUDF_LIB} but RMM's headers were not (looked for rmm/cuda_stream_view.hpp). Skipping the tornado-cudf native library. Set RMM_HOME to the librmm prefix.")
    return()
endif()

# include/rapids carries the CCCL this libcudf was built against. The copy in
# the CUDA toolkit is usually older and RMM refuses to compile against it --
# "RMM requires CCCL version 3.3 or newer" -- so the vendored one has to be
# searched first. Absent in a conda install, where CCCL is merged into include/.
set(CUDF_RAPIDS_INCLUDE_DIR "")
if(EXISTS "${CUDF_INCLUDE_DIR}/rapids/cuda/std/version")
    set(CUDF_RAPIDS_INCLUDE_DIR "${CUDF_INCLUDE_DIR}/rapids")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CUDA_STANDARD 20)
set(CMAKE_CUDA_STANDARD_REQUIRED ON)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC")
set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -O3 --expt-relaxed-constexpr -Xcompiler=-fPIC -Xcompiler=-Wno-deprecated-declarations")

add_library(tornado-cudf SHARED source/tornado_cudf.cu)

# The vendored CCCL first, so it wins over the toolkit's.
target_include_directories(tornado-cudf PRIVATE
    ${CUDF_RAPIDS_INCLUDE_DIR}
    ${CUDF_INCLUDE_DIR}
    ${RMM_INCLUDE_DIR}
    source/
)

find_package(CUDAToolkit QUIET)

find_library(CUDART_LIB cudart
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

if(CUDART_LIB)
    target_link_libraries(tornado-cudf ${CUDF_LIB} ${CUDART_LIB})
else()
    message(WARNING "Could not locate libcudart via find_library; falling back to -lcudart")
    target_link_libraries(tornado-cudf ${CUDF_LIB} "-lcudart")
endif()

message(STATUS "TornadoVM cuDF: arch=${CMAKE_CUDA_ARCHITECTURES}; nvcc=${CMAKE_CUDA_COMPILER}; libcudf=${CUDF_LIB}; rmm=${RMM_INCLUDE_DIR}; cccl=${CUDF_RAPIDS_INCLUDE_DIR}")
