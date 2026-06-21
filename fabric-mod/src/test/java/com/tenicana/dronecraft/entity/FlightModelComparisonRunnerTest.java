package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class FlightModelComparisonRunnerTest {
	@Test
	void emitsComparisonRowsForRequiredScenarios() {
		List<String> lines = FlightModelComparisonRunner.runCsv();
		Set<String> scenarios = lines.stream()
				.skip(1)
				.map(line -> line.split(",", -1)[0])
				.collect(Collectors.toSet());

		assertEquals("scenario,tick,dt_s,position_diff_m,velocity_diff_mps,attitude_distance_rad,angular_rate_diff_radps,motor_power_diff,average_rpm_diff,rotor_thrust_diff_n,playable_state_corrections,simulation_state_corrections,first_threshold_tick", lines.get(0));
		assertTrue(scenarios.containsAll(Set.of(
				"hover",
				"throttle_step",
				"pitch",
				"roll",
				"yaw",
				"diagonal",
				"full_roll",
				"forward_cruise",
				"crosswind",
				"collision_free_recovery"
		)));
		assertTrue(lines.stream().skip(1).allMatch(line -> line.split(",", -1).length == 13));
	}
}
