cmake_minimum_required(VERSION 3.22.1)
project(direct_forwarder)

set(FORWARDER_CORE_SOURCES
    packet_parser.cpp
    tls_sni_parser.cpp
    flow_table.cpp
    tun_reader.cpp
    checksum.cpp
    tcp_state.cpp
    tcp_packet_builder.cpp
    direct_tcp_socket.cpp
    tcp_proxy_session.cpp
    udp_packet_builder.cpp
    direct_udp_socket.cpp
    udp_proxy_session.cpp
    forwarder_engine.cpp
    forwarder_runtime.cpp)

add_library(forwarder_core STATIC ${FORWARDER_CORE_SOURCES})
set_target_properties(forwarder_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(forwarder_core PUBLIC cxx_std_17)
target_include_directories(forwarder_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

if(ANDROID)
    add_library(direct_forwarder SHARED direct_forwarder.cpp)
    find_library(log-lib log)
    target_link_libraries(direct_forwarder forwarder_core ${log-lib})
endif()

option(REDMIKLAB_BUILD_TESTS "Build host-native forwarding tests" OFF)
if(REDMIKLAB_BUILD_TESTS)
    enable_testing()
    file(GLOB FORWARDER_TEST_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/*_test.cpp")
    foreach(TEST_SOURCE ${FORWARDER_TEST_SOURCES})
        get_filename_component(TEST_NAME ${TEST_SOURCE} NAME_WE)
        add_executable(${TEST_NAME} ${TEST_SOURCE})
        target_link_libraries(${TEST_NAME} PRIVATE forwarder_core)
        add_test(NAME ${TEST_NAME} COMMAND ${TEST_NAME})
    endforeach()
endif()
