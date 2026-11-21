# DexKit native build (thin shell version).
# Upstream dexkit-android/CMakeLists.txt depends on the dev.rikka.ndk.thirdparty:cxx prefab package
# (find_package(cxx) + -DANDROID_STL=none). prefab CLI fails on Windows / local build environments,
# so cxx/prefab is removed here in favor of NDK bundled STL (c++_static default)
# and directly reusing upstream Core source. Core/CMakeLists.txt itself only depends on z, not cxx.
cmake_minimum_required(VERSION 3.4.1)
project(dexkit)

set(CMAKE_CXX_STANDARD 20)
option(DEXKIT_ENABLE_INTERNAL_METRICS "Compile internal metrics into core" OFF)

# Global code size optimization (-Os), separate sections (-ffunction-sections -fdata-sections), and hidden symbols
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Os -ffunction-sections -fdata-sections -fvisibility=hidden")

# Global dead code elimination (GC sections) and strip DWARF debug symbols
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--strip-all -s")

# Upstream Core: produces dexkit_static (inside Core/CMakeLists.txt, including slicer/third_party)
add_subdirectory(DexKit/Core Core)

add_library(dexkit SHARED
        DexKit/dexkit/src/main/cpp/native-bridge.cpp)

if (DEXKIT_ENABLE_INTERNAL_METRICS)
    target_sources(dexkit PRIVATE DexKit/dexkit/src/main/cpp/native-bridge-metrics.cpp)
endif ()

set_target_properties(dexkit PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

find_library(log-lib log)

target_link_libraries(dexkit
        dexkit_static
        z
        ${log-lib})
