# Acoustic source profile schema v1

## Purpose and boundary

The profile is the only supported route from measured source data into the
runtime order-domain source model. It does not describe propagation, block
materials, distance attenuation, Doppler, or late reverberation.

Runtime resources live at:

```text
assets/<namespace>/acoustic_profiles/<path>.json
```

The JSON `id` must equal `<namespace>:<path>`. Reload is all-or-nothing: an
invalid file rejects the new set and retains the preceding immutable catalog.
No profile resource is currently shipped because no redistributable 5-inch FPV
measurement has passed the holdout gates.

## Exact selection key

Selection uses five synchronized physical fields:

- `airframe_preset`
- `rotor_count`
- `blade_count`
- `rotor_radius_mm`
- `motor_pole_pairs`

Radius is rounded to integer millimetres after network float serialization.
Two profiles may not claim the same key. There is deliberately no "nearest
drone" match: an unknown configuration uses
`fpvdrone:research_unity_fallback`, whose operating-point curve and directivity
are unity and whose coefficients remain explicitly unmeasured.

This key is a deployable minimum, not proof that two propellers with equal
diameter and blade count sound alike. Before profiles for multiple propeller
models of the same geometry are shipped, entity metadata must gain stable
motor/propeller identifiers and schema v2 must extend the key.

## Complete v1 example

The following is structural documentation only, not a calibrated asset:

```json
{
  "schema_version": 1,
  "id": "example:five_inch_test_stand",
  "key": {
    "airframe_preset": "racing_quad",
    "rotor_count": 4,
    "blade_count": 3,
    "rotor_radius_mm": 64,
    "motor_pole_pairs": 7
  },
  "calibration": {
    "measured": true,
    "replaces_legacy_rpm_volume": true,
    "motor_playback_gain_db": -3.0,
    "propeller_playback_gain_db": -1.0
  },
  "validation": {
    "evaluated": true,
    "unseen_rpm_samples": 12,
    "unseen_angle_samples": 18,
    "order_spectrum_samples": 24,
    "maximum_unseen_rpm_error_db": 2.1,
    "maximum_unseen_angle_error_db": 2.7,
    "maximum_order_spectrum_error_db": 1.9
  },
  "evidence": [
    {
      "citation": "Dataset or paper citation",
      "source": "https://example.invalid/dataset",
      "license": "Explicit redistribution and use terms",
      "measurement_conditions": "Microphone distance, angle, room, sample rate, operating point and calibration chain",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
  ],
  "source_model": {
    "blade_pass_harmonics": 12,
    "harmonic_rolloff": 1.15,
    "shaft_amplitude": 0.05,
    "blade_pass_amplitude": 0.18,
    "electrical_amplitude": 0.035,
    "cogging_candidate_amplitude": 0.012,
    "broadband_energy": 0.08,
    "broadband_distribution": {
      "low": 0.2,
      "mid": 0.5,
      "high": 0.3
    },
    "operating_points": [
      {
        "rpm": 8000,
        "rotor_tonal_gain_db": -6,
        "motor_tonal_gain_db": -4,
        "broadband_gain_db": -8
      },
      {
        "rpm": 16000,
        "rotor_tonal_gain_db": 0,
        "motor_tonal_gain_db": 1,
        "broadband_gain_db": 2
      }
    ]
  },
  "directivity": {
    "low_anchor_hz": 150,
    "mid_anchor_hz": 1000,
    "high_anchor_hz": 8000,
    "low": {
      "c2_db": 0,
      "c4_db": 0,
      "minimum_db": 0,
      "maximum_db": 0
    },
    "mid": {
      "c2_db": -2,
      "c4_db": 1,
      "minimum_db": -12,
      "maximum_db": 0
    },
    "high": {
      "c2_db": -4,
      "c4_db": 2,
      "minimum_db": -18,
      "maximum_db": 0
    }
  }
}
```

## Validation and activation rules

- Objects have exact field sets. Unknown, missing and duplicate fields are
  errors; parsing uses strict JSON rather than Gson's lenient object mapping.
- Schema version, identifiers, booleans and integer fields are type checked.
- Every numeric input must be finite; core constructors enforce physical and
  safety ranges.
- RPM anchors are non-empty and strictly increasing. Rotor tonal, motor tonal
  and broadband gains are independent and interpolate in log-RPM/dB space.
- Broadband low/mid/high values are non-negative energy fractions that sum to
  one. One shared broadband RPM gain is applied to this fixed profile spectrum;
  holdout validation therefore catches spectral changes that this runtime model
  cannot reproduce.
- Evidence is non-empty and includes an absolute source URI, citation,
  license, measurement conditions and SHA-256 for every measured input.
- Directivity anchors are strictly increasing. Each even polynomial is
  referenced to 0 dB in the rotor plane and bounded to `[-120, +60] dB`.
- Measured profiles must report at least one unseen-RPM, unseen-angle and
  order-spectrum sample. All three maximum errors must be `<= 3 dB`; these
  constraints are enforced again by the Java constructor, not only by the
  fitting tools.
- `replaces_legacy_rpm_volume` must have the same value as `measured`.
  It replaces the outer Minecraft RPM heuristic with fixed per-layer playback
  gains, preventing a second hidden RPM response.
- NEAPTIDE and NASA values are not product defaults. NEAPTIDE fails the
  cross-aircraft transfer gate and is CC BY-NC; the NASA rotor geometries and
  operating trends do not represent a 5-inch three-blade FPV configuration.

## Required evidence before shipping a measured profile

1. Calibrated pressure or level data with synchronized RPM, rotor/propeller
   identity, microphone geometry, sample rate and measurement-chain metadata.
2. Explicit license compatible with distribution and the intended product use.
3. Unseen-RPM source-level error no worse than 3 dB for rotor tonal, motor
   tonal and broadband groups.
4. Unseen-angle directivity error no worse than 3 dB, including rotor-plane and
   axial observations.
5. Unseen-RPM order-spectrum error no worse than 3 dB for the fitted BPF
   envelope and detected shaft/electrical/twice-electrical ratios.
6. Minecraft A/B validation showing no double application of distance,
   transmission, directivity or RPM gain.
