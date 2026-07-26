# Calibrated recording analysis pipeline

## Scope

`tools/acoustics/analyze_recording.py` is the deterministic bridge between a
steady motor/propeller bench measurement and the v1 profile fitter. It is not a
blind drone detector and does not estimate RPM from audio. Every analyzed
segment requires synchronized tach/ESC RPM telemetry.

The current implementation targets static, nearly steady operating points. RPM
ramps, forward-flight load changes, gust AM/FM, damaged blades and wet
propellers remain separate future descriptor families.

## Pressure calibration

For calibrator level `L_cal`:

```text
p_cal,rms = 20 µPa × 10^(L_cal / 20)
C_pa = p_cal,rms / sqrt(integral tone PSD_normalized df)
p(t) = C_pa × x_normalized(t)
```

The calibrator tone is integrated only inside its configured frequency window;
DC and unrelated background do not become part of the sensitivity estimate.
The WAV is decoded as normalized PCM from uncompressed 8/16/24/32-bit integer
samples. Neither calibration nor measurements are resampled.

## Spectrum and background

The research default reproduces the NASA small-rotor analysis settings:

- 5 Hz frequency resolution;
- Hann window;
- 75% overlap;
- ±40 Hz windows around modeled tones.

The one-sided PSD uses window-energy and sample-rate normalization. A separate
background recording from the same channel and gain setting is analyzed on the
same frequency grid:

```text
G_residual(f) = max(G_signal(f) - G_background(f), 0)
```

The manifest records microphone and signal-chain IDs so a background captured
with a different gain cannot be presented as the same measurement chain
without leaving visible evidence. Overall 20 Hz–20 kHz SNR must meet the
configured gate.

It also records `airframe_id` and a controlled `source_configuration`
(`single_rotor_bench`, `full_airframe_bench`, or `free_flight`). This prevents
isolated-rotor evidence from being silently relabeled as a complete-airframe
measurement.

## RPM/order decomposition

The segment RPM is the median of at least five strictly time-ordered telemetry
samples. The population coefficient of variation must remain below the
configured threshold, normally 2.5%.

With shaft frequency `f_s = RPM / 60`:

```text
rotor centers = h × bladeCount × f_s
motor centers = f_s, polePairs × f_s, 2 × polePairs × f_s
```

Only centers below `0.45 × sampleRate` are used, matching the runtime source
model's Nyquist guard. No two integration windows may overlap; ambiguous
configurations fail instead of double-counting energy.

For each individual order, the analyzer estimates a local residual-noise floor
from clean sidebands 1.5–3.5 window half-widths away. That floor is subtracted
from the order window. The report preserves background SNR, local prominence,
isolated 1 m level and `detected`; aggregate rotor/motor levels sum only
detected isolated powers. An expected-but-absent harmonic therefore does not
become tonal energy merely because broadband noise occupies its window.

All modeled tone windows, detected or not, are removed before residual PSD is
integrated into 20–300 Hz, 300–3200 Hz and 3200–20000 Hz broadband bands.

The electrical and twice-electrical values remain candidate labels. A spectral
peak inside those windows is not by itself proof of electromagnetic or cogging
origin; component-isolation experiments are still required.

## Reference level

For microphone distance `r` metres:

```text
L_1m = L_measured + 20 log10(r / 1 m) + correction_db
```

`reference_level_correction_db` exists for a documented measurement-chain or
ground-reflection correction. It is copied into the report and must never be
used as an informal listening adjustment.

## Rejection gates

Analysis fails without publishing a descriptor CSV when any of these occurs:

- missing, non-finite or out-of-range manifest data;
- unsupported/compressed WAV, invalid channel or truncated segment;
- peak above the configured capture gate;
- non-integer Welch segment for the requested resolution;
- fewer than two Welch frames;
- signal/background sample-rate mismatch;
- analysis-band SNR below threshold;
- fewer than five RPM samples, nonmonotonic timestamps or excessive RPM CV;
- any overlapping order windows;
- no BPF or no motor order remaining after the prominence gate; individual
  weak orders are otherwise retained as nondetections;
- empty/non-positive tonal or broadband energy after background subtraction.

Every accepted calibration, signal WAV, background WAV and RPM CSV is SHA-256
hashed. The deterministic report also hashes the manifest and final descriptor
CSV, enabling the fitter evidence to bind the exact derived input.

## Verified synthetic chain

The regression fixture uses real 24-bit PCM encoding, a 94 dB/1 kHz calibrator,
synchronized RPM, known BPF/electrical tones and three band probes. It verifies:

- signed 24-bit endpoint decoding;
- known tone recovery within 0.25 dB after 2 m normalization and a -1 dB
  documented correction;
- deterministic byte output;
- low-SNR, excessive RPM variation, nonmonotonic tach, peak and overlapping
  order rejection.

The full synthetic pipeline contains 18 recordings: 12 independent training
rows and 6 independent validation rows. Analyzer → fitter produced maximum
unseen-RPM error `0.000013878 dB` and maximum unseen-angle error
`0.000002508 dB`; the resulting JSON then passed the actual Java runtime
decoder with 3 RPM anchors, 2 unseen-RPM rows and 4 unseen-angle rows.

This proves the software/data contracts and numerical conventions. It is not
evidence that a real 5-inch FPV profile has passed.

## Capture-session preflight

`tools/acoustics/plan_capture_session.py` fixes the RPM/angle/take matrix before
recording and materializes the strict analyzer manifest only after every
calibration, background, signal and tach file exists. It checks unseen
interior RPM holdouts, unseen axis-cosine angles, positive/negative hemisphere
coverage, Nyquist guard and all planned tonal-window collisions. The default
35-recording matrix and commands are documented in
[`decision-D068-target-capture-session-preflight.md`](decision-D068-target-capture-session-preflight.md).
