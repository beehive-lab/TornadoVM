cmake_minimum_required (VERSION 3.6)
project (tornado)

find_package(JNI REQUIRED)

#if (DEFINED $ENV{ZE_SHARED_LOADER})
#	message("- ZE_SHARED_LOADER found")
#else()
#	message("ERROR: ZE_SHARED_LOADER is not set")
#	message("Set this export to PATH-TO/level-zero/build/lib/libze_loader.so")
#	return()
#endif()

# set the C++14 standard
set(CMAKE_CXX_STANDARD 14)
set(GCC_INTEL_LEVEL0 "-std=c++14 -O2 -fpermissive -rdynamic -fPIC -lstdc++  -fno-stack-protector")

# This should point to: "PATH-TO/level-zero/build/lib/libze_loader.so"
set(ZE_LOADER $ENV{ZE_SHARED_LOADER})

add_definitions(${GCC_INTEL_LEVEL0})

include_directories(
	./
	${JNI_INCLUDE_DIRS}
	${OPENCL_INCLUDE_DIRS}
	)

file(GLOB_RECURSE "src/*.cpp")
add_library(tornado-levelzero SHARED
	src/levelZeroDriver.cpp
	src/levelZeroContext.cpp
	src/levelZeroDevice.cpp
	src/levelZeroBuffer.cpp
	src/levelZeroModule.cpp
	src/levelZeroKernel.cpp
	src/levelZeroCommandList.cpp
    src/levelZeroCommandQueue.cpp
	src/levelZeroFence.cpp
	src/levelZeroTimeStampKernel.cpp
		src/levelZeroDescriptors.cpp)

target_link_libraries(tornado-levelzero ${OpenCL_LIBRARIES} ${JNI_LIB_DIRS})
target_link_libraries(tornado-levelzero ${ZE_LOADER})
