cmake_minimum_required(VERSION 3.6)
project(TornadoMetal)

find_package(JNI REQUIRED)

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_VERBOSE_MAKEFILE on)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ObjC++")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
add_definitions(${CMAKE_CXX_FLAGS})

include_directories(
    source/
    ${JNI_INCLUDE_DIRS}
)

add_library(tornado-objc-metal SHARED
    source/objc_metal_jni.mm
)

target_link_libraries(tornado-objc-metal
    "-framework Metal"
    "-framework Foundation"
)
