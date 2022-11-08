cmake_minimum_required (VERSION 3.6)
project (Tornado)

find_package(OpenCL REQUIRED)
find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fpermissive -export-dynamic -fPIC ")
set(CMAKE_VERBOSE_MAKEFILE on)

include_directories(
	source/
	headers/opencl/3.0/include/
	${JNI_INCLUDE_DIRS}
	${OPENCL_INCLUDE_DIRS}
	)

file(GLOB_RECURSE "source/*.cpp")
add_library(tornado-opencl SHARED
		source/OCLCommandQueue.cpp
		source/OCLContext.cpp
		source/OCLDevice.cpp
		source/OCLEvent.cpp
		source/OCLKernel.cpp
		source/OCLPlatform.cpp
		source/OCLProgram.cpp
		source/OpenCL.cpp
		source/utils.cpp
		source/opencl_time_utils.cpp)

target_link_libraries(tornado-opencl ${OpenCL_LIBRARIES} ${JNI_LIB_DIRS})
if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-opencl PROPERTIES PREFIX "")
endif()
