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

# Hint order matters: prefer $CUDA_PATH and /usr/local/cuda over the
# CMake-discovered toolkit. On systems with both a full toolkit under
# /usr/local/cuda and an older apt-installed one (nvcc in /usr/bin),
# find_package(CUDAToolkit) resolves to the apt toolkit, but the JIT include
# path in CUDAProgram.cpp prefers /usr/local/cuda/include — linking NVRTC from
# a different toolkit version than the headers breaks every runtime kernel
# compilation (e.g. undefined __internal_float2half in cuda_fp16.hpp).
find_path(CUDA_INCLUDE_DIR cuda.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        /usr/include
        /usr/lib/cuda/include
)
find_path(NVRTC_INCLUDE_DIR nvrtc.h
    HINTS
        $ENV{CUDA_PATH}/include
        /usr/local/cuda/include
        ${CUDAToolkit_INCLUDE_DIRS}
        /usr/include
        /usr/lib/cuda/include
)

# Link the CUDA Driver API stub and NVRTC. The real libcuda is provided by the
# NVIDIA driver at runtime; the stub satisfies the linker at build time.
find_library(CUDA_DRIVER_LIB cuda
    HINTS
        $ENV{CUDA_PATH}/lib64/stubs
        /usr/local/cuda/lib64/stubs
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu/stubs
        /usr/lib/x86_64-linux-gnu
)
find_library(NVRTC_LIB nvrtc
    HINTS
        $ENV{CUDA_PATH}/lib64
        /usr/local/cuda/lib64
        ${CUDAToolkit_LIBRARY_DIR}
        /usr/lib/x86_64-linux-gnu
)

# ── CUDA toolkit / driver compatibility guard ───────────────────────────────
# This backend compiles kernels at runtime with NVRTC to a cubin (SASS) and loads
# it through the driver. A cubin built by toolkit X.y loads on any driver of the
# same CUDA major X (CUDA minor-version compatibility), so a newer *minor* toolkit
# than the driver is fine; a newer *major* toolkit produces an image the driver
# cannot load and fails at runtime (e.g. CUDA_ERROR_UNSUPPORTED_PTX_VERSION).
# Surface that mismatch here, at build time, instead of letting it fail cryptically
# later. Best-effort: skips quietly when versions cannot be determined.
get_filename_component(_nvrtc_realpath "${NVRTC_LIB}" REALPATH)
set(_driver_cuda "")
find_program(NVIDIA_SMI nvidia-smi)
if(NVIDIA_SMI)
    execute_process(COMMAND ${NVIDIA_SMI} OUTPUT_VARIABLE _smi_out ERROR_QUIET RESULT_VARIABLE _smi_rc)
    if(_smi_rc EQUAL 0)
        string(REGEX MATCH "CUDA Version: ([0-9]+)\\.([0-9]+)" _ "${_smi_out}")
        if(CMAKE_MATCH_1)
            set(_driver_major "${CMAKE_MATCH_1}")
            set(_driver_minor "${CMAKE_MATCH_2}")
            set(_driver_cuda "${CMAKE_MATCH_1}.${CMAKE_MATCH_2}")
        endif()
    endif()
endif()

message(STATUS "TornadoVM CUDA: toolkit=${CUDAToolkit_VERSION} (nvrtc: ${_nvrtc_realpath}); driver max CUDA=${_driver_cuda}")

if(_driver_cuda AND CUDAToolkit_VERSION_MAJOR)
    if(CUDAToolkit_VERSION_MAJOR GREATER _driver_major)
        message(FATAL_ERROR
            "CUDA toolkit ${CUDAToolkit_VERSION} has a newer major than the installed driver supports "
            "(driver max CUDA ${_driver_cuda}). A cubin produced by this toolkit cannot be loaded by this "
            "driver. Fix one of:\n"
            "  * build against a CUDA toolkit whose major <= ${_driver_major} "
            "(point CUDA_PATH/PATH/LD_LIBRARY_PATH at e.g. /usr/local/cuda-${_driver_cuda}), or\n"
            "  * upgrade the NVIDIA driver to one supporting CUDA ${CUDAToolkit_VERSION_MAJOR}.x.")
    elseif(CUDAToolkit_VERSION_MINOR GREATER _driver_minor)
        message(STATUS
            "TornadoVM CUDA: toolkit minor (${CUDAToolkit_VERSION}) is newer than the driver (CUDA "
            "${_driver_cuda}); supported because kernels load as cubin via CUDA minor-version compatibility "
            "(a raw-PTX path would fail here with CUDA_ERROR_UNSUPPORTED_PTX_VERSION).")
    endif()
endif()

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
