cmake_minimum_required (VERSION 3.6)
project (TornadoCusparse)

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
find_path(CUSPARSE_INCLUDE_DIR cusparse.h
    HINTS $ENV{CUDA_PATH}/include /usr/local/cuda/include ${CUDAToolkit_INCLUDE_DIRS})
find_library(CUDART_LIB cudart
    HINTS $ENV{CUDA_PATH}/lib64 /usr/local/cuda/lib64 ${CUDAToolkit_LIBRARY_DIR} /usr/lib/x86_64-linux-gnu)
# Prefer the CUDA toolkit's libcusparse (12.x) over an older system one.
find_library(CUSPARSE_LIB cusparse
    HINTS $ENV{CUDA_PATH}/lib64 /usr/local/cuda/lib64 ${CUDAToolkit_LIBRARY_DIR}
    NO_DEFAULT_PATH)
if(NOT CUSPARSE_LIB)
    find_library(CUSPARSE_LIB cusparse HINTS /usr/lib/x86_64-linux-gnu)
endif()

# cuSPARSE ships with the CUDA toolkit, so it is normally present. Guard anyway:
# if the header/lib cannot be found, skip building the native library (build
# still succeeds) and CUSPARSE tasks report UNSUPPORTED at runtime.
if(NOT CUSPARSE_INCLUDE_DIR OR NOT CUSPARSE_LIB)
    message(WARNING "TornadoVM cuSPARSE: cusparse.h/libcusparse not found. Skipping tornado-cusparse native library; CUSPARSE tasks report UNSUPPORTED.")
    return()
endif()

include_directories(source/ ${JNI_INCLUDE_DIRS} ${CUSPARSE_INCLUDE_DIR})
if(CUDA_RT_INCLUDE_DIR)
    include_directories(${CUDA_RT_INCLUDE_DIR})
endif()

file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-cusparse SHARED ${SOURCE_FILES})

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cusparse PROPERTIES PREFIX "")
endif()

get_filename_component(CUSPARSE_LIB_DIR ${CUSPARSE_LIB} DIRECTORY)
set_target_properties(tornado-cusparse PROPERTIES
    BUILD_RPATH ${CUSPARSE_LIB_DIR}
    INSTALL_RPATH ${CUSPARSE_LIB_DIR})

target_link_libraries(tornado-cusparse ${JNI_LIB_DIRS} ${CUSPARSE_LIB} ${CUDART_LIB})
message(STATUS "TornadoVM cuSPARSE: include=${CUSPARSE_INCLUDE_DIR}; lib=${CUSPARSE_LIB}")
