cmake_minimum_required (VERSION 3.15)

# See the matching note in cuda-jni/CMakeLists.txt: must be set before
# project() to affect CMAKE_MSVC_RUNTIME_LIBRARY below.
cmake_policy(SET CMP0091 NEW)

project (TornadoCuDnn)

find_package(JNI REQUIRED)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Header-only cudnn-frontend (NVIDIA-recommended interface to the cuDNN graph
# API), used for fused patterns such as SDPA / flash attention.
include(FetchContent)
FetchContent_Declare(
    cudnn_frontend
    GIT_REPOSITORY https://github.com/NVIDIA/cudnn-frontend.git
    GIT_TAG v1.25.0
    GIT_SHALLOW TRUE
)
# Header-only: populate the sources but do NOT add_subdirectory (the frontend's
# own CMakeLists requires cmake >= 3.23; we only need its include/ directory).
FetchContent_GetProperties(cudnn_frontend)
if(NOT cudnn_frontend_POPULATED)
    FetchContent_Populate(cudnn_frontend)
endif()

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

if(MSVC)
	set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
	if(NOT CMAKE_CXX_COMPILER_ID STREQUAL Clang)
		set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /MP")
	endif()
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc")
else()
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -export-dynamic")
	add_definitions(${CMAKE_CXX_FLAGS})
endif()

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${CPLUS_INCLUDE_PATH}
	$ENV{CPLUS_INCLUDE_PATH}
	$ENV{C_INCLUDE_PATH}
)

file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-cudnn SHARED ${SOURCE_FILES})

# Prefer $CUDA_PATH and /usr/local/cuda over the CMake-discovered toolkit so
# headers and library come from the same toolkit the CUDA backend JIT uses
# (see the matching note in cuda-jni/src/main/cpp/CMakeLists.txt).
find_package(CUDAToolkit QUIET)

# NVIDIA's Windows cuDNN 9.x installer no longer merges its headers/libs into
# the CUDA toolkit's own include/lib directories (the layout every HINTS list
# below otherwise assumes). Instead it installs under its own tree (the
# CUDNN_ROOT env var the installer sets, e.g. "C:/Program Files/NVIDIA/CUDNN/
# v9.x"), nesting headers/libs one level deeper under a subdirectory named for
# the CUDA major version they target (e.g. include/13.3, lib/13.3/x64). Glob
# for the subdirectory matching the CUDA toolkit in use so headers/libs come
# from the same CUDA version family.
if(DEFINED ENV{CUDNN_ROOT} AND CUDAToolkit_FOUND)
    file(GLOB CUDNN_ROOT_INCLUDE_DIRS "$ENV{CUDNN_ROOT}/include/${CUDAToolkit_VERSION_MAJOR}.*")
    file(GLOB CUDNN_ROOT_LIB_DIRS "$ENV{CUDNN_ROOT}/lib/${CUDAToolkit_VERSION_MAJOR}.*/x64")
endif()

find_path(CUDA_INCLUDE_DIR cudnn.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        ${CUDNN_ROOT_INCLUDE_DIRS}
        /usr/include
        /usr/include/x86_64-linux-gnu
        /usr/lib/cuda/include
)

# cudnn.h needs cuda toolkit headers (cudnn_version.h includes cuda_runtime.h)
find_path(CUDA_RT_INCLUDE_DIR cuda_runtime_api.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
)
if(CUDA_RT_INCLUDE_DIR)
    include_directories(${CUDA_RT_INCLUDE_DIR})
endif()

find_library(CUDNN_LIB cudnn
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        ${CUDNN_ROOT_LIB_DIRS}
        /usr/lib/x86_64-linux-gnu
)

if(CUDA_INCLUDE_DIR)
    include_directories(${CUDA_INCLUDE_DIR})
endif()

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cudnn PROPERTIES PREFIX "")
endif()

find_library(CUDART_LIB cudart
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

# cudnn-frontend's SDPA graph path transitively includes the experimental
# sm90/sm100 prefill engine headers (see the toolkit guard comment in
# tornado-cudnn-sdpa.cpp), which call into NVRTC directly since this
# FetchContent is header-only (cudnn-frontend's own CMakeLists, which would
# otherwise select its dlopen-based NVRTC shim, is never invoked).
find_library(NVRTC_LIB nvrtc
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

target_include_directories(tornado-cudnn PRIVATE ${cudnn_frontend_SOURCE_DIR}/include)

if(CUDNN_LIB AND CUDART_LIB AND NVRTC_LIB)
    target_link_libraries(tornado-cudnn ${JNI_LIB_DIRS} ${CUDNN_LIB} ${CUDART_LIB} ${NVRTC_LIB})
else()
    message(WARNING "Could not locate libcudnn/libcudart/libnvrtc via find_library; falling back to -lcudnn -lcudart -lnvrtc")
    target_link_libraries(tornado-cudnn ${JNI_LIB_DIRS} "-lcudnn -lcudart -lnvrtc")
endif()

message(STATUS "TornadoVM cuDNN: include=${CUDA_INCLUDE_DIR}; cudnn=${CUDNN_LIB}; nvrtc=${NVRTC_LIB}")
