"""Fit conservative JIRS 2024 near-surface distance curves.

Outputs:
  docs/data/surface_jirs2024_curve_fit_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category surface_jirs2024_curve_fit_*

This packet is intentionally modest: ground/ceiling rows become smooth
extra-thrust curves, while wall rows are fit only as lateral force/moment
scales. The wall data are facility-dependent and noisy, so the output records
R2 and uncertainty ratios instead of turning them into a clean thrust-loss
law.
"""

from __future__ import annotations

import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
INPUT = DATA / "surface_jirs2024_effect_packet.csv"
NEARFIELD = DATA / "surface_nearfield_calibration_packet.csv"
OBSTRUCTION = DATA / "surface_obstruction_geometry_reference.csv"
OUTPUT = DATA / "surface_jirs2024_curve_fit_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

ARTICLE_URL = "https://link.springer.com/article/10.1007/s10846-024-02155-7"
SUPPLEMENT_URL = "https://doi.org/10.5281/zenodo.11384638"
GROUND_CEILING_XS = [0.328084, 0.393701, 0.5, 1.0, 2.0, 4.0, 6.0]
WALL_XS = [1.0, 1.5, 2.0, 3.0]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def value_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def to_float(value: object) -> float:
    try:
        result = float(str(value))
    except (TypeError, ValueError):
        return math.nan
    return result if math.isfinite(result) else math.nan


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    row_list = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in row_list:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in row_list:
            writer.writerow({key: value_text(row.get(key)) for key in fieldnames})


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
    source_url: str = ARTICLE_URL,
    evidence_role: str = "curve_fit_handoff",
    note: str = "",
    **extra: object,
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
            **extra,
        }
    )


def finite(values: Iterable[float]) -> list[float]:
    return [value for value in values if math.isfinite(value)]


def median(values: Iterable[float]) -> float:
    vals = finite(values)
    return statistics.median(vals) if vals else math.nan


def mean(values: Iterable[float]) -> float:
    vals = finite(values)
    return statistics.fmean(vals) if vals else math.nan


