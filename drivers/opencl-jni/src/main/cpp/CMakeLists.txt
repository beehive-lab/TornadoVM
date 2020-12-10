cmake_minimum_required (VERSION 3.6)
project (Tornado)

find_package(OpenCL REQUIRED)
find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fpermissive -rdynamic -fPIC ")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -rdynamic -fPIC ")
 
include_directories(
	source/
	headers/opencl/3.0/include/
	${JNI_INCLUDE_DIRS}
	${OPENCL_INCLUDE_DIRS}
	)

file(GLOB_RECURSE "source/*.c source/*.cpp")
add_library(tornado-opencl SHARED 
	source/OCLCommandQueue.c
	source/OCLContext.c
	source/OCLDevice.c
	source/OCLEvent.c
	source/OCLKernel.c
	source/OCLPlatform.c
	source/OCLProgram.c
	source/OpenCL.cpp
	source/data_movers.c
	source/utils.c
	source/opencl_time_utils.cpp
)

set_property(TARGET tornado-opencl PROPERTY C_STANDARD 99)
target_link_libraries(tornado-opencl ${OpenCL_LIBRARIES} ${JNI_LIB_DIRS})
