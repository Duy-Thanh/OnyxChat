cmake_minimum_required(VERSION 3.10.2)

# Set the project name
project(pqc-native)

# Find the Android NDK
find_library(log-lib log)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Source files
set(SOURCE_FILES
    pqc/pqc_jni.cpp
    pqc/kyber/kyber.cpp
    pqc/dilithium/dilithium.cpp
    pqc/aes/aes_gcm.cpp
    pqc/shamir/shamir.cpp
)

# Create the shared library
add_library(pqc-native SHARED ${SOURCE_FILES})

# Link against the required libraries
target_link_libraries(pqc-native
    ${log-lib}
)

# Set compiler flags
target_compile_options(pqc-native PRIVATE
    -Wall
    -Wno-unused-parameter
    -O3
)

# Set C++ standard
set_target_properties(pqc-native PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
