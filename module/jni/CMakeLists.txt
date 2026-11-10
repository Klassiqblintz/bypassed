cmake_minimum_required(VERSION 3.20.0)
project(kyc_bypasser_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(SOURCES
    entry.cpp
    camera_hook.cpp
    frame_publisher.cpp
    metadata_spoof.cpp
    companion.cpp
    face_detector.cpp
)

add_library(kyc_bypasser SHARED ${SOURCES})

target_link_libraries(
    kyc_bypasser
    android
    log
    jnigraphics
    media
    OpenMAXAL
    OpenSLES
)

target_compile_options(kyc_bypasser PRIVATE
    -Wall
    -Wextra
    -O2
)

set_target_properties(kyc_bypasser PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../libs/${CMAKE_ANDROID_ARCH_ABI}
)
