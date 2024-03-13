cmake_minimum_required (VERSION 3.6)
project (Tornado)

if(MSVC)
	set(CMAKE_PREFIX_PATH "$ENV{CommonProgramFiles\(x86\)}/Intel/Shared Libraries")
	find_package(OpenCL REQUIRED)
endif()
if(APPLE)
    find_package(OpenCL REQUIRED)
endif()
find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

if(MSVC)
	string(REPLACE "/MDd" "/MTd" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
	string(REPLACE "/MD" "/MT" CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE}")

	# enable multi-process compilation, not supported by clang-cl
	if(NOT CMAKE_CXX_COMPILER_ID STREQUAL Clang)
		set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /MP")
	endif()

	# enable exceptions handling
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc")

	# enable creation of PDB files for Release Builds
	set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /Zi")
	set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} /DEBUG /OPT:REF /OPT:ICF")

	# enable CET shadow stack
	set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} /CETCOMPAT")

	#Use of sccache with MSVC requires workaround of replacing /Zi with /Z7
	#https://github.com/mozilla/sccache
	if(USE_Z7) #sccache
		string(REPLACE "/Zi" "/Z7" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
		string(REPLACE "/Zi" "/Z7" CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG}")
		string(REPLACE "/Zi" "/Z7" CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE}")
		string(REPLACE "/Zi" "/Z7" CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE}")
		string(REPLACE "/Zi" "/Z7" CMAKE_CXX_FLAGS_RELWITHDEBINFO "${CMAKE_CXX_FLAGS_RELWITHDEBINFO}")
		string(REPLACE "/Zi" "/Z7" CMAKE_C_FLAGS_RELWITHDEBINFO "${CMAKE_C_FLAGS_RELWITHDEBINFO}")
	endif()
else()
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fpermissive")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
	set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -export-dynamic")
	add_definitions(${CMAKE_CXX_FLAGS})
endif()

if(APPLE)
    include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${OPENCL_INCLUDE_DIRS}
	)
else()
    include_directories(
	source/
	headers/include
	${JNI_INCLUDE_DIRS}
	${OPENCL_INCLUDE_DIRS}
	)
endif()

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
	source/opencl_time_utils.cpp
	)

if(MSVC OR APPLE)
    target_link_libraries(tornado-opencl ${OpenCL_LIBRARIES} ${JNI_LIB_DIRS})
else()
    target_link_libraries(tornado-opencl -lOpenCL ${OpenCL_LIBRARIES} ${JNI_LIB_DIRS})
endif()
if(CMAKE_HOST_WIN32)
    set_target_properties(tornado-opencl PROPERTIES PREFIX "")
endif()
