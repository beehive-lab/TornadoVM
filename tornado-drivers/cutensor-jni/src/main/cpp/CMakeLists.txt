cmake_minimum_required (VERSION 3.6)
project (TornadoCutensor)

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

# cuTENSOR is a standalone NVIDIA library (not part of the CUDA toolkit). Locate
# it via $CUTENSOR_ROOT, a pip `cutensor-cu12` install, or ~/.local/cutensor. If
# it (or its header) cannot be found, skip building the native library entirely:
# System.loadLibrary("tornado-cutensor") then fails and the CUTENSOR unit tests
# report UNSUPPORTED. This keeps the CUDA build green on machines without
# cuTENSOR installed (e.g. CI), mirroring the cutlass-jni CUDA-version guard.
find_path(CUTENSOR_INCLUDE_DIR cutensor.h
    HINTS
        $ENV{CUTENSOR_ROOT}/include
        $ENV{HOME}/.local/cutensor/include
        /usr/local/cutensor/include
        /usr/include)
find_library(CUTENSOR_LIB cutensor
    HINTS
        $ENV{CUTENSOR_ROOT}/lib
        $ENV{HOME}/.local/cutensor/lib
        /usr/local/cutensor/lib
        /usr/lib/x86_64-linux-gnu)

if(NOT CUTENSOR_INCLUDE_DIR OR NOT CUTENSOR_LIB)
    message(WARNING "TornadoVM cuTENSOR: libcutensor/cutensor.h not found (set CUTENSOR_ROOT). Skipping the tornado-cutensor native library; CUTENSOR tasks will report UNSUPPORTED at runtime.")
    return()
endif()

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${CUTENSOR_INCLUDE_DIR}
)
if(CUDA_RT_INCLUDE_DIR)
    include_directories(${CUDA_RT_INCLUDE_DIR})
endif()

file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-cutensor SHARED ${SOURCE_FILES})

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cutensor PROPERTIES PREFIX "")
endif()

# Embed the cuTENSOR library directory as an rpath so libtornado-cutensor can
# resolve libcutensor.so at load time without LD_LIBRARY_PATH.
get_filename_component(CUTENSOR_LIB_DIR ${CUTENSOR_LIB} DIRECTORY)
set_target_properties(tornado-cutensor PROPERTIES
    BUILD_RPATH ${CUTENSOR_LIB_DIR}
    INSTALL_RPATH ${CUTENSOR_LIB_DIR})

target_link_libraries(tornado-cutensor ${JNI_LIB_DIRS} ${CUTENSOR_LIB} ${CUDART_LIB})
message(STATUS "TornadoVM cuTENSOR: include=${CUTENSOR_INCLUDE_DIR}; lib=${CUTENSOR_LIB}")
