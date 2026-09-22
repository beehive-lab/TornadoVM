cmake_minimum_required (VERSION 3.18)

# See the matching note in cuda-jni/CMakeLists.txt: must be set before
# project() to affect CMAKE_MSVC_RUNTIME_LIBRARY below.
cmake_policy(SET CMP0091 NEW)

project (TornadoCudf CXX)

# No CUDA language. The shim declares no kernel of its own -- every kernel it runs
# is already compiled inside libcudf -- and touches the CUDA runtime only for
# stream copies and a synchronise, so a host compiler builds it and nvcc is never
# invoked. Same shape as cudnn-jni, which is also CXX-only.

# libcudf has no Windows build -- RAPIDS ships Linux packages only -- so there is
# nothing to look for on Windows and a find_path failure there would be reported
# as a missing install rather than an unsupported platform.
if(CMAKE_HOST_WIN32)
    message(WARNING "TornadoVM cuDF: RAPIDS libcudf has no Windows build. Skipping the tornado-cudf native library (cuDF tasks will report UNSUPPORTED at runtime).")
    return()
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

# cuda_runtime.h and libcudart, for the stream copies. Prefer $CUDA_PATH and
# /usr/local/cuda over the CMake-discovered toolkit so headers and library come
# from the same toolkit the CUDA backend JIT uses (see the matching note in
# cuda-jni/src/main/cpp/CMakeLists.txt).
find_package(CUDAToolkit QUIET)

find_path(CUDART_INCLUDE_DIR cuda_runtime.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        /usr/include
)

find_library(CUDART_LIB cudart
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

if(NOT CUDART_INCLUDE_DIR OR NOT CUDART_LIB)
    message(WARNING "TornadoVM cuDF: libcudf was found but the CUDA runtime was not (looked for cuda_runtime.h and libcudart). Skipping the tornado-cudf native library.")
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

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC -Wno-deprecated-declarations")

add_library(tornado-cudf SHARED source/tornado_cudf.cpp)

# The vendored CCCL first, so it wins over the toolkit's.
target_include_directories(tornado-cudf PRIVATE
    ${CUDF_RAPIDS_INCLUDE_DIR}
    ${CUDF_INCLUDE_DIR}
    ${RMM_INCLUDE_DIR}
    ${CUDART_INCLUDE_DIR}
    source/
)

target_link_libraries(tornado-cudf ${CUDF_LIB} ${CUDART_LIB})

message(STATUS "TornadoVM cuDF: libcudf=${CUDF_LIB}; rmm=${RMM_INCLUDE_DIR}; cccl=${CUDF_RAPIDS_INCLUDE_DIR}; cudart=${CUDART_LIB}")
