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

find_path(CUDA_INCLUDE_DIR cublas_v2.h
    HINTS
        ${CUDAToolkit_INCLUDE_DIRS}
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        /usr/include
        /usr/lib/cuda/include
)

find_library(CUBLAS_LIB cublas
    HINTS
        ${CUDAToolkit_LIBRARY_DIR}
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        /usr/lib/x86_64-linux-gnu
)

if(CUDA_INCLUDE_DIR)
    include_directories(${CUDA_INCLUDE_DIR})
endif()

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cublas PROPERTIES PREFIX "")
endif()

if(CUBLAS_LIB)
    target_link_libraries(tornado-cublas ${JNI_LIB_DIRS} ${CUBLAS_LIB})
else()
    message(WARNING "Could not locate libcublas via find_library; falling back to -lcublas")
    target_link_libraries(tornado-cublas ${JNI_LIB_DIRS} "-lcublas")
endif()

message(STATUS "TornadoVM cuBLAS: include=${CUDA_INCLUDE_DIR}; lib=${CUBLAS_LIB}")
