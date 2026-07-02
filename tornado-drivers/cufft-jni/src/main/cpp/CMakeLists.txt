cmake_minimum_required (VERSION 3.6)
project (TornadoCuFft)

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
add_library(tornado-cufft SHARED ${SOURCE_FILES})

# Prefer $CUDA_PATH and /usr/local/cuda over the CMake-discovered toolkit so
# headers and library come from the same toolkit the CUDA backend JIT uses
# (see the matching note in cuda-jni/src/main/cpp/CMakeLists.txt).
find_package(CUDAToolkit QUIET)

find_path(CUDA_INCLUDE_DIR cufft.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        /usr/include
        /usr/lib/cuda/include
)

find_library(CUFFT_LIB cufft
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
    set_target_properties(tornado-cufft PROPERTIES PREFIX "")
endif()

if(CUFFT_LIB)
    target_link_libraries(tornado-cufft ${JNI_LIB_DIRS} ${CUFFT_LIB})
else()
    message(WARNING "Could not locate libcufft via find_library; falling back to -lcufft")
    target_link_libraries(tornado-cufft ${JNI_LIB_DIRS} "-lcufft")
endif()

message(STATUS "TornadoVM cuFFT: include=${CUDA_INCLUDE_DIR}; cufft=${CUFFT_LIB}")
