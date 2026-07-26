# Acoustic profile fitting tools

## Capture-session planning

Before recording, generate a fixed RPM/angle/take matrix:

```powershell
python tools/acoustics/plan_capture_session.py generate `
  --config-json tools/acoustics/capture-session-config-v1.example.json `
  --output-plan-json external-data/computational-acoustics/5inch-session/capture-plan.json `
  --output-results-csv external-data/computational-acoustics/5inch-session/capture-results.csv
```

The planner uses disjoint interior RPM and axis-cosine holdouts, captures both
hemispheres, and rejects Nyquist or tonal-window conflicts before data
collection. After all actual load/environment values and files exist, run the
`materialize` subcommand shown in
`docs/acoustics/decision-D068-target-capture-session-preflight.md`. It creates
the exact analyzer manifest plus a plan/results/manifest hash report. Planned
targets never become measured RPM automatically.

## Processed spectral references

Published frequency-bin autopower is analyzed by
`docs/scripts/analyze_spectral_reference.py`, not by the raw-recording
pipeline below. The adapter validates the Recherche Data Gouv HDF5 axes,
extracts sideband-corrected BPF harmonics, per-harmonic RPM trends and
band-dependent directivity. Its report permanently disables release-profile
eligibility, PCM synthesis and profile fitting because processed Pa² bins do
not contain time history, phase, transients or synchronized tach.

The pinned 4.3 MB example can be fetched without downloading the full dataset:

```powershell
python docs/scripts/fetch_serration_spectral_reference.py `
  external-data/computational-acoustics/serration-reference
```

The HDF5 reader needs optional `h5py==3.12.1`. Use a dedicated environment if
installing it would otherwise change the project's NumPy/SciPy versions.
Source contract, real results and the complete command are documented in
`docs/acoustics/decision-D066-processed-autopower-spectral-reference.md`.

Two reports with identical coordinate and analysis contracts can be paired:

```powershell
python docs/scripts/compare_spectral_references.py `
  --baseline-report build/research/serration-straight-b-spectral-reference-v1.json `
  --candidate-report build/research/serration-straight-bt-spectral-reference-v1.json `
  --baseline-label straight_B `
  --candidate-label straight_tripped_BT `
  --relationship boundary-layer-tripping-straight-rotor `
  --output-json build/research/serration-straight-b-vs-bt-paired-v1.json
```

The comparator rejects mismatched RPM/angle/frequency/normalization contracts,
reports inclusive and BPF-removed band deltas, and preserves the same closed
release gates. Real paired results are in
`docs/acoustics/decision-D067-paired-autopower-deltas.md`.

## Recording analysis

`analyze_recording.py` converts steady, calibrated bench recordings and
synchronized tach telemetry into the descriptor CSV consumed by the fitter.
The manifest schema is illustrated by
`recording-manifest-v1.example.json`.

```powershell
python tools/acoustics/analyze_recording.py `
  --manifest-json build/acoustics/recording-manifest.json `
  --output-csv build/acoustics/measurements.csv `
  --output-report-json build/acoustics/measurement-report.json
```

The analyzer:

- reads mono channels from uncompressed 8/16/24/32-bit PCM WAV without
  resampling;
- derives Pa/sample from a known acoustic-calibrator tone;
- uses 5 Hz Hann Welch spectra with 75% overlap by default;
- subtracts a separately recorded background PSD;
- integrates BPF harmonics and shaft/electrical/2×electrical candidate windows;
- subtracts a local sideband floor for every individual order and records
  background SNR, local prominence, isolated 1 m level and detection state;
- removes all modeled tone windows before integrating low/mid/high broadband;
- normalizes pressure level to 1 m and records any explicit measurement-chain
  correction separately;
- rejects low SNR, excessive RPM variation, nonmonotonic tach timestamps,
  clipping/peak violations and any overlapping order windows;
- hashes calibration, measurement, background, tach, manifest and descriptor
  files in the deterministic report.

Every recording also records airframe, motor, propeller, microphone and
signal-chain IDs; a controlled `source_configuration` (`single_rotor_bench`,
`full_airframe_bench`, or `free_flight`); thrust, voltage, current,
temperature, humidity and pressure. These are provenance today and inputs for
later installation/load/environment models; they are not silently folded into
current source gains.

`reference_level_correction_db` must come from a documented measurement-chain
or ground-reflection correction. It is not a listening-tune control.

## Order-model fitting

`fit_order_source_model.py` consumes the analyzer's deterministic JSON report,
not a hand-edited spectrum. It fits the largest consecutive BPF harmonic set
present in every training rotor-plane recording, harmonic pressure rolloff,
shaft/electrical/twice-electrical relative amplitudes and broadband energy at
the reference RPM. Every plane record must analyze the same consecutive BPF
candidate orders, including at least one nondetected candidate above the fitted
cutoff. Validation rotor-plane recordings must use unseen RPMs; a newly
detected higher harmonic is a release-gate failure.

```powershell
python tools/acoustics/fit_order_source_model.py `
  --analysis-report-json build/acoustics/measurement-report.json `
  --reference-rpm 12000 `
  --plane-max-elevation-deg 2 `
  --output-json build/acoustics/order-model.json
