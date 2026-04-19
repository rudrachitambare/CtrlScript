cmake_minimum_required(VERSION 3.22)
project(quickjs)

set(QJS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)

# Grab all QuickJS .c files, then strip out CLI/test binaries
file(GLOB QJS_ALL "${QJS_DIR}/*.c")
list(FILTER QJS_ALL EXCLUDE REGEX ".*(qjs|qjsc|run-test262|repl|unicode_gen)\.c$")

add_library(quickjs SHARED
    ${QJS_ALL}
    quickjs_jni.c
)

target_include_directories(quickjs PRIVATE ${QJS_DIR})
target_link_libraries(quickjs log)
target_compile_options(quickjs PRIVATE -O2 -fvisibility=hidden)
