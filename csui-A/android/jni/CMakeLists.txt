cmake_minimum_required(VERSION 3.22)
project(quickjs)

# QuickJS C source — downloaded by GitHub Actions into jni/quickjs/
set(QJS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)

# Glob all QuickJS .c files so we don't break on version differences
file(GLOB QJS_SOURCES "${QJS_DIR}/*.c")

add_library(quickjs SHARED
    ${QJS_SOURCES}
    quickjs_jni.c
)

target_include_directories(quickjs PRIVATE ${QJS_DIR})

target_link_libraries(quickjs log)

target_compile_options(quickjs PRIVATE -O2 -fvisibility=hidden)
