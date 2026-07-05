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
