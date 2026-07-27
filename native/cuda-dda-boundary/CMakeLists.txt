cmake_minimum_required(VERSION 3.24)

project(mcfpv_cuda_dda_boundary LANGUAGES CXX)

add_library(
	mcfpv_dda_boundary
	SHARED
	src/dda_boundary.cpp
	src/cuda_bridge.cpp
)
target_compile_definitions(
	mcfpv_dda_boundary
	PRIVATE
	MCFPV_CUDA_BRIDGE_BUILD
)
target_include_directories(
	mcfpv_dda_boundary
	PUBLIC
	"${CMAKE_CURRENT_SOURCE_DIR}/include"
)
set_target_properties(
	mcfpv_dda_boundary
	PROPERTIES
	OUTPUT_NAME "mcfpv_dda_boundary_v2"
)

add_executable(mcfpv_cuda_worker src/cuda_worker.cpp)
target_compile_features(mcfpv_cuda_worker PRIVATE cxx_std_20)
target_link_libraries(mcfpv_cuda_worker PRIVATE mcfpv_dda_boundary)
target_compile_features(mcfpv_dda_boundary PRIVATE cxx_std_20)

if(MSVC)
	target_compile_options(mcfpv_dda_boundary PRIVATE /W4 /WX /permissive-)
	target_compile_options(mcfpv_cuda_worker PRIVATE /W4 /WX /permissive-)
else()
	target_compile_options(
		mcfpv_dda_boundary
		PRIVATE
		-Wall
		-Wextra
		-Wpedantic
		-Werror
	)
	target_compile_options(
		mcfpv_cuda_worker
		PRIVATE
		-Wall
		-Wextra
		-Wpedantic
		-Werror
	)
endif()
