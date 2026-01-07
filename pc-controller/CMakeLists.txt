cmake_minimum_required(VERSION 3.16)
project(arcs-pc-controller VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOUIC ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

# Find Qt6
find_package(Qt6 REQUIRED COMPONENTS 
    Core 
    Widgets 
    Network
    Multimedia
    MultimediaWidgets
)

# Find FFmpeg
find_package(PkgConfig REQUIRED)
pkg_check_modules(AVCODEC REQUIRED libavcodec)
pkg_check_modules(AVFORMAT REQUIRED libavformat)
pkg_check_modules(AVUTIL REQUIRED libavutil)
pkg_check_modules(SWSCALE REQUIRED libswscale)

# Find WebSocket++
find_package(websocketpp REQUIRED)
find_package(Boost REQUIRED COMPONENTS system)
find_package(OpenSSL REQUIRED)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/src
    ${AVCODEC_INCLUDE_DIRS}
    ${AVFORMAT_INCLUDE_DIRS}
    ${AVUTIL_INCLUDE_DIRS}
    ${SWSCALE_INCLUDE_DIRS}
    ${Boost_INCLUDE_DIRS}
)

# Source files
set(SOURCES
    src/main.cpp
    src/ui/main_window.cpp
    src/ui/video_widget.cpp
    src/ui/control_panel.cpp
    src/network/websocket_client.cpp
    src/decoder/video_decoder.cpp
    src/decoder/frame_buffer.cpp
    src/input/input_translator.cpp
    src/input/gesture_detector.cpp
)

# Header files
set(HEADERS
    src/ui/main_window.h
    src/ui/video_widget.h
    src/ui/control_panel.h
    src/network/websocket_client.h
    src/decoder/video_decoder.h
    src/decoder/frame_buffer.h
    src/input/input_translator.h
    src/input/gesture_detector.h
)

# Create executable
add_executable(arcs-pc-controller ${SOURCES} ${HEADERS})

# Link libraries
target_link_libraries(arcs-pc-controller
    Qt6::Core
    Qt6::Widgets
    Qt6::Network
    Qt6::Multimedia
    Qt6::MultimediaWidgets
    ${AVCODEC_LIBRARIES}
    ${AVFORMAT_LIBRARIES}
    ${AVUTIL_LIBRARIES}
    ${SWSCALE_LIBRARIES}
    ${Boost_LIBRARIES}
    OpenSSL::SSL
    OpenSSL::Crypto
    websocketpp
)

# Install
install(TARGETS arcs-pc-controller DESTINATION bin)
