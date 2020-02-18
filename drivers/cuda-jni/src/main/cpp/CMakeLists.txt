cmake_minimum_required (VERSION 3.6)
project (Tornado)

find_package(JNI REQUIRED)

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${C_INCLUDE_PATH}
	${CPLUS_INCLUDE_PATH}
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

#Support for CUDA Integration
FIND_PACKAGE(CUDA)
if(CUDA_FOUND)
	set_property(TARGET tornado-cuda PROPERTY C_STANDARD 99)
	set_target_properties(tornado-cuda PROPERTIES LINKER_LANGUAGE C)
	target_link_libraries(tornado-cuda ${JNI_LIB_DIRS} -lcuda)
else(CUDA_FOUND)
    message("CUDA is not installed on this system.")
endif()


