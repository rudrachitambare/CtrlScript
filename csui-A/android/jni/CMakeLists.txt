cmake_minimum_required(VERSION 3.22)
project(quickjs)

# QuickJS C source — downloaded by GitHub Actions into jni/quickjs/
set(QJS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)

add_library(quickjs SHARED
    ${QJS_DIR}/quickjs.c
    ${QJS_DIR}/libregexp.c
    ${QJS_DIR}/libunicode.c
    ${QJS_DIR}/cutils.c
    ${QJS_DIR}/libbf.c
    quickjs_jni.c
)

target_include_directories(quickjs PRIVATE ${QJS_DIR})

# Link the Android log library so we can use __android_log_print
target_link_libraries(quickjs log)

# Disable unused QuickJS features to keep .so small
target_compile_definitions(quickjs PRIVATE
    CONFIG_BIGNUM          # needed by libbf.c
)

# Size optimisations
target_compile_options(quickjs PRIVATE -O2 -fvisibility=hidden)
