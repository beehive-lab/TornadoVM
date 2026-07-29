cmake_minimum_required (VERSION 3.18)

# See the matching note in cuda-jni/CMakeLists.txt: must be set before
# project() to affect CMAKE_MSVC_RUNTIME_LIBRARY below.
cmake_policy(SET CMP0091 NEW)

# Prefer the /usr/local/cuda toolkit so the CUTLASS kernels are compiled by the
# same toolkit the CUDA backend JIT uses (see the matching note in
# cuda-jni/src/main/cpp/CMakeLists.txt).
if(NOT DEFINED CMAKE_CUDA_COMPILER)
    if(DEFINED ENV{CUDA_PATH} AND EXISTS "$ENV{CUDA_PATH}/bin/nvcc")
        set(CMAKE_CUDA_COMPILER "$ENV{CUDA_PATH}/bin/nvcc")
    elseif(EXISTS "/usr/local/cuda/bin/nvcc")
        set(CMAKE_CUDA_COMPILER "/usr/local/cuda/bin/nvcc")
    endif()
endif()

# Target architecture(s) for the pre-instantiated CUTLASS kernels. This MUST be
# set before project() enables the CUDA language, otherwise CMake picks its own
# default (sm_52 on this toolchain) - and the tensor-core MMA kernels compiled
# for compute_52 fail at launch with CUDA_ERROR_LAUNCH_FAILED (the SIMT FP32
# kernel would still run, masking the problem).
#
# Default "80-real;80-virtual": emit sm_80 SASS plus compute_80 PTX. The kernels
# use the Sm80 arch tag, and sm_80 SASS is binary-compatible with every later
# 8.x device (sm_86 Ampere, sm_89 Ada), while the embedded PTX JIT-compiles for
# sm_90 (Hopper) and beyond at load time. Crucially this compiles on any toolkit
# from CUDA 11.0 on - unlike sm_89/sm_90 targets, which the CI toolkit (older
# than CUDA 11.8) rejects with "Unsupported gpu architecture". Override with the
# CUDA_ARCH environment variable (e.g. CUDA_ARCH=89) to emit native SASS for a
# specific device.
if(DEFINED ENV{CUDA_ARCH})
    set(CMAKE_CUDA_ARCHITECTURES $ENV{CUDA_ARCH})
else()
    set(CMAKE_CUDA_ARCHITECTURES 80-real 80-virtual)
endif()

project (TornadoCutlass CXX CUDA)

find_package(JNI REQUIRED)

# CUTLASS 3.5.1 requires CUDA 12.0+. On older toolkits its headers fail to
# compile (e.g. nvcc 11.x + GCC 11 chokes on libstdc++ <functional> with
# "parameter packs not expanded"). Rather than break the whole CUDA build on
# such machines (some CI runners still ship CUDA 11.5), skip the native library
# entirely: System.loadLibrary("tornado-cutlass") then fails and the CUTLASS
# unit tests report UNSUPPORTED. Mirrors the CUDA-version guard used by the
# cudnn-jni SDPA path. Build on a CUDA 12+ toolkit to enable the provider.
if(CMAKE_CUDA_COMPILER_VERSION VERSION_LESS 12.0)
    message(WARNING "TornadoVM CUTLASS: nvcc ${CMAKE_CUDA_COMPILER_VERSION} is older than CUDA 12.0; CUTLASS requires CUDA 12+. Skipping the tornado-cutlass native library (CUTLASS tasks will report UNSUPPORTED at runtime).")
    return()
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CUDA_STANDARD 17)
set(CMAKE_CUDA_STANDARD_REQUIRED ON)

