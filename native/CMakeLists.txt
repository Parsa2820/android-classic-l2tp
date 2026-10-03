cmake_minimum_required(VERSION 3.16)
project(classic_l2tp_host C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(ENGINE_DIR "${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp")
set(MBEDTLS_DIR "${CMAKE_CURRENT_LIST_DIR}/../third_party/mbedtls")

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
add_subdirectory("${MBEDTLS_DIR}" "${CMAKE_BINARY_DIR}/mbedtls")
if(TARGET mbedcrypto)
  target_compile_definitions(mbedcrypto PRIVATE MBEDTLS_MD4_C)
endif()

find_package(JNI REQUIRED)

set(ENGINE_SOURCES
  ${ENGINE_DIR}/tunnel_loop.c
  ${ENGINE_DIR}/packet_endpoint.c
  ${ENGINE_DIR}/gvisor_bridge.c
  ${ENGINE_DIR}/ipv4_dataplane.c
  ${ENGINE_DIR}/nat_t_keepalive.c
  ${ENGINE_DIR}/util.c
  ${ENGINE_DIR}/util_endian.c
  ${ENGINE_DIR}/modp2048_data.c
  ${ENGINE_DIR}/ikev1.c
  ${ENGINE_DIR}/esp_udp.c
  ${ENGINE_DIR}/l2tp_avps.c
  ${ENGINE_DIR}/l2tp.c
  ${ENGINE_DIR}/ppp_mschap.c
  ${ENGINE_DIR}/ppp.c
)

add_library(tunnel_engine_host STATIC ${ENGINE_SOURCES})
target_include_directories(tunnel_engine_host PRIVATE
  ${ENGINE_DIR}
  ${MBEDTLS_DIR}/include
  ${JNI_INCLUDE_DIRS}
  ${CMAKE_CURRENT_LIST_DIR}/compat
)
target_compile_definitions(tunnel_engine_host PRIVATE
  TUNNEL_FORGE_ESP_INNER_UDP_NO_CHECKSUM=1
  TUNNEL_FORGE_KEYMAT_VARIANT=1
)
target_link_libraries(tunnel_engine_host PUBLIC mbedtls mbedx509 mbedcrypto ${CMAKE_DL_LIBS})

add_executable(l2tp-lab lab_negotiate.c)
target_link_libraries(l2tp-lab PRIVATE tunnel_engine_host)
target_include_directories(l2tp-lab PRIVATE ${ENGINE_DIR} ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_LIST_DIR}/compat)

add_executable(mschap_rfc2759 tests/mschap_rfc2759.c ${ENGINE_DIR}/ppp_mschap.c)
target_include_directories(mschap_rfc2759 PRIVATE ${ENGINE_DIR} ${MBEDTLS_DIR}/include)
target_link_libraries(mschap_rfc2759 PRIVATE mbedcrypto)

enable_testing()
add_test(NAME mschap_rfc2759 COMMAND mschap_rfc2759)