def percentile(values: Iterable[float], q: float) -> float:
    vals = sorted(finite(values))
    if not vals:
        return math.nan
    if len(vals) == 1:
        return vals[0]
    pos = (len(vals) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return vals[int(pos)]
    return vals[lo] * (hi - pos) + vals[hi] * (pos - lo)


def fit_exponential(points: list[tuple[float, float]]) -> dict[str, float]:
    valid = [(x, y) for x, y in points if math.isfinite(x) and math.isfinite(y) and x >= 0.0 and y >= 0.0]
    if not valid:
        return {"a": math.nan, "k": math.nan, "rmse": math.nan, "mae": math.nan, "r2": math.nan, "n": 0}

    best: dict[str, float] | None = None
    for step in range(1, 1001):
        k = 0.01 + step * 0.00499
        phis = [math.exp(-k * x) for x, _ in valid]
        denom = sum(phi * phi for phi in phis)
        if denom <= 0.0:
            continue
        a = max(0.0, sum(y * phi for phi, (_, y) in zip(phis, valid)) / denom)
        errors = [a * phi - y for phi, (_, y) in zip(phis, valid)]
        sse = sum(err * err for err in errors)
        if best is None or sse < best["sse"]:
            best = {"a": a, "k": k, "sse": sse}

    if best is None:
        return {"a": math.nan, "k": math.nan, "rmse": math.nan, "mae": math.nan, "r2": math.nan, "n": len(valid)}

    a = best["a"]
    k = best["k"]
    predictions = [a * math.exp(-k * x) for x, _ in valid]
    actuals = [y for _, y in valid]
    errors = [pred - actual for pred, actual in zip(predictions, actuals)]
    rmse = math.sqrt(sum(err * err for err in errors) / len(errors))
    mae = sum(abs(err) for err in errors) / len(errors)
    ybar = sum(actuals) / len(actuals)
    sst = sum((y - ybar) ** 2 for y in actuals)
    r2 = 1.0 - best["sse"] / sst if sst > 0.0 else math.nan
    return {"a": a, "k": k, "rmse": rmse, "mae": mae, "r2": r2, "n": len(valid)}


def predict_extra(fit: dict[str, float], x: float) -> float:
    a = fit["a"]
    k = fit["k"]
    if not (math.isfinite(a) and math.isfinite(k)):
        return math.nan
    return a * math.exp(-k * x)


def measurement_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [row for row in rows if row.get("row_type") == "surface_jirs2024_packet_measurement"]


def uncertainty_lookup(rows: list[dict[str, str]]) -> dict[str, float]:
    lookup: dict[str, float] = {}
    for row in rows:
        if row.get("row_type") != "surface_jirs2024_packet_uncertainty_summary":
            continue
        if row.get("metric") != "uncertainty_p50":
            continue
        lookup[row.get("name", "")] = to_float(row.get("value"))
    return lookup


def add_source_rows(rows: list[dict[str, object]], measurements: list[dict[str, str]]) -> None:
    add_metric(
        rows,
        row_type="surface_jirs2024_curve_fit_source",
        name="jirs2024_surface_curve_fit",
        metric="input_measurement_rows",
        value=len(measurements),
        unit="rows",
        source_file=INPUT,
        source_url=SUPPLEMENT_URL,
        evidence_role="source_inventory",
        note="Derived from parsed JIRS supplementary measurement rows.",
    )
    add_metric(
        rows,
        row_type="surface_jirs2024_curve_fit_method",
        name="fit_method",
        metric="ground_ceiling_model",
        value="multiplier = 1 + A * exp(-k * h_over_R), using median same-PWM far-normalized Fz ratios by h/R and clipped nonnegative extra lift",
        unit="text",
        source_file=OUTPUT,
        source_url=ARTICLE_URL,
        evidence_role="method",
        note="This keeps far-field behavior at 1.0 and avoids overfitting the slight below-baseline bins.",
    )
    add_metric(
        rows,
        row_type="surface_jirs2024_curve_fit_method",
        name="fit_method",
        metric="wall_model",
        value="abs(force_or_moment) = A * exp(-k * d_over_R), fit separately for terraXcube, DU2SRI, and pooled rows",
        unit="text",
        source_file=OUTPUT,
        source_url=ARTICLE_URL,
        evidence_role="method",
        note="Wall fits are distance-scale evidence for attraction/moment, not clean whole-vehicle thrust-loss fits.",
    )


def add_ground_ceiling_fits(rows: list[dict[str, object]], measurements: list[dict[str, str]]) -> dict[str, dict[str, float]]:
    fits: dict[str, dict[str, float]] = {}
    for effect in ("ground", "ceiling"):
        buckets: dict[float, list[float]] = defaultdict(list)
        for row in measurements:
            if row.get("effect") != effect:
                continue
            distance = to_float(row.get("distance_over_radius"))
            ratio = to_float(row.get("fz_over_far_same_pwm"))
            if math.isfinite(distance) and math.isfinite(ratio):
                buckets[round(distance, 6)].append(ratio)

        fit_points: list[tuple[float, float]] = []
        for distance in sorted(buckets):
            values = buckets[distance]
            p50 = median(values)
            extra = max(0.0, p50 - 1.0)
            fit_points.append((distance, extra))
            add_metric(
                rows,
                row_type="surface_jirs2024_curve_fit_bin",
                name=f"{effect}_h_over_r_{distance:g}",
                metric="fz_ratio_p50",
                value=p50,
                unit="ratio",
                source_file=INPUT,
                source_url=SUPPLEMENT_URL,
                evidence_role="ground_ceiling_bin",
                note="Median same-PWM Fz ratio versus 100 cm far baseline at this normalized clearance.",
                effect=effect,
                distance_over_radius=distance,
                sample_count=len(values),
                fz_ratio_min=min(values),
                fz_ratio_p90=percentile(values, 0.9),
                fz_ratio_max=max(values),
                fit_y_extra=max(0.0, p50 - 1.0),
            )

        fit = fit_exponential(fit_points)
        fits[effect] = fit
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_model",
            name=f"{effect}_extra_lift_exp_fit",
            metric="model_a",
            value=fit["a"],
            unit="extra ratio",
            source_file=INPUT,
            source_url=SUPPLEMENT_URL,
            evidence_role="ground_ceiling_fit",
            note="A parameter in multiplier = 1 + A * exp(-k * h/R).",
            effect=effect,
            fit_family="extra_lift_exponential",
            fit_a=fit["a"],
            fit_k=fit["k"],
            fit_rmse=fit["rmse"],
            fit_mae=fit["mae"],
            fit_r2=fit["r2"],
            fit_sample_count=fit["n"],
        )
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_model",
            name=f"{effect}_extra_lift_exp_fit",
            metric="model_k",
            value=fit["k"],
            unit="1/(h/R)",
            source_file=INPUT,
            source_url=SUPPLEMENT_URL,
            evidence_role="ground_ceiling_fit",
            note="k parameter in multiplier = 1 + A * exp(-k * h/R).",
            effect=effect,
            fit_family="extra_lift_exponential",
            fit_a=fit["a"],
            fit_k=fit["k"],
            fit_rmse=fit["rmse"],
            fit_mae=fit["mae"],
            fit_r2=fit["r2"],
            fit_sample_count=fit["n"],
        )
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_model",
            name=f"{effect}_extra_lift_exp_fit",
            metric="fit_r2",
            value=fit["r2"],
            unit="R2",
            source_file=INPUT,
            source_url=SUPPLEMENT_URL,
            evidence_role="ground_ceiling_fit",
            note="Goodness of fit over median h/R bins.",
            effect=effect,
            fit_family="extra_lift_exponential",
            fit_a=fit["a"],
            fit_k=fit["k"],
            fit_rmse=fit["rmse"],
            fit_mae=fit["mae"],
            fit_r2=fit["r2"],
            fit_sample_count=fit["n"],
        )

        for distance in GROUND_CEILING_XS:
            extra = predict_extra(fit, distance)
            add_metric(
                rows,
                row_type="surface_jirs2024_curve_fit_prediction",
                name=f"{effect}_h_over_r_{distance:g}",
                metric="predicted_multiplier",
                value=1.0 + extra,
                unit="multiplier",
                source_file=INPUT,
                source_url=SUPPLEMENT_URL,
                evidence_role="ground_ceiling_prediction",
                note="Conservative smooth prediction from the JIRS far-normalized Fz ratios.",
                effect=effect,
                distance_over_radius=distance,
                fit_family="extra_lift_exponential",
                predicted_extra_lift=extra,
                fit_a=fit["a"],
                fit_k=fit["k"],
                fit_r2=fit["r2"],
            )

    return fits


