cmake_minimum_required(VERSION 3.20)
project(llamapanama C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

string(TOLOWER "${CMAKE_SYSTEM_NAME}" OS_LOWER)
string(TOLOWER "${CMAKE_SYSTEM_PROCESSOR}" ARCH_LOWER)
set(OUTPUT_DIR "${CMAKE_BINARY_DIR}/artifacts/${OS_LOWER}-${ARCH_LOWER}")
file(MAKE_DIRECTORY "${OUTPUT_DIR}")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${OUTPUT_DIR}")
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${OUTPUT_DIR}")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${OUTPUT_DIR}")

add_library(llamapanama SHARED src/main/c/llamapanama.c)
target_include_directories(llamapanama PUBLIC src/main/c)
set_target_properties(llamapanama PROPERTIES OUTPUT_NAME "llamapanama")
