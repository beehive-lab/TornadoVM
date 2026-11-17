cmake_minimum_required (VERSION 3.21)

# The project comes first, so that a skip below still leaves a project to build (an empty one);
# its compiler is chosen below, from ROCm, before CXX is enabled.
project (TornadoHipdf NONE)

# libtornado-hipdf: the cuDF shim (tornado-native-libs/cudf-jni/src/main/cpp/source, copied here by
# the module's pom) compiled unchanged against hipDF, AMD's HIP port of libcudf, for HIP devices.
# hipDF keeps cuDF's headers and C++ API and ships CUDA-named compatibility headers, so the only
# glue is in compat/: <cuda_runtime.h>, and the RMM memory-resource headers at the paths newer RMM
# releases use, which hipMM still keeps under rmm/mr/device/.
#
# Without hipDF the build skips this library with a warning; cuDF tasks then report UNSUPPORTED on
# HIP devices. With TORNADO_HIPDF_REQUIRED set, a skipped build fails instead.
macro(tornado_hipdf_skip reason)
    if(NOT "$ENV{TORNADO_HIPDF_REQUIRED}" STREQUAL "" AND NOT "$ENV{TORNADO_HIPDF_REQUIRED}" STREQUAL "0")
        message(FATAL_ERROR "TornadoVM hipDF: ${reason} (TORNADO_HIPDF_REQUIRED is set, so this fails the build.)")
    endif()
    message(WARNING "TornadoVM hipDF: ${reason} Skipping the tornado-hipdf native library (cuDF tasks will report UNSUPPORTED on HIP devices).")
    return()
endmacro()

if(CMAKE_HOST_WIN32)
    tornado_hipdf_skip("hipDF has no Windows build.")
endif()

set(ROCM_ROOT "")
foreach(variable ROCM_PATH HIP_PATH ROCM_HOME)
    if(NOT ROCM_ROOT AND DEFINED ENV{${variable}} AND EXISTS "$ENV{${variable}}")
        set(ROCM_ROOT "$ENV{${variable}}")
    endif()
endforeach()
if(NOT ROCM_ROOT AND EXISTS "/opt/rocm")
    set(ROCM_ROOT "/opt/rocm")
endif()
if(NOT ROCM_ROOT)
    tornado_hipdf_skip("no ROCm found (looked at ROCM_PATH, HIP_PATH, ROCM_HOME and /opt/rocm).")
endif()

# hipDF's headers include the HIP runtime headers, which need ROCm's clang.
if(NOT CMAKE_CXX_COMPILER)
    find_program(TORNADO_ROCM_CLANG clang++ HINTS "${ROCM_ROOT}/llvm/bin" "${ROCM_ROOT}/lib/llvm/bin" NO_DEFAULT_PATH)
    if(NOT TORNADO_ROCM_CLANG)
        tornado_hipdf_skip("no ROCm clang++ under ${ROCM_ROOT}.")
    endif()
    set(CMAKE_CXX_COMPILER "${TORNADO_ROCM_CLANG}")
endif()

enable_language(CXX)

# HIPDF_HOME is the libcudf prefix of a hipDF install: the libcudf directory of the amd-libhipdf
# wheel (whose librmm and rapids_logger wheels install beside it), or a conda prefix. Its
# dependencies (the CCCL port among them) come before ROCm's, which may carry other versions.
set(HIPDF_ROOTS "")
if(DEFINED ENV{HIPDF_HOME} AND NOT "$ENV{HIPDF_HOME}" STREQUAL "")
    list(APPEND HIPDF_ROOTS "$ENV{HIPDF_HOME}" "$ENV{HIPDF_HOME}/lib64/rapids" "$ENV{HIPDF_HOME}/../librmm" "$ENV{HIPDF_HOME}/../rapids_logger")
endif()
if(DEFINED ENV{CONDA_PREFIX})
    list(APPEND HIPDF_ROOTS "$ENV{CONDA_PREFIX}")
endif()
# Each root and its lib64/cmake and lib/cmake: the wheels keep their packages under lib64/cmake.
set(HIPDF_PREFIXES "")
foreach(root IN LISTS HIPDF_ROOTS)
    foreach(prefix "${root}" "${root}/lib64/cmake" "${root}/lib/cmake")
        if(EXISTS "${prefix}")
            list(APPEND HIPDF_PREFIXES "${prefix}")
        endif()
    endforeach()
endforeach()
list(APPEND CMAKE_PREFIX_PATH ${HIPDF_PREFIXES} "${ROCM_ROOT}")

find_path(HIPDF_CONFIG_DIR cudf-config.cmake PATH_SUFFIXES cudf lib64/cmake/cudf lib/cmake/cudf PATHS ${HIPDF_PREFIXES} "${ROCM_ROOT}" NO_DEFAULT_PATH)
if(NOT HIPDF_CONFIG_DIR)
    tornado_hipdf_skip("no hipDF found (looked for cudf-config.cmake under $ENV{HIPDF_HOME}, $ENV{CONDA_PREFIX} and ${ROCM_ROOT}). Set HIPDF_HOME to enable it.")
endif()
if(CMAKE_VERSION VERSION_LESS 3.30.4)
    tornado_hipdf_skip("hipDF's CMake package needs CMake 3.30.4 or newer (this is ${CMAKE_VERSION}).")
endif()
find_package(cudf CONFIG PATHS "${HIPDF_CONFIG_DIR}" NO_DEFAULT_PATH)
if(NOT cudf_FOUND)
    tornado_hipdf_skip("hipDF's CMake package at ${HIPDF_CONFIG_DIR} could not be loaded.")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

add_library(tornado-hipdf SHARED source/tornado_cudf.cpp source/glibc_compat.cpp)
target_include_directories(tornado-hipdf BEFORE PRIVATE compat)
target_compile_options(tornado-hipdf PRIVATE -O2 -Wno-deprecated-declarations)
target_link_libraries(tornado-hipdf PRIVATE cudf::cudf)

message(STATUS "TornadoVM hipDF: cudf ${cudf_VERSION} from ${HIPDF_CONFIG_DIR}; compiler=${CMAKE_CXX_COMPILER}")
