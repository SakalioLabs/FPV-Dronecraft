# CT/CP/J Playable Reference Note 2026-07-03

`sim/lab` now has a callable CT/CP/J lookup evaluator for the accepted `apDrone` reference windows:
static anchor, mid-domain bilinear, and high-advance single-RPM edge. These rows can be used as low-cost
playable reference material only for curve shape checks: CT decreases with advance ratio, CP rises toward the
high-advance edge, and dimensional thrust/power/torque follow the SI propeller coefficient equations.

Do not auto-apply these rows to `playable/dev` tuning yet. They are suitable for simplified force/power curve
prototyping and controller-feel comparison, but not for full propeller replacement, OpenFOAM coupling, or
terrain/voxel airflow tuning until more reviewed CT/CP/J rows and offline CFD comparisons are available.
