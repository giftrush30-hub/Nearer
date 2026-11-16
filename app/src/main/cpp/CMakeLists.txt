cmake_minimum_required(VERSION 3.22.1)
project(nearer_audio LANGUAGES CXX)

add_library(nearer_audio SHARED nearer_audio.cpp)
find_package(oboe REQUIRED CONFIG)
target_compile_features(nearer_audio PRIVATE cxx_std_17)
target_compile_options(nearer_audio PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(nearer_audio PRIVATE oboe::oboe log)