def current_ground_ceiling(rows: list[dict[str, str]]) -> dict[tuple[str, float], float]:
    result: dict[tuple[str, float], float] = {}
    for row in rows:
        if row.get("row_type") != "surface_nearfield_current_ground_ceiling":
            continue
        name = row.get("name", "")
        if not name.startswith("racingQuad_h_over_r_"):
            continue
        distance = to_float(name.replace("racingQuad_h_over_r_", ""))
        metric = row.get("metric", "")
        if metric == "current_ground_multiplier":
            result[("ground", distance)] = to_float(row.get("value"))
        elif metric == "current_ceiling_multiplier":
            result[("ceiling", distance)] = to_float(row.get("value"))
    return result


def add_ground_ceiling_comparisons(
    rows: list[dict[str, object]],
    nearfield_rows: list[dict[str, str]],
    fits: dict[str, dict[str, float]],
) -> None:
    current = current_ground_ceiling(nearfield_rows)
    for effect in ("ground", "ceiling"):
        fit = fits[effect]
        for distance in (0.5, 1.0, 2.0, 4.0, 6.0):
            current_value = current.get((effect, distance), math.nan)
            fitted = 1.0 + predict_extra(fit, distance)
            ratio = current_value / fitted if fitted and math.isfinite(current_value) else math.nan
            extra_ratio = (current_value - 1.0) / (fitted - 1.0) if fitted > 1.0 and math.isfinite(current_value) else math.nan
            add_metric(
                rows,
                row_type="surface_jirs2024_curve_fit_runtime_compare",
                name=f"racingQuad_{effect}_h_over_r_{distance:g}",
                metric="current_over_jirs_fit_multiplier",
                value=ratio,
                unit="ratio",
                source_file=NEARFIELD,
                source_url=SUPPLEMENT_URL,
                evidence_role="runtime_comparison",
                note="Compares current runtime multiplier with the conservative JIRS curve-fit multiplier.",
                effect=effect,
                distance_over_radius=distance,
                current_value=current_value,
                source_fit_value=fitted,
                current_extra_lift=current_value - 1.0 if math.isfinite(current_value) else math.nan,
                source_fit_extra_lift=fitted - 1.0 if math.isfinite(fitted) else math.nan,
                current_extra_over_fit_extra=extra_ratio,
                ratio=ratio,
            )


def wall_key(row: dict[str, str]) -> str:
    facility = row.get("facility", "")
    if facility == "txc":
        return "WallEffect_txc.mat"
    if facility == "DU2SRI":
        return "WallEffect_du.mat"
    return ""


