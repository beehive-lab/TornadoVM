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

# Phase 0 skeleton: builds an (almost) empty shared library so the module
# packages correctly. Phase 1 adds the real Driver-API + NVRTC sources here.
file(GLOB_RECURSE SOURCE_FILES "source/*.cpp")
add_library(tornado-cuda SHARED ${SOURCE_FILES})

# CUDA Driver API + NVRTC (runtime compilation of generated CUDA C -> PTX)
find_package(CUDAToolkit REQUIRED)
if(CUDAToolkit_FOUND)
    include_directories(${CUDAToolkit_INCLUDE_DIRS})
    link_directories(tornado-cuda ${CUDAToolkit_LIBRARY_ROOT})
    if(CMAKE_HOST_WIN32)
        set_target_properties(tornado-cuda PROPERTIES PREFIX "")
        find_library(CUDA_LIB cuda PATHS ${CUDAToolkit_LIBRARY_DIR})
        find_library(NVRTC_LIB nvrtc PATHS ${CUDAToolkit_LIBRARY_DIR})
        target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} ${CUDA_LIB} ${NVRTC_LIB})
    else()
        target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} "-L/usr/local/cuda/lib64/stubs -lcuda -lnvrtc")
    endif()
else(CUDAToolkit_FOUND)
    message("CUDA is not installed on this system.")
endif()
