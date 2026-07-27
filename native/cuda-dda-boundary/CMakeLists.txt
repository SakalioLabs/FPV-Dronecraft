cmake_minimum_required(VERSION 3.24)

project(mcfpv_cuda_dda_boundary LANGUAGES CXX)

add_library(mcfpv_dda_boundary SHARED src/dda_boundary.cpp)
target_compile_features(mcfpv_dda_boundary PRIVATE cxx_std_20)

if(MSVC)
	target_compile_options(mcfpv_dda_boundary PRIVATE /W4 /WX /permissive-)
else()
	target_compile_options(
		mcfpv_dda_boundary
		PRIVATE
		-Wall
		-Wextra
		-Wpedantic
		-Werror
	)
endif()
