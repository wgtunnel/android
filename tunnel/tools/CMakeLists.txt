# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
# Modified for WG Tunnel WireGuard kernel and AmneziaWG userspace

cmake_minimum_required(VERSION 3.4.1)
project("WGTunnel")

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
add_link_options(LINKER:--build-id=none)
add_compile_options(-Wall -Werror)

# AmneziaWG tools for userspace config
add_executable(libam-quick.so amneziawg-tools/src/wg-quick/android.c)
target_compile_options(libam-quick.so PUBLIC -std=gnu11 -DAWG_PACKAGE_NAME=\"${ANDROID_PACKAGE_NAME}\")
target_link_libraries(libam-quick.so -ldl)

file(GLOB AM_SOURCES amneziawg-tools/src/*.c)
add_executable(libam.so ${AM_SOURCES})
target_include_directories(libam.so PUBLIC
    "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/uapi/linux/"
    "${CMAKE_CURRENT_SOURCE_DIR}/amneziawg-tools/src/"
)
target_compile_options(libam.so PUBLIC -std=gnu11 -DRUNSTATEDIR=\"/data/data/${ANDROID_PACKAGE_NAME}/cache\")

# Amnezia userspace go build
add_custom_target(libam-go.so
    WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go"
    COMMENT "Building amneziawg-go"
    VERBATIM
    COMMAND "${ANDROID_HOST_PREBUILTS}/bin/make"
        ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
        ANDROID_PACKAGE_NAME=${ANDROID_PACKAGE_NAME}
        GRADLE_USER_HOME=${GRADLE_USER_HOME}
        CC=${CMAKE_C_COMPILER}
        CFLAGS=${CMAKE_C_FLAGS}
        LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}
        SYSROOT=${CMAKE_SYSROOT}
        TARGET=${CMAKE_C_COMPILER_TARGET}
        DESTDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        BUILDDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/../generated-src
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/main.go
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/vpn/vpn.go
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/vpn/vpn_jni.c
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/proxy/proxy.go
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/proxy/proxy_jni.c
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/dns/dns.go
            ${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/dns/dns_jni.c
)