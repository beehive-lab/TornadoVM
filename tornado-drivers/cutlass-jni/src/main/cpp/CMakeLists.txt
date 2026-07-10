cmake_minimum_required (VERSION 3.18)

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

project (TornadoCutlass CXX CUDA)

find_package(JNI REQUIRED)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CUDA_STANDARD 17)
set(CMAKE_CUDA_STANDARD_REQUIRED ON)

# Target architecture(s) for the pre-instantiated CUTLASS kernels. The kernels
# use the Sm80 arch tag (Ampere tensor ops), so any sm_80+ device works; the
# default compiles for the build machine's common cases. Override with
# -DCMAKE_CUDA_ARCHITECTURES or the CUDA_ARCH environment variable.
if(NOT DEFINED CMAKE_CUDA_ARCHITECTURES OR CMAKE_CUDA_ARCHITECTURES STREQUAL "")
    if(DEFINED ENV{CUDA_ARCH})
        set(CMAKE_CUDA_ARCHITECTURES $ENV{CUDA_ARCH})
    else()
        set(CMAKE_CUDA_ARCHITECTURES 89)
    endif()
endif()

# Header-only CUTLASS (NVIDIA template library for GEMM). Populate the sources
# but do NOT add_subdirectory — we only need its include/ directories.
include(FetchContent)
FetchContent_Declare(
    cutlass
    GIT_REPOSITORY https://github.com/NVIDIA/cutlass.git
    GIT_TAG v3.5.1
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(cutlass)
if(NOT cutlass_POPULATED)
    FetchContent_Populate(cutlass)
endif()

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC")
set(CMAKE_CUDA_FLAGS "${CMAKE_CUDA_FLAGS} -O3 --expt-relaxed-constexpr -Xcompiler=-fPIC -Xcompiler=-Wno-deprecated-declarations")

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
