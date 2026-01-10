# Server Tests CMakeLists
# Run with: mkdir build && cd build && cmake .. && make && ./arcs_tests

cmake_minimum_required(VERSION 3.14)
project(arcs_tests)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Google Test
include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/v1.14.0.zip
)
FetchContent_MakeAvailable(googletest)

# Find packages
find_package(Threads REQUIRED)
find_package(SQLite3 REQUIRED)
find_package(nlohmann_json REQUIRED)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/../src
    ${SQLite3_INCLUDE_DIRS}
)

# Source files to test
set(SRC_FILES
    ../src/security/rate_limiter.cpp
    ../src/auth/device_registry.cpp
    ../src/router/command_router.cpp
)

# Test files
set(TEST_FILES
    rate_limiter_test.cpp
    device_registry_test.cpp
    command_router_test.cpp
)

# Create test executable
add_executable(arcs_tests ${TEST_FILES} ${SRC_FILES})

# Link with gtest
target_link_libraries(arcs_tests
    gtest_main
    gmock_main
    pthread
    SQLite3::SQLite3
    nlohmann_json::nlohmann_json
)

# Enable testing
enable_testing()
add_test(NAME arcs_tests COMMAND arcs_tests)

# Coverage (optional)
option(ENABLE_COVERAGE "Enable code coverage" OFF)
if(ENABLE_COVERAGE)
    target_compile_options(arcs_tests PRIVATE --coverage)
    target_link_options(arcs_tests PRIVATE --coverage)
endif()
