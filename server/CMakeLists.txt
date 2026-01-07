cmake_minimum_required(VERSION 3.15)
project(arcs-server VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wpedantic")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-g -O0 -DDEBUG")

# Find packages
find_package(Threads REQUIRED)
find_package(OpenSSL REQUIRED)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}/third_party
)

# Source files
set(SERVER_SOURCES
    src/main.cpp
    src/auth/jwt_manager.cpp
    src/auth/device_registry.cpp
    src/websocket/connection_handler.cpp
    src/websocket/message_parser.cpp
    src/websocket/session_manager.cpp
    src/router/command_router.cpp
    src/stream/stream_router.cpp
    src/logger/audit_logger.cpp
)

# Create executable
add_executable(arcs-server ${SERVER_SOURCES})

# Link libraries
target_link_libraries(arcs-server
    Threads::Threads
    OpenSSL::SSL
    OpenSSL::Crypto
    pistache
    sqlite3
    jwt-cpp
    websocketpp
    uuid
    boost_system
)

# Installation
install(TARGETS arcs-server DESTINATION bin)
install(FILES config/server.conf DESTINATION etc/arcs)