def wall_uncertainty_name(row: dict[str, str], metric: str) -> str:
    base = wall_key(row)
    if not base:
        return ""
    if metric == "abs_wall_force_n":
        return f"{base}_Fhtal"
    if metric == "abs_wall_moment_nm":
        return f"{base}_Mhtal"
    return ""


def add_wall_fits(
    rows: list[dict[str, object]],
    measurements: list[dict[str, str]],
    uncertainties: dict[str, float],
) -> dict[tuple[str, str], dict[str, float]]:
    wall_rows = [row for row in measurements if row.get("effect") == "wall"]
    fits: dict[tuple[str, str], dict[str, float]] = {}
    facility_scopes = ["txc", "DU2SRI", "all"]
    metric_specs = [
        ("abs_wall_force_n", "abs_force", "N"),
        ("abs_wall_moment_nm", "abs_moment", "N*m"),
    ]

    for facility in facility_scopes:
        scoped = wall_rows if facility == "all" else [row for row in wall_rows if row.get("facility") == facility]
        for metric, metric_name, unit in metric_specs:
            buckets: dict[float, list[float]] = defaultdict(list)
            signed_buckets: dict[float, list[float]] = defaultdict(list)
            for row in scoped:
                distance = to_float(row.get("distance_over_radius"))
                value = to_float(row.get(metric))
                signed_metric = "wall_force_n" if metric == "abs_wall_force_n" else "wall_moment_nm"
                signed_value = to_float(row.get(signed_metric))
                if math.isfinite(distance) and math.isfinite(value):
                    buckets[round(distance, 6)].append(value)
                if math.isfinite(distance) and math.isfinite(signed_value):
                    signed_buckets[round(distance, 6)].append(signed_value)

            points: list[tuple[float, float]] = []
            for distance in sorted(buckets):
                values = buckets[distance]
                signed_values = signed_buckets.get(distance, [])
                p50 = median(values)
                points.append((distance, p50))
                add_metric(
                    rows,
                    row_type="surface_jirs2024_curve_fit_bin",
                    name=f"wall_{facility}_{metric_name}_d_over_r_{distance:g}",
                    metric=f"{metric_name}_p50",
                    value=p50,
                    unit=unit,
                    source_file=INPUT,
                    source_url=SUPPLEMENT_URL,
                    evidence_role="wall_bin",
                    note="Median absolute wall force/moment by normalized wall distance.",
                    effect="wall",
                    facility=facility,
                    distance_over_radius=distance,
                    sample_count=len(values),
                    signed_p50=median(signed_values),
                    abs_min=min(values),
                    abs_p90=percentile(values, 0.9),
                    abs_max=max(values),
                )

            fit = fit_exponential(points)
            fits[(facility, metric_name)] = fit
            for fit_metric, fit_value, fit_unit, fit_note in (
                ("model_a", fit["a"], unit, "A parameter in abs(force_or_moment) = A * exp(-k * d/R)."),
                ("model_k", fit["k"], "1/(d/R)", "k parameter in abs(force_or_moment) = A * exp(-k * d/R)."),
                ("fit_r2", fit["r2"], "R2", "Goodness of fit over median d/R bins; low values mean use as a weak distance-scale cue only."),
            ):
                add_metric(
                    rows,
                    row_type="surface_jirs2024_curve_fit_model",
                    name=f"wall_{facility}_{metric_name}_exp_fit",
                    metric=fit_metric,
                    value=fit_value,
                    unit=fit_unit,
                    source_file=INPUT,
                    source_url=SUPPLEMENT_URL,
                    evidence_role="wall_fit",
                    note=fit_note,
                    effect="wall",
                    facility=facility,
                    fit_family="absolute_wall_exponential",
                    fit_a=fit["a"],
                    fit_k=fit["k"],
                    fit_rmse=fit["rmse"],
                    fit_mae=fit["mae"],
                    fit_r2=fit["r2"],
                    fit_sample_count=fit["n"],
                )

            uncertainty_key = f"WallEffect_txc.mat_{'Fhtal' if metric_name == 'abs_force' else 'Mhtal'}" if facility == "txc" else ""
            if facility == "DU2SRI":
                uncertainty_key = f"WallEffect_du.mat_{'Fhtal' if metric_name == 'abs_force' else 'Mhtal'}"
            uncertainty_p50 = uncertainties.get(uncertainty_key, math.nan)
            for distance in WALL_XS:
                pred = predict_extra(fit, distance)
                signal_over_unc = pred / uncertainty_p50 if uncertainty_p50 and math.isfinite(uncertainty_p50) else math.nan
                add_metric(
                    rows,
                    row_type="surface_jirs2024_curve_fit_prediction",
                    name=f"wall_{facility}_{metric_name}_d_over_r_{distance:g}",
                    metric=f"predicted_{metric_name}",
                    value=pred,
                    unit=unit,
                    source_file=INPUT,
                    source_url=SUPPLEMENT_URL,
                    evidence_role="wall_prediction",
                    note="Distance-scale prediction from median absolute wall rows; use with the fit R2 and uncertainty fields.",
                    effect="wall",
                    facility=facility,
                    distance_over_radius=distance,
                    fit_family="absolute_wall_exponential",
                    fit_a=fit["a"],
                    fit_k=fit["k"],
                    fit_r2=fit["r2"],
                    uncertainty_p50=uncertainty_p50,
                    signal_over_uncertainty_p50=signal_over_unc,
                )

    for key, value in sorted(uncertainties.items()):
        if not key.startswith("WallEffect_"):
            continue
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_uncertainty",
            name=key,
            metric="source_uncertainty_p50",
            value=value,
            unit="N" if key.endswith("_Fhtal") else "N*m",
            source_file=INPUT,
            source_url=SUPPLEMENT_URL,
            evidence_role="wall_uncertainty",
            note="Direct p50 uncertainty parsed from the source MAT uncertainty arrays.",
            effect="wall",
            facility="txc" if "_txc." in key else "DU2SRI",
        )

    return fits


