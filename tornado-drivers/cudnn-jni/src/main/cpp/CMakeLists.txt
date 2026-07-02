cmake_minimum_required (VERSION 3.6)
project (TornadoCuDnn)

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
add_library(tornado-cudnn SHARED ${SOURCE_FILES})

# Prefer $CUDA_PATH and /usr/local/cuda over the CMake-discovered toolkit so
# headers and library come from the same toolkit the CUDA backend JIT uses
# (see the matching note in cuda-jni/src/main/cpp/CMakeLists.txt).
find_package(CUDAToolkit QUIET)

find_path(CUDA_INCLUDE_DIR cudnn.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
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

if(CUDNN_LIB AND CUDART_LIB)
    target_link_libraries(tornado-cudnn ${JNI_LIB_DIRS} ${CUDNN_LIB} ${CUDART_LIB})
else()
    message(WARNING "Could not locate libcudnn via find_library; falling back to -lcudnn -lcudart")
    target_link_libraries(tornado-cudnn ${JNI_LIB_DIRS} "-lcudnn -lcudart")
endif()

message(STATUS "TornadoVM cuFFT: include=${CUDA_INCLUDE_DIR}; cudnn=${CUDNN_LIB}")
