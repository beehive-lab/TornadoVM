cmake_minimum_required (VERSION 3.6)
project (Tornado)

find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -fpermissive -export-dynamic -fPIC ")
set(CMAKE_VERBOSE_MAKEFILE on)

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${C_INCLUDE_PATH}
	${CPLUS_INCLUDE_PATH}
)

file(GLOB_RECURSE SOURCE_FILES "*.cpp")
add_library(tornado-ptx SHARED
		source/PTXPlatform.cpp
		source/PTXContext.cpp
		source/PTXModule.cpp
		source/PTXStream.cpp
		source/PTXDevice.cpp
		source/PTXEvent.cpp
		source/PTX.cpp
		source/ptx_utils.cpp
		source/ptx_log.h)

#Support for CUDA Integration
FIND_PACKAGE(CUDA)
if(CUDA_FOUND)
    include_directories(${CUDA_INCLUDE_DIRS})
    link_directories(tornado-ptx ${CUDA_TOOLKIT_ROOT_DIR})
    if(CMAKE_HOST_WIN32)
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} $ENV{PTX_LDFLAGS}")
    endif()
    target_link_libraries(tornado-ptx ${JNI_LIB_DIRS} "-L/usr/local/cuda/lib64/stubs -lcuda")
    if(CMAKE_HOST_WIN32)
        set_target_properties(tornado-ptx PROPERTIES PREFIX "")
    endif() 
else(CUDA_FOUND)
    message("CUDA is not installed on this system.")
endif()
