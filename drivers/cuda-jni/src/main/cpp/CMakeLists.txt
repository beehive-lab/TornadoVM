cmake_minimum_required (VERSION 3.6)
project (Tornado)

find_package(CUDA REQUIRED)
find_package(JNI REQUIRED)

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	)

file(GLOB_RECURSE SOURCE_FILES "*.c")
add_library(tornado-cuda SHARED
source/CUDAPlatform.c
source/CUDAContext.c
source/CUDAModule.c
source/CUDAStream.c
source/CUDADevice.c
source/CUDA.c
)

set_property(TARGET tornado-cuda PROPERTY C_STANDARD 99)
set_target_properties(tornado-cuda PROPERTIES LINKER_LANGUAGE C)
target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} -lnvrtc -lcuda)
set_target_properties(tornado-cuda PROPERTIES LINK_FLAGS "-L $CUDA_PATH/lib64 -Wl,-rpath,$CUDA_PATH/lib64")
