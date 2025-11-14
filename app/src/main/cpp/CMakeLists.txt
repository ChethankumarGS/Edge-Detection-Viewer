cmake_minimum_required(VERSION 3.10.2)

project("native-lib")

# Set C++ standard
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set C++ standard library (must match OpenCV build configuration)
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# Find OpenCV (assuming OpenCV Android SDK is in the project)
# Note: Update the path to match your OpenCV installation
set(OpenCV_DIR "C:/EdgeDetectionViewer/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
    message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
else()
    message(FATAL_ERROR "OpenCV not found. Please set OpenCV_DIR correctly.")
endif()

# Add library
add_library(
        native-lib
        SHARED
        native-lib.cpp
        opencv-processor.cpp
)

# Include directories
target_include_directories(native-lib PRIVATE
        ${OpenCV_INCLUDE_DIRS}
)

# Link libraries
target_link_libraries(
        native-lib
        ${OpenCV_LIBS}
        android
        log
        jnigraphics
)

