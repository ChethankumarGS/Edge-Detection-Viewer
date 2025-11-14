cmake_minimum_required(VERSION 3.10.2)

project("native-lib")


set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_ANDROID_STL_TYPE c++_shared)

set(OpenCV_DIR "C:/EdgeDetectionViewer/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
    message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
else()
    message(FATAL_ERROR "OpenCV not found. Please set OpenCV_DIR correctly.")
endif()

add_library(
        native-lib
        SHARED
        native-lib.cpp
        opencv-processor.cpp
)

target_include_directories(native-lib PRIVATE
        ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(
        native-lib
        ${OpenCV_LIBS}
        android
        log
        jnigraphics
)

