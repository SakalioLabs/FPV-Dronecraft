cmake_minimum_required(VERSION 3.20)
project(mcfpv_cuda_dda_research LANGUAGES CXX)

option(
	MCFPV_ENABLE_CUDA
	"Build the CUDA DDA executor when a CUDA compiler is available"
	ON
)

add_executable(
	mcfpv_dda_cpu_reference
	src/dda_cpu_reference.cpp
)
target_compile_features(mcfpv_dda_cpu_reference PRIVATE cxx_std_20)

add_executable(
	mcfpv_dda_batch_plan
	src/dda_batch_plan.cpp
)
target_compile_features(mcfpv_dda_batch_plan PRIVATE cxx_std_20)

if(MSVC)
	target_compile_options(
		mcfpv_dda_cpu_reference
		PRIVATE /W4 /WX /permissive-
	)
	target_compile_options(
		mcfpv_dda_batch_plan
		PRIVATE /W4 /WX /permissive-
	)
else()
	target_compile_options(
		mcfpv_dda_cpu_reference
		PRIVATE -Wall -Wextra -Wpedantic -Werror
	)
	target_compile_options(
		mcfpv_dda_batch_plan
		PRIVATE -Wall -Wextra -Wpedantic -Werror
	)
endif()

include(CheckLanguage)
if(MCFPV_ENABLE_CUDA)
	check_language(CUDA)
endif()

if(MCFPV_ENABLE_CUDA AND CMAKE_CUDA_COMPILER)
	enable_language(CUDA)
	add_executable(
		mcfpv_dda_cuda
		src/dda_cuda.cu
	)
	set_target_properties(
		mcfpv_dda_cuda
		PROPERTIES
			CUDA_STANDARD 20
			CUDA_STANDARD_REQUIRED YES
			CUDA_ARCHITECTURES "75;86"
	)
	target_compile_definitions(
		mcfpv_dda_cuda
		PRIVATE MCFPV_CUDA_DDA_COMPILED=1
	)
	if(MSVC)
		target_compile_options(
			mcfpv_dda_cuda
			PRIVATE
				$<$<COMPILE_LANGUAGE:CXX>:/W4>
				$<$<COMPILE_LANGUAGE:CXX>:/WX>
				$<$<COMPILE_LANGUAGE:CXX>:/permissive->
				$<$<COMPILE_LANGUAGE:CUDA>:-Xcompiler=/W4,/WX,/permissive->
		)
	else()
		target_compile_options(
			mcfpv_dda_cuda
			PRIVATE
				$<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Wpedantic;-Werror>
				$<$<COMPILE_LANGUAGE:CUDA>:-Xcompiler=-Wall,-Wextra,-Wpedantic,-Werror>
		)
	endif()
	message(STATUS "MCFPV CUDA DDA executor: enabled")
else()
	message(
		STATUS
		"MCFPV CUDA DDA executor: disabled (CUDA compiler unavailable)"
	)
endif()

include(CTest)
if(BUILD_TESTING)
	add_test(
		NAME mcfpv_dda_cpu_reference_self_test
		COMMAND mcfpv_dda_cpu_reference --self-test
	)
	add_test(
		NAME mcfpv_dda_batch_plan_self_test
		COMMAND mcfpv_dda_batch_plan --self-test
	)
	if(DEFINED MCFPV_DDA_FIXTURE)
		add_test(
			NAME mcfpv_dda_cpu_reference_java_fixture
			COMMAND
				mcfpv_dda_cpu_reference
				--warmup 2
				--iterations 5
				$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:--expected-results>
				$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:${MCFPV_DDA_EXPECTED_RESULTS}>
				"${MCFPV_DDA_FIXTURE}"
		)
		add_test(
			NAME mcfpv_dda_batch_plan_java_fixture
			COMMAND
				mcfpv_dda_batch_plan
				"${MCFPV_DDA_FIXTURE}"
		)
		if(TARGET mcfpv_dda_cuda)
			add_test(
				NAME mcfpv_dda_cuda_java_fixture
				COMMAND
					mcfpv_dda_cuda
					--warmup 2
					--iterations 5
					$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:--expected-results>
					$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:${MCFPV_DDA_EXPECTED_RESULTS}>
					"${MCFPV_DDA_FIXTURE}"
			)
		endif()
	endif()
endif()
