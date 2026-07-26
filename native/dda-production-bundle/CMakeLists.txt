cmake_minimum_required(VERSION 3.20)
project(mcfpv_dda_production_bundle LANGUAGES CXX)

add_executable(
	mcfpv_dda_bundle_verify
	src/dda_bundle_verify.cpp
)
target_compile_features(mcfpv_dda_bundle_verify PRIVATE cxx_std_20)
if(MSVC)
	target_compile_options(
		mcfpv_dda_bundle_verify
		PRIVATE /W4 /WX /permissive-
	)
else()
	target_compile_options(
		mcfpv_dda_bundle_verify
		PRIVATE -Wall -Wextra -Wpedantic -Werror
	)
endif()

include(CTest)
if(BUILD_TESTING)
	add_test(
		NAME mcfpv_dda_bundle_self_test
		COMMAND mcfpv_dda_bundle_verify --self-test
	)
	if(DEFINED MCFPV_DDA_FIXTURE)
		add_test(
			NAME mcfpv_dda_bundle_java_fixture
			COMMAND
				mcfpv_dda_bundle_verify
				--expect-fixture
				--require-complete
				$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:--expected-results>
				$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:${MCFPV_DDA_EXPECTED_RESULTS}>
				"${MCFPV_DDA_FIXTURE}"
		)
		find_package(Python3 COMPONENTS Interpreter)
		if(Python3_Interpreter_FOUND)
			add_test(
				NAME mcfpv_dda_bundle_corruption
				COMMAND
					"${Python3_EXECUTABLE}"
					"${CMAKE_CURRENT_SOURCE_DIR}/../../docs/scripts/exercise_native_dda_bundle_verifier.py"
					"--executable"
					"$<TARGET_FILE:mcfpv_dda_bundle_verify>"
					"--fixture"
					"${MCFPV_DDA_FIXTURE}"
					$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:--expected-results>
					$<$<BOOL:${MCFPV_DDA_EXPECTED_RESULTS}>:${MCFPV_DDA_EXPECTED_RESULTS}>
			)
		endif()
	endif()
endif()
