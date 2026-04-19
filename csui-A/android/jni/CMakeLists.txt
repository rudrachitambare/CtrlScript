cmake_minimum_required(VERSION 3.22)
project(quickjs)

set(QJS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)

# Explicit whitelist — only the library source files, no CLI tools
set(QJS_LIB_FILES quickjs.c libregexp.c libunicode.c cutils.c)

# dtoa.c exists in newer QuickJS versions — add if present
foreach(f dtoa.c libbf.c)
    if(EXISTS "${QJS_DIR}/${f}")
        list(APPEND QJS_LIB_FILES ${f})
    endif()
endforeach()

list(TRANSFORM QJS_LIB_FILES PREPEND "${QJS_DIR}/")

add_library(quickjs SHARED
    ${QJS_LIB_FILES}
    quickjs_jni.c
)

target_include_directories(quickjs PRIVATE ${QJS_DIR})
target_link_libraries(quickjs log)
target_compile_options(quickjs PRIVATE -O2 -fvisibility=hidden)
