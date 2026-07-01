cmake_minimum_required (VERSION 3.6)
project (TornadoCuBlas)

find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

if(MSVC)
	string(REPLACE "/MDd" "/MTd" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
	string(REPLACE "/MD" "/MT" CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE}")
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
add_library(tornado-cublas SHARED ${SOURCE_FILES})

# cuBLAS from the CUDA toolkit. Prefer the CMake-discovered toolkit but fall
# back to common system locations (same strategy as cuda-jni).
find_package(CUDAToolkit QUIET)

# Prefer $CUDA_PATH and /usr/local/cuda over the CMake-discovered toolkit so
# headers and library come from the same toolkit the CUDA backend JIT uses
# (see the matching note in cuda-jni/src/main/cpp/CMakeLists.txt).
find_path(CUDA_INCLUDE_DIR cublas_v2.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        /usr/include
        /usr/lib/cuda/include
)

find_library(CUBLAS_LIB cublas
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

# CUDA runtime for workspace allocation (cudaMalloc/cudaFree)
find_library(CUDART_LIB cudart
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

if(CUDA_INCLUDE_DIR)
    include_directories(${CUDA_INCLUDE_DIR})
endif()

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cublas PROPERTIES PREFIX "")
endif()

if(CUBLAS_LIB AND CUDART_LIB)
    target_link_libraries(tornado-cublas ${JNI_LIB_DIRS} ${CUBLAS_LIB} ${CUDART_LIB})
else()
    message(WARNING "Could not locate libcublas/libcudart via find_library; falling back to -lcublas -lcudart")
    target_link_libraries(tornado-cublas ${JNI_LIB_DIRS} "-lcublas -lcudart")
endif()

message(STATUS "TornadoVM cuBLAS: include=${CUDA_INCLUDE_DIR}; cublas=${CUBLAS_LIB}; cudart=${CUDART_LIB}")