def current_wall_mapping(rows: list[dict[str, str]]) -> dict[float, dict[str, float]]:
    result: dict[float, dict[str, float]] = {}
    for row in rows:
        if row.get("row_type") != "current_flat_wall_runtime_mapping" or row.get("preset") != "racingQuad":
            continue
        distance = to_float(row.get("clearance_over_r"))
        if not math.isfinite(distance):
            continue
        result[round(distance, 6)] = {
            "wall_force_per_affected_rotor_n": to_float(row.get("wall_force_per_affected_rotor_n")),
            "two_affected_rotors_wall_force_n": to_float(row.get("two_affected_rotors_wall_force_n")),
            "two_affected_rotors_wall_force_over_weight": to_float(row.get("two_affected_rotors_wall_force_over_weight")),
            "current_runtime_thrust_multiplier_per_affected_rotor": to_float(row.get("current_runtime_thrust_multiplier_per_affected_rotor")),
            "two_affected_rotors_vehicle_thrust_multiplier": to_float(row.get("two_affected_rotors_vehicle_thrust_multiplier")),
        }
    return result


def add_wall_runtime_comparisons(
    rows: list[dict[str, object]],
    obstruction_rows: list[dict[str, str]],
    fits: dict[tuple[str, str], dict[str, float]],
) -> None:
    mapping = current_wall_mapping(obstruction_rows)
    fit = fits.get(("all", "abs_force"), {})
    for distance in (1.0, 2.0, 3.0):
        current = mapping.get(round(distance, 6), {})
        source_fit = predict_extra(fit, distance) if fit else math.nan
        two_force = current.get("two_affected_rotors_wall_force_n", math.nan)
        per_force = current.get("wall_force_per_affected_rotor_n", math.nan)
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_runtime_compare",
            name=f"racingQuad_wall_d_over_r_{distance:g}",
            metric="two_affected_rotors_force_over_jirs_all_fit",
            value=two_force / source_fit if source_fit and math.isfinite(two_force) else math.nan,
            unit="ratio",
            source_file=OBSTRUCTION,
            source_url=SUPPLEMENT_URL,
            evidence_role="runtime_comparison",
            note="Current two-affected-rotor wall-force proxy compared with pooled JIRS absolute wall-force fit; geometry is not one-to-one.",
            effect="wall",
            facility="all",
            distance_over_radius=distance,
            current_value=two_force,
            current_per_affected_rotor_value=per_force,
            source_fit_value=source_fit,
            current_per_affected_rotor_over_fit=per_force / source_fit if source_fit and math.isfinite(per_force) else math.nan,
            current_two_affected_force_over_weight=current.get("two_affected_rotors_wall_force_over_weight", math.nan),
            current_two_affected_vehicle_thrust_multiplier=current.get("two_affected_rotors_vehicle_thrust_multiplier", math.nan),
            source_fit_r2=fit.get("r2", math.nan),
        )


