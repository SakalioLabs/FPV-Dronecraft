# CT/CP/J Playable Reference Note 2026-07-03

`sim/lab` now has a callable CT/CP/J lookup evaluator for the accepted `apDrone` reference windows:
static anchor, mid-domain bilinear, and high-advance single-RPM edge. These rows can be used as low-cost
playable reference material only for curve shape checks: CT decreases with advance ratio, CP rises toward the
high-advance edge, and dimensional thrust/power/torque follow the SI propeller coefficient equations.

Do not auto-apply these rows to `playable/dev` tuning yet. They are suitable for simplified force/power curve
prototyping and controller-feel comparison, but not for full propeller replacement, OpenFOAM coupling, or
terrain/voxel airflow tuning until more reviewed CT/CP/J rows and offline CFD comparisons are available.

2026-07-05 update: `./gradlew :drone-sim-core:ctCpJCurve -Ppreset=apDrone` now also exports
`static_anchored_runtime_*` rows. These rows keep the APDrone rotor-spec static CT/CP at the requested runtime RPM
and apply only the accepted PropellerArchive advance-ratio shape. `playable/dev` may use the normalized CT rolloff,
CP rise, and torque/power ratios as low-cost feel references inside the accepted J envelope. It should not treat the
absolute N/W/Nm values as gameplay tuning targets, and it should not extrapolate beyond the exported envelope.

2026-07-07 update: `docs/data/propeller_archive_ct_cp_j_runtime_curve_packet.csv` is now the playable-facing
selection surface. `playable/dev` may only consult rows where `runtime_eligibility_status=ACCEPTED` and
`runtime_force_replacement_accepted=true`; these are static-anchored APDrone runtime rows with finite thrust,
shaft power, shaft torque, body thrust force, reaction torque, thrust moment, tip Mach, and Reynolds telemetry.
Rows marked `MOMENTUM_POWER_CLOSURE_FAILED`, `OPERATING_POINT_OUTSIDE_RUNTIME_ENVELOPE`,
`OBLIQUE_INFLOW_OUTSIDE_RUNTIME_ENVELOPE`, `CLAMPED`, `OUT_OF_ENVELOPE_BLOCKED`, or `NOT_RUNTIME_CANDIDATE`
remain sim/lab diagnostics only. The current packet has 14 accepted runtime-reference rows; all other rows are
for plotting, validation, and envelope explanation rather than gameplay auto-apply.