```

Exit `0` requires both training and independent order-spectrum maximum error
to be `<=3 dB`. Exit `1` writes the failed model for diagnosis; it cannot be
consumed by the profile fitter.

## Profile fitting

`fit_acoustic_profile.py` converts calibrated, 1 m source descriptors into the
strict runtime schema documented in
`docs/acoustics/acoustic-profile-schema-v1.md`.

It does not analyze raw WAV files or guess RPM. Run raw-audio calibration and
order/band extraction first, preserving the original recording and its hash.

### Measurement CSV

The header is exact and ordered:

```text
recording_id,maneuver_id,split,rpm,elevation_deg,rotor_tonal_db,motor_tonal_db,broadband_low_db,broadband_mid_db,broadband_high_db
```

All levels are calibrated dB re 20 µPa at a 1 m reference distance:

- `rotor_tonal_db`: energy-summed BPF/order group;
- `motor_tonal_db`: energy-summed shaft/electrical/cogging group;
- broadband columns: mutually exclusive low/mid/high band energies;
- `elevation_deg`: angle from the rotor plane; 0° is in-plane and ±90° is axial.

Training and validation may not share a `recording_id` or `maneuver_id`.
Validation must contain unseen, in-range rotor-plane RPM values and unseen
off-plane angles. This prevents samples from the same recording or maneuver
from appearing on both sides of the release gate.

### Metadata input

Metadata contains the intended v1 `id`, physical `key`, playback calibration,
evidence, and fit settings. It contains no hand-entered `source_model`.
Every evidence item requires a SHA-256; at least one must equal the measurement
CSV hash and at least one must equal the analyzer report hash embedded in the
order-model artifact.
Fitted profiles must set both `measured` and
`replaces_legacy_rpm_volume` to true.

The harmonic count/rolloff and shaft/electrical/twice-electrical amplitudes are
copied only from a passing `fit_order_source_model.py` artifact. The profile
fitter strictly checks its schema, 3 dB gate, matching reference RPM and plane
limit, and evidence hash binding. Passing these gates still does not replace
the final renderer spectral golden and Minecraft A/B checks.

`fit_settings` fields are:

- `reference_rpm`: exact training plane anchor used as 0 dB operating point;
- `plane_max_elevation_deg`: at most 10°, defining plane observations;
- `low_anchor_hz`, `mid_anchor_hz`, `high_anchor_hz`: directivity anchors.

### Command and exit codes

```powershell
python tools/acoustics/fit_acoustic_profile.py `
  --measurements-csv build/acoustics/measurements.csv `
  --metadata-json build/acoustics/profile-metadata.json `
  --order-model-json build/acoustics/order-model.json `
  --output-profile-json build/acoustics/profile.json `
  --output-report-json build/acoustics/profile-report.json
```

- `0`: unseen-RPM, unseen-angle and order-spectrum maximum errors are all
  `<= 3 dB`; profile and report were atomically written.
- `1`: the validation gate failed; only the report was written and
  `profile_written=false`.
- `2`: the input contract was invalid.

Every candidate that passes the Python gate must then pass the actual Java
runtime decoder and constructor:

```powershell
.\gradlew.bat :fabric-mod:verifyAcousticProfileFile `
  "-Pprofile=build/acoustics/profile.json"
```

Operating points use energy means, then log-RPM/dB interpolation. The
low/mid/high energy distribution is fitted at the reference RPM, while one
shared broadband RPM gain drives all three bands exactly as the runtime does.
Consequently, band-specific spectral drift appears as holdout error instead of
being hidden in an unrealizable fit. Directivity fits the runtime even
polynomial `c2*mu² + c4*mu⁴`, where `mu=abs(sin(elevation))`. The report records
counts, errors, input CSV, analyzer report, order-model and exact generated
profile hashes.
