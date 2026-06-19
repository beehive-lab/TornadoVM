cmake_minimum_required (VERSION 3.6)
project (TornadoCUDA)

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
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fpermissive")
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

# Real CUDA-C JNI backend: Driver API + NVRTC sources are globbed from source/.
file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-cuda SHARED ${SOURCE_FILES})

# CUDA Driver API + NVRTC (runtime compilation of generated CUDA C -> PTX).
# Prefer the CMake-discovered CUDA toolkit, but fall back to common system
# locations so the lib builds even when CUDA is not under /usr/local/cuda.
find_package(CUDAToolkit QUIET)

find_path(CUDA_INCLUDE_DIR cuda.h
    HINTS
        ${CUDAToolkit_INCLUDE_DIRS}
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        /usr/include
        /usr/lib/cuda/include
)
find_path(NVRTC_INCLUDE_DIR nvrtc.h
    HINTS
        ${CUDAToolkit_INCLUDE_DIRS}
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        /usr/include
        /usr/lib/cuda/include
)

# Link the CUDA Driver API stub and NVRTC. The real libcuda is provided by the
# NVIDIA driver at runtime; the stub satisfies the linker at build time.
find_library(CUDA_DRIVER_LIB cuda
    HINTS
        ${CUDAToolkit_LIBRARY_DIR}
        $ENV{CUDA_PATH}/lib64/stubs
        /usr/local/cuda/lib64/stubs
        /usr/lib/x86_64-linux-gnu/stubs
        /usr/lib/x86_64-linux-gnu
)
find_library(NVRTC_LIB nvrtc
    HINTS
        ${CUDAToolkit_LIBRARY_DIR}
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        /usr/lib/x86_64-linux-gnu
)

if(CUDA_INCLUDE_DIR)
    include_directories(${CUDA_INCLUDE_DIR})
endif()
if(NVRTC_INCLUDE_DIR)
    include_directories(${NVRTC_INCLUDE_DIR})
endif()

if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-cuda PROPERTIES PREFIX "")
endif()

if(CUDA_DRIVER_LIB AND NVRTC_LIB)
    target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} ${CUDA_DRIVER_LIB} ${NVRTC_LIB})
else()
    message(WARNING "Could not locate libcuda / libnvrtc via find_library; falling back to -lcuda -lnvrtc")
    target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} "-lcuda -lnvrtc")
endif()