def add_summary_rows(
    rows: list[dict[str, object]],
    ground_ceiling_fits: dict[str, dict[str, float]],
    wall_fits: dict[tuple[str, str], dict[str, float]],
) -> None:
    def runtime_metric(name: str, metric: str) -> float:
        for row in rows:
            if row.get("row_type") == "surface_jirs2024_curve_fit_runtime_compare" and row.get("name") == name and row.get("metric") == metric:
                return to_float(row.get("value"))
        return math.nan

    summary_specs = [
        (
            "ground_fit_hR1_multiplier",
            1.0 + predict_extra(ground_ceiling_fits["ground"], 1.0),
            "multiplier",
            "JIRS ground-effect fitted multiplier at h/R=1.",
        ),
        (
            "ceiling_fit_hR1_multiplier",
            1.0 + predict_extra(ground_ceiling_fits["ceiling"], 1.0),
            "multiplier",
            "JIRS ceiling-effect fitted multiplier at h/R=1.",
        ),
        (
            "racingQuad_ground_hR1_current_over_jirs_fit",
            runtime_metric("racingQuad_ground_h_over_r_1", "current_over_jirs_fit_multiplier"),
            "ratio",
            "Current racingQuad ground multiplier divided by the JIRS h/R=1 fitted multiplier.",
        ),
        (
            "racingQuad_ceiling_hR1_current_over_jirs_fit",
            runtime_metric("racingQuad_ceiling_h_over_r_1", "current_over_jirs_fit_multiplier"),
            "ratio",
            "Current racingQuad ceiling multiplier divided by the JIRS h/R=1 fitted multiplier.",
        ),
        (
            "wall_all_abs_force_fit_dR1",
            predict_extra(wall_fits[("all", "abs_force")], 1.0),
            "N",
            "Pooled JIRS absolute wall-force fit at d/R=1.",
        ),
        (
            "wall_all_abs_force_fit_r2",
            wall_fits[("all", "abs_force")]["r2"],
            "R2",
            "Pooled wall-force fit quality; low R2 means this is only a weak distance-scale cue.",
        ),
        (
            "racingQuad_wall_dR1_two_force_over_jirs_all_fit",
            runtime_metric("racingQuad_wall_d_over_r_1", "two_affected_rotors_force_over_jirs_all_fit"),
            "ratio",
            "Current two-affected-rotor wall force divided by pooled JIRS d/R=1 force fit.",
        ),
    ]

    for metric, value, unit, note in summary_specs:
        add_metric(
            rows,
            row_type="surface_jirs2024_curve_fit_summary",
            name="jirs2024_curve_fit_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SUPPLEMENT_URL,
            evidence_role="compact_surface_fit_handoff",
            note=note,
        )

    add_metric(
        rows,
        row_type="surface_jirs2024_curve_fit_method",
        name="handoff_guidance",
        metric="recommended_use",
        value=(
            "Use ground/ceiling fits as conservative thrust-multiplier targets; use wall fits as attraction/moment "
            "distance-scale evidence with facility-aware uncertainty. Do not map wall force directly to clean "
            "vehicle-wide thrust loss without a separate obstruction or recirculation assumption."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SUPPLEMENT_URL,
        evidence_role="handoff_guidance",
        note="The pooled wall force fit has weak explanatory power; terraXcube force is cleaner than DU2SRI force in this packet.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("surface_jirs2024_curve_fit_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row["metric"]),
                "value": value_text(row["value"]),
                "unit": str(row["unit"]),
                "source": str(row.get("source_url") or row.get("source_file", "")),
                "source_file": str(row.get("source_file", "")),
                "source_url": str(row.get("source_url", "")),
                "evidence_role": str(row.get("evidence_role", "")),
                "note": str(row.get("note", "")),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    source_rows = read_rows(INPUT)
    measurements = measurement_rows(source_rows)
    nearfield_rows = read_rows(NEARFIELD)
    obstruction_rows = read_rows(OBSTRUCTION)
    uncertainties = uncertainty_lookup(source_rows)

    rows: list[dict[str, object]] = []
    add_source_rows(rows, measurements)
    ground_ceiling_fits = add_ground_ceiling_fits(rows, measurements)
    add_ground_ceiling_comparisons(rows, nearfield_rows, ground_ceiling_fits)
    wall_fits = add_wall_fits(rows, measurements, uncertainties)
    add_wall_runtime_comparisons(rows, obstruction_rows, wall_fits)
    add_summary_rows(rows, ground_ceiling_fits, wall_fits)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
