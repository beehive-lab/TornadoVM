cmake_minimum_required (VERSION 3.6)
project (TornadoNccl)

find_package(JNI REQUIRED)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

if(MSVC)
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc")
else()
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fPIC -export-dynamic")
	add_definitions(${CMAKE_CXX_FLAGS})
endif()

find_package(CUDAToolkit QUIET)

find_path(CUDA_RT_INCLUDE_DIR cuda_runtime_api.h
    HINTS $ENV{CUDA_PATH}/include /usr/local/cuda/include ${CUDAToolkit_INCLUDE_DIRS})
find_library(CUDART_LIB cudart
    HINTS $ENV{CUDA_PATH}/lib64 /usr/local/cuda/lib64 ${CUDAToolkit_LIBRARY_DIR} /usr/lib/x86_64-linux-gnu)

# NCCL is a standalone NVIDIA library (multi-GPU collectives). Locate it via
# $NCCL_ROOT, a pip `nvidia-nccl-cu12` install, ~/.local/nccl, or the CUDA
# toolkit. If not found, skip building the native library (build still
# succeeds); the NCCL API then reports UNSUPPORTED at runtime.
find_path(NCCL_INCLUDE_DIR nccl.h
    HINTS
        $ENV{NCCL_ROOT}/include
        $ENV{HOME}/.local/nccl/include
        /usr/local/cuda/include
        /usr/include)
find_library(NCCL_LIB nccl
    HINTS
        $ENV{NCCL_ROOT}/lib
        $ENV{HOME}/.local/nccl/lib
        /usr/local/cuda/lib64
        /usr/lib/x86_64-linux-gnu)

if(NOT NCCL_INCLUDE_DIR OR NOT NCCL_LIB)
    message(WARNING "TornadoVM NCCL: nccl.h/libnccl not found (set NCCL_ROOT). Skipping tornado-nccl native library; NCCL collectives report UNSUPPORTED.")
    return()
endif()

include_directories(source/ ${JNI_INCLUDE_DIRS} ${NCCL_INCLUDE_DIR})
if(CUDA_RT_INCLUDE_DIR)
    include_directories(${CUDA_RT_INCLUDE_DIR})
endif()

file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-nccl SHARED ${SOURCE_FILES})

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-nccl PROPERTIES PREFIX "")
endif()

get_filename_component(NCCL_LIB_DIR ${NCCL_LIB} DIRECTORY)
set_target_properties(tornado-nccl PROPERTIES
    BUILD_RPATH ${NCCL_LIB_DIR}
    INSTALL_RPATH ${NCCL_LIB_DIR})

target_link_libraries(tornado-nccl ${JNI_LIB_DIRS} ${NCCL_LIB} ${CUDART_LIB})
message(STATUS "TornadoVM NCCL: include=${NCCL_INCLUDE_DIR}; lib=${NCCL_LIB}")