# Header-only CUTLASS (NVIDIA template library for GEMM) — we only need its
# include/ and tools/util/include/ directories (see target_include_directories
# below), never add_subdirectory.
#
# CUTLASS's docs/ tree contains thousands of Doxygen-generated HTML files with
# very long, template-mangled names (e.g. "classcutlass_1_1gemm_1_1warp_1_1..."
# past 100 chars). A plain FetchContent git clone checks out the *entire* tree
# including docs/, and on Windows the combined path (build dir + docs/<long
# name>.html) exceeds what git-for-windows can write there — confirmed even
# with GIT_CONFIG core.longpaths=true set, checkout still fails with
# "unable to create file docs/...: Filename too long". Rather than keep
# fighting the Windows path limit, avoid ever fetching docs/ at all: use git's
# sparse-checkout (cone mode) to pull only the two subtrees this build uses.
set(CUTLASS_SOURCE_DIR "${CMAKE_BINARY_DIR}/_deps/cutlass-src")
if(NOT EXISTS "${CUTLASS_SOURCE_DIR}/include/cutlass/cutlass.h")
    find_package(Git REQUIRED)
    file(REMOVE_RECURSE "${CUTLASS_SOURCE_DIR}")
    file(MAKE_DIRECTORY "${CUTLASS_SOURCE_DIR}")

    macro(cutlass_git)
        execute_process(
            COMMAND ${GIT_EXECUTABLE} ${ARGN}
            WORKING_DIRECTORY "${CUTLASS_SOURCE_DIR}"
            RESULT_VARIABLE _cutlass_git_rc
        )
        if(NOT _cutlass_git_rc EQUAL 0)
            message(FATAL_ERROR "CUTLASS sparse checkout failed: git ${ARGN}")
        endif()
    endmacro()

    cutlass_git(init -q)
    cutlass_git(remote add origin https://github.com/NVIDIA/cutlass.git)
    cutlass_git(sparse-checkout init --cone)
    cutlass_git(sparse-checkout set include tools/util/include)
    cutlass_git(fetch --depth 1 origin v3.5.1)
    cutlass_git(checkout FETCH_HEAD)
endif()
set(cutlass_SOURCE_DIR "${CUTLASS_SOURCE_DIR}")

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

# -fPIC is meaningless on Windows (no PIC/PIE distinction for DLLs) and, worse,
# nvcc forwards it to cl.exe via -Xcompiler, which does not understand GCC-style
# flags and floods the build with D9002 "ignoring unknown option" warnings for
# every CUTLASS header. On MSVC, statically link the CRT instead (matching
# cuda-jni/cublas-jni/cufft-jni/cudnn-jni) so tornado-cutlass.dll doesn't end up
# dynamically linked against msvcp140.dll/vcruntime140.dll while its siblings
# are statically linked.
if(MSVC)
	set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc")
	set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -O3 --expt-relaxed-constexpr")
else()
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC")
	set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -O3 --expt-relaxed-constexpr -Xcompiler=-fPIC -Xcompiler=-Wno-deprecated-declarations")
endif()

# GCC 15+ diagnoses invalid code inside uninstantiated template bodies
# (-Wtemplate-body, an error by default; GCC <= 14 never checked such bodies
# and has no such flag). CUTLASS 3.5.1's matrix.h calls a misspelled
# set_slice3x3 member from its rotation()/reflection() helpers; nothing here
# instantiates them, so disable just that diagnostic on the nvcc
# host-compiler pass.
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 15.0)
	set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -Xcompiler=-Wno-template-body")
endif()

# CUTLASS 3.5.1's cuda_host_adapter.hpp casts function pointers loaded via
# cudaGetDriverEntryPoint() to unversioned typedefs like
# PFN_cuTensorMapEncodeTiled/PFN_cuTensorMapEncodeIm2col for CUDA 12.0-12.4.
# CUDA 13.x's cudaTypedefs.h dropped those unversioned aliases (only the
# versioned PFN_..._v12000 typedefs remain), so nvcc 13.x fails with
# "identifier ... is undefined" merely parsing the (uninstantiated) template.
# CUTLASS_ENABLE_DIRECT_CUDA_DRIVER_CALL switches that wrapper to call the
# driver function directly instead of going through the missing typedef.
if(CMAKE_CUDA_COMPILER_VERSION VERSION_GREATER_EQUAL 13.0)
	set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -DCUTLASS_ENABLE_DIRECT_CUDA_DRIVER_CALL")
endif()

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
)

file(GLOB_RECURSE SOURCE_FILES "source/*.cu" "source/*.cpp")
add_library(tornado-cutlass SHARED ${SOURCE_FILES})

target_include_directories(tornado-cutlass PRIVATE
    ${cutlass_SOURCE_DIR}/include
    ${cutlass_SOURCE_DIR}/tools/util/include
)

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cutlass PROPERTIES PREFIX "")
endif()

find_library(CUDART_LIB cudart
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CMAKE_CUDA_TOOLKIT_INCLUDE_DIRECTORIES}/../lib64
        /usr/lib/x86_64-linux-gnu
)

if(CUDART_LIB)
    target_link_libraries(tornado-cutlass ${JNI_LIB_DIRS} ${CUDART_LIB})
else()
    message(WARNING "Could not locate libcudart via find_library; falling back to -lcudart")
    target_link_libraries(tornado-cutlass ${JNI_LIB_DIRS} "-lcudart")
endif()

message(STATUS "TornadoVM CUTLASS: arch=${CMAKE_CUDA_ARCHITECTURES}; nvcc=${CMAKE_CUDA_COMPILER}; cutlass=${cutlass_SOURCE_DIR}")
