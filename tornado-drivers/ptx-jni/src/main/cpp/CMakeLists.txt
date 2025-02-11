cmake_minimum_required (VERSION 3.6)
project (Tornado)

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

include_directories(
	source/
	${JNI_INCLUDE_DIRS}
	${CPLUS_INCLUDE_PATH}
	$ENV{CPLUS_INCLUDE_PATH}
	$ENV{C_INCLUDE_PATH}
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
	source/PTXNvidiaPowerMetricHandler.cpp
	source/ptx_utils.cpp
    source/NativePTXStream.cpp
	source/ptx_log.h
	)

#Support for CUDA Integration
find_package(CUDAToolkit REQUIRED)
if(CUDAToolkit_FOUND)
    include_directories(${CUDAToolkit_INCLUDE_DIRS})
    link_directories(tornado-ptx ${CUDAToolkit_LIBRARY_ROOT})
    if(CMAKE_HOST_WIN32)
        set_target_properties(tornado-ptx PROPERTIES PREFIX "")
        find_library(CUDA_LIB cuda PATHS ${CUDAToolkit_LIBRARY_DIR})
        find_path(NVML_INCLUDE_DIR NAMES nvml.h HINTS ${CUDAToolkit_INCLUDE_DIRS})
        if(NOT NVML_INCLUDE_DIR)
            message(STATUS "nvml.h not found")
        endif()
        find_library(NVML_LIBRARY NAMES nvml PATHS ${CUDAToolkit_LIBRARY_DIR})
        if(NOT NVML_LIBRARY)
            message(STATUS "nvml not found")
        else()
            set(EXTRA_LIBS ${EXTRA_LIBS} "${NVML_LIBRARY}")
        endif()
        target_link_libraries(tornado-ptx ${EXTRA_LIBS} ${JNI_LIB_DIRS} ${CUDA_LIB})
    else()
        execute_process(COMMAND grep -q "jetson-nano" /proc/device-tree/model RESULT_VARIABLE device_model)
        if(${device_model} EQUAL 0)
            message(STATUS "Building on Jetson Nano - skipping the linking with the nvidia management library (nvml)")
        else()
            message(STATUS "Not building on Jetson Nano")
            find_path(NVML_INCLUDE_DIR NAMES nvml.h HINTS ${CUDA_INCLUDE_DIRS} /usr/local/cuda/targets/x86_64-linux/include /usr/local/cuda/targets/sbsa-linux/include)
            find_library(NVML_LIBRARY NAMES nvidia-ml PATHS ${CUDA_TOOLKIT_ROOT_DIR}/lib/x64 /usr/local/cuda/targets/x86_64-linux/lib/stubs/ /usr/local/cuda/targets/sbsa-linux/lib/stubs/)
        endif()
        target_link_libraries(tornado-ptx ${JNI_LIB_DIRS} "-L/usr/local/cuda/lib64/stubs -lcuda -lnvidia-ml")
    endif()
    if (NVML_INCLUDE_DIR AND NVML_LIBRARY)
        add_definitions(-DNVML_IS_SUPPORTED)
    else()
        if(NOT NVML_INCLUDE_DIR)
            message(STATUS "nvml.h not found")
        endif()
        if(NOT NVML_LIBRARY)
            message(STATUS "nvml or nvidia-ml not found")
        endif()
    endif()
else(CUDAToolkit_FOUND)
    message("CUDA is not installed on this system.")
endif()
