"""Build APdrone Mendeley file inventory and selected-flight summary.

Outputs:
  docs/data/apdrone_mendeley_file_inventory.csv
  docs/data/apdrone_selected_flight_reference.csv
  docs/data/apdrone_inertia_reference.csv
  docs/data/apdrone_pid_tuning_reference.csv
  docs/data/apdrone_component_specs_reference.csv
  docs/data/apdrone_battery_autonomy_reference.csv
  docs/data/apdrone_flight_archive_reference.csv
  docs/data/apdrone_open_field_speed_current_bins_reference.csv
  docs/data/apdrone_flight_vs_model_reference.csv
  docs/data/apdrone_drag_speed_envelope_reference.csv
  docs/data/apdrone_preset_source_match_reference.csv
  docs/data/apdrone_article_performance_reference.csv
  docs/data/apdrone_battery_esr_proxy_reference.csv

The script only downloads the small Betaflight text dump and one selected
Blackbox CSV, one small inertia PDF, small PID tuning summary CSVs, and
selected component datasheets. It also summarizes the APdrone battery autonomy
and real-flight RAR archives when they are present/extractable. Large videos
and unrelated RAR archives are inventoried but not mirrored.
"""

from __future__ import annotations

import csv
import json
import math
import re
import shutil
import statistics
import subprocess
import time
import urllib.request
from pathlib import Path

from airframe_runtime_drag_law import drag_force, equivalent_cda, equivalent_quadratic_c, terminal_speed_m_s


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "apdrone_zgsvdtxnfh_v2"

DATASET_ID = "zgsvdtxnfh"
DATASET_VERSION = 2
DOI = "10.17632/zgsvdtxnfh.2"
SOURCE_PAGE = f"https://data.mendeley.com/datasets/{DATASET_ID}/{DATASET_VERSION}"
ARTICLE_PAGE = "https://www.sciencedirect.com/science/article/pii/S2468067225000501"
ARTICLE_DOI = "10.1016/j.ohx.2025.e00672"
PUBLIC_API_BASE = f"https://data.mendeley.com/public-api/datasets/{DATASET_ID}"
PROJECT_DRAG_REFERENCE_SPEED_M_S = 10.0
HEADERS = {
    "Accept": "application/vnd.mendeley-public-dataset.1+json",
    "User-Agent": "Mozilla/5.0 fpv-sim-data-validation",
}
STANDARD_AIR_DENSITY_KG_M3 = 1.225

SELECTED_DOWNLOADS = {
    "Betaflight Configuration for F722.txt": RAW / "betaflight_configuration_for_f722.txt",
    "Selected Flight.csv": RAW / "selected_flight.csv",
    "Moment of Inertia Calculations FPV Drone and Test Platforms.pdf": RAW / "moment_of_inertia_calculations.pdf",
}

PID_TUNING_RESULT_FILES = [
    ("pitch", "p_only", "RESULT_P_PITCH.csv"),
    ("pitch", "p_i", "RESULT_P_I_PITCH.csv"),
    ("pitch", "p_i_d", "RESULT_P_I_D_PITCH.csv"),
    ("roll", "p_only", "RESULT_P_ROLL.csv"),
    ("roll", "p_i", "RESULT_P_I_ROLL.csv"),
    ("roll", "p_i_d", "RESULT_P_I_D_ROLL.csv"),
    ("yaw", "p_only", "RESULT_P_YAW.csv"),
    ("yaw", "p_i", "RESULT_P_I_YAW.csv"),
    ("yaw", "p_i_d", "RESULT_P_I_D_YAW.csv"),
]

for _, _, filename in PID_TUNING_RESULT_FILES:
    SELECTED_DOWNLOADS[filename] = RAW / "pid_tuning_results" / filename

COMPONENT_DATASHEET_FILES = [
    "P02_Foxeer Donut 5145.pdf",
    "P03_2507 1800KV Brushless Motor.pdf",
    "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
    "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
    "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
]

for filename in COMPONENT_DATASHEET_FILES:
    SELECTED_DOWNLOADS[filename] = RAW / "component_datasheets" / filename

BATTERY_AUTONOMY_DATASETS = [
    {
        "scenario": "max_power",
        "label": "Max Power Time",
        "folder_path": "Supplementary Data/Battery Autonoy test/Max Power Time",
        "archive_filename": "Max Power Time Flights.rar",
        "script_filename": "battery test.py",
        "plot_filename": "Battery_Discharge_Max.png",
        "archive_path": RAW / "battery_autonomy" / "max_power_time_flights.rar",
        "extract_dir": RAW / "battery_autonomy" / "max_power_time_flights",
        "script_path": RAW / "battery_autonomy" / "max_power_battery_test.py",
        "plot_path": RAW / "battery_autonomy" / "battery_discharge_max.png",
    },
    {
        "scenario": "normal_power",
        "label": "Normal Power Time",
        "folder_path": "Supplementary Data/Battery Autonoy test/Normal Power Time",
        "archive_filename": "Normal Power Time Flights.rar",
        "script_filename": "battery test.py",
        "plot_filename": "Battery_Discharge_Normal.png",
        "archive_path": RAW / "battery_autonomy" / "normal_power_time_flights.rar",
        "extract_dir": RAW / "battery_autonomy" / "normal_power_time_flights",
        "script_path": RAW / "battery_autonomy" / "normal_power_battery_test.py",
        "plot_path": RAW / "battery_autonomy" / "battery_discharge_normal.png",
    },
]

FLIGHT_ARCHIVE_DATASETS = [
    {
        "scenario": "open_field",
        "label": "Flight Data in Open Field",
        "folder_path": "Supplementary Data",
        "archive_filename": "Flight Data in Open Field.rar",
        "archive_path": RAW / "flight_archives" / "open_field_flight_data.rar",
        "extract_dir": RAW / "flight_archives" / "open_field",
    },
    {
        "scenario": "urban_environment",
        "label": "Flight Data in Urban Environment",
        "folder_path": "Supplementary Data",
        "archive_filename": "Flight Data in Urban Environment.rar",
        "archive_path": RAW / "flight_archives" / "urban_environment_flight_data.rar",
        "extract_dir": RAW / "flight_archives" / "urban_environment",
    },
]

COMPONENT_SPEC_ROWS: list[dict[str, str | float | int]] = [
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "diameter_in",
        "value": 5.1,
        "unit": "in",
        "note": "Official Foxeer page lists Size of Prop 5.1 inch.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "pitch_in",
        "value": 4.5,
        "unit": "in",
        "note": "Official Foxeer page lists Pitch 4.5.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "prop_mass_g",
        "value": 4.3,
        "unit": "g",
        "note": "Official Foxeer page lists prop weight 4.3 g.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "vendor_product_page",
        "source_page": "https://www.myfpvstore.com/drone-propellers/foxeer-donut-5145-toroidal-props-pick-your-color/",
        "spec_name": "blade_count",
        "value": 3,
        "unit": "blades",
        "note": "Vendor page lists 3 blades and toroidal form; official Foxeer page does not expose blade count in text.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "hub_diameter_mm",
        "value": 5.0,
        "unit": "mm",
        "note": "Official Foxeer page lists Diameter of Hub 5 mm.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "hub_height_mm",
        "value": 6.5,
        "unit": "mm",
        "note": "Official Foxeer page lists Height of Hub 6.5 mm.",
    },
    {
        "component_category": "propeller",
        "component_name": "Foxeer Donut 5145 Props",
        "dataset_filename": "P02_Foxeer Donut 5145.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-donut-5145-props-g-520",
        "spec_name": "material",
        "value": "Pure PC",
        "unit": "",
        "note": "Official Foxeer page lists Pure PC.",
    },
    {
        "component_category": "motor",
        "component_name": "APdrone 2507 1800KV brushless motor",
        "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "kv_from_component_pdf_filename",
        "value": 1800,
        "unit": "rpm_per_volt",
        "note": "Exact APdrone component PDF is image-only; the filename itself records 2507 1800KV.",
    },
    {
        "component_category": "motor",
        "component_name": "APdrone 2507 1800KV brushless motor",
        "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "stator_diameter_mm_from_2507",
        "value": 25,
        "unit": "mm",
        "note": "Derived from the 2507 motor-size convention in the APdrone PDF filename.",
    },
    {
        "component_category": "motor",
        "component_name": "APdrone 2507 1800KV brushless motor",
        "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "stator_height_mm_from_2507",
        "value": 7,
        "unit": "mm",
        "note": "Derived from the 2507 motor-size convention in the APdrone PDF filename.",
    },
    {
        "component_category": "motor",
        "component_name": "DarwinFPV 2507 1800KV comparable motor",
        "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
        "source_scope": "manufacturer_comparable_product_page",
        "source_page": "https://darwinfpv.com/products/darwin-129-2507-1800kv-3-6s-brushless-motor",
        "spec_name": "max_thrust_g",
        "value": 1800,
        "unit": "g",
        "note": "Comparable 2507 1800KV page claims 1.8 kg max thrust; use only as a same-size motor sanity anchor, not proof of APdrone's exact motor curve.",
    },
    {
        "component_category": "motor",
        "component_name": "DarwinFPV 2507 1800KV comparable motor",
        "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
        "source_scope": "manufacturer_comparable_product_page",
        "source_page": "https://darwinfpv.com/products/darwin-129-2507-1800kv-3-6s-brushless-motor",
        "spec_name": "target_prop_size_range",
        "value": "5-6",
        "unit": "in_prop",
        "note": "Comparable 2507 1800KV page describes the motor as intended for 5-6 inch freestyle drones.",
    },
    {
        "component_category": "battery",
        "component_name": "Zeee 4S 1500mAh 14.8V 100C LiPo Battery",
        "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "capacity_mah_from_component_pdf_filename",
        "value": 1500,
        "unit": "mAh",
        "note": "Exact APdrone component PDF is image-only; filename records 1500 mAh.",
    },
    {
        "component_category": "battery",
        "component_name": "Zeee 4S 1500mAh 14.8V 100C LiPo Battery",
        "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "nominal_voltage_v_from_component_pdf_filename",
        "value": 14.8,
        "unit": "V",
        "note": "Filename records a 4S 14.8 V nominal LiPo.",
    },
    {
        "component_category": "battery",
        "component_name": "Zeee 4S 1500mAh 14.8V 100C LiPo Battery",
        "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
        "source_scope": "apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "discharge_c_from_component_pdf_filename",
        "value": 100,
        "unit": "C",
        "note": "Filename records 100C. Current Zeee 1500 mAh official listing found online is 120C, so keep this APdrone value separate.",
    },
    {
        "component_category": "battery",
        "component_name": "Zeee 4S 1500mAh 14.8V 100C LiPo Battery",
        "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
        "source_scope": "derived_from_apdrone_dataset_pdf_filename",
        "source_page": SOURCE_PAGE,
        "spec_name": "claimed_max_discharge_current_a",
        "value": 150,
        "unit": "A",
        "note": "Derived as 1.5 Ah * 100C. Treat C-rating as a manufacturer claim, not measured continuous current.",
    },
    {
        "component_category": "battery",
        "component_name": "Zeee 4S 1500mAh current official listing",
        "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
        "source_scope": "manufacturer_current_similar_product_page",
        "source_page": "https://zeeebattery.com/products/zeee-4s-lipo-battery-1500mah-14-8v-120c-xt60",
        "spec_name": "approx_weight_g_current_120c_listing",
        "value": 176.7,
        "unit": "g",
        "note": "Current Zeee 1500 mAh official listing is 120C, not the APdrone PDF's 100C label; use weight as similar-pack context only.",
    },
    {
        "component_category": "esc",
        "component_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
        "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420",
        "spec_name": "continuous_current_per_channel_a",
        "value": 65,
        "unit": "A",
        "note": "Official Foxeer page lists 65A*4 continuous current.",
    },
    {
        "component_category": "esc",
        "component_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
        "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420",
        "spec_name": "burst_current_per_channel_a",
        "value": 100,
        "unit": "A",
        "note": "Official Foxeer page lists 100A*4 burst current.",
    },
    {
        "component_category": "esc",
        "component_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
        "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420",
        "spec_name": "input_voltage_range_v",
        "value": "9-40",
        "unit": "V",
        "note": "Official Foxeer page lists DC 9V-40V, 3-8S LiPo.",
    },
    {
        "component_category": "esc",
        "component_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
        "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420",
        "spec_name": "supported_protocols",
        "value": "DShot150/300/600/1200, MultiShot, OneShot",
        "unit": "",
        "note": "Official Foxeer page lists these input signals.",
    },
    {
        "component_category": "esc",
        "component_name": "Foxeer Reaper F4 128K 65A BL32 4in1 ESC",
        "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-reaper-f4-128k-65a-bl32-4in1-9-40v-esc-30-5-30-5mm-m3-g-420",
        "spec_name": "current_scaling",
        "value": 70,
        "unit": "esc_current_scale",
        "note": "Official Foxeer page lists Current Scaling 70.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "cpu",
        "value": "STM32F722RET6",
        "unit": "",
        "note": "Official Foxeer 8S MPU6000 X8 page lists CPU STM32F722RET6.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "gyro",
        "value": "MPU6000",
        "unit": "",
        "note": "Official Foxeer 8S MPU6000 X8 product name/model lists MPU6000.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "barometer",
        "value": "DPS310",
        "unit": "",
        "note": "Official Foxeer page lists barometer DPS310.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "power_supply_cells",
        "value": "4-8S",
        "unit": "LiPo",
        "note": "Official Foxeer page lists 4-8S LiPo power supply.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "blackbox_flash_mb",
        "value": 16,
        "unit": "MB",
        "note": "Official Foxeer page lists 16M flash memory.",
    },
    {
        "component_category": "flight_controller",
        "component_name": "Foxeer F722 V4 FC MPU6000 8S Dual BEC Barometer X8",
        "dataset_filename": "P10_Foxeer F722 V4 Flight Con- troller MPU6000 8S.pdf",
        "source_scope": "foxeer_official_product_page",
        "source_page": "https://www.foxeer.com/foxeer-f722-v4-mpu6000-fc-8s-dual-bec-barometer-x8-g-502",
        "spec_name": "weight_g",
        "value": 7.7,
        "unit": "g",
        "note": "Official Foxeer page lists 7.7 g.",
    },
]

DRONE_CONFIG = ROOT / "drone-sim-core" / "src" / "main" / "java" / "com" / "tenicana" / "dronecraft" / "sim" / "DroneConfig.java"
DRONE_CONFIG_NUMERIC_CONSTANT_FILES = [
    DRONE_CONFIG.parent / "RateEnvelopeCalibration.java",
    DRONE_CONFIG.parent / "SensorNoiseCalibration.java",
    DRONE_CONFIG,
]

APDRONE_INERTIA_SOURCE_NOTE = (
    "Extracted from APdrone's Moment of Inertia Calculations FPV Drone and Test Platforms PDF. "
    "The PDF uses a simplified grouped-body model, with the FPV drone in an X-type layout and yaw on its Z axis."
)

APDRONE_INERTIA_ROWS: list[dict[str, str | float]] = [
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone FPV drone total",
        "mass_kg": 0.6284,
        "inertia_x_kg_m2": 0.001346,
        "inertia_y_kg_m2": 0.001410,
        "inertia_z_kg_m2": 0.002480,
        "yaw_axis": "source_z",
        "motor_center_radius_m": 0.095,
        "note": APDRONE_INERTIA_SOURCE_NOTE,
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone motors plus propellers",
        "mass_kg": 0.2142,
        "inertia_x_kg_m2": 0.000966,
        "inertia_y_kg_m2": 0.000966,
        "inertia_z_kg_m2": 0.001932,
        "yaw_axis": "source_z",
        "motor_center_radius_m": 0.095,
        "note": "Four point masses; per-motor+prop assembly mass is 0.05355 kg.",
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone battery",
        "mass_kg": 0.177,
        "inertia_x_kg_m2": 0.0001468,
        "inertia_y_kg_m2": 0.0002114,
        "inertia_z_kg_m2": 0.0001008,
        "yaw_axis": "source_z",
        "motor_center_radius_m": float("nan"),
        "note": "Rectangular prism battery at z=0.025 m in the PDF model.",
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone frame arms",
        "mass_kg": 0.1000,
        "inertia_x_kg_m2": 0.0001503,
        "inertia_y_kg_m2": 0.0001503,
        "inertia_z_kg_m2": 0.0003008,
        "yaw_axis": "source_z",
        "motor_center_radius_m": 0.095,
        "note": "Four thin rods; per-arm mass 0.025 kg and length 0.095 m.",
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone central core",
        "mass_kg": 0.1372,
        "inertia_x_kg_m2": 0.0000833,
        "inertia_y_kg_m2": 0.0000833,
        "inertia_z_kg_m2": 0.0001463,
        "yaw_axis": "source_z",
        "motor_center_radius_m": float("nan"),
        "note": "Central 0.08 m x 0.08 m x 0.03 m rectangular prism group.",
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone pitch/roll test platform",
        "mass_kg": 0.114,
        "inertia_x_kg_m2": 0.00005074,
        "inertia_y_kg_m2": 0.00009557,
        "inertia_z_kg_m2": 0.00009977,
        "yaw_axis": "source_z",
        "motor_center_radius_m": float("nan"),
        "note": "Hollow rectangular prism platform; PDF states platform inertia is below 10% of the drone along comparable axes.",
    },
    {
        "row_type": "apdrone_inertia_reference",
        "name": "APdrone yaw test platform",
        "mass_kg": 0.202,
        "inertia_x_kg_m2": 0.0002186,
        "inertia_y_kg_m2": 0.0002880,
        "inertia_z_kg_m2": 0.0002287,
        "yaw_axis": "source_z",
        "motor_center_radius_m": float("nan"),
        "note": "Hollow rectangular prism yaw platform; PDF yaw-platform/drone yaw inertia ratio is about 9.22%.",
    },
]


def repo_path(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def fetch_bytes(url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(3):
        request = urllib.request.Request(url, headers=HEADERS)
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                return response.read()
        except Exception as exc:  # pragma: no cover - network failure path
            last_error = exc
            time.sleep(0.75 * (attempt + 1))
    assert last_error is not None
    raise last_error


def fetch_json(url: str) -> object:
    return json.loads(fetch_bytes(url).decode("utf-8"))


def fetch_to_path(url: str, destination: Path, expected_size: int | None = None) -> None:
    if destination.exists() and (expected_size is None or destination.stat().st_size == expected_size):
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    tmp = destination.with_suffix(destination.suffix + ".part")
    last_error: Exception | None = None
    for attempt in range(5):
        request = urllib.request.Request(url, headers=HEADERS)
        try:
            if tmp.exists():
                tmp.unlink()
            with urllib.request.urlopen(request, timeout=240) as response, tmp.open("wb") as handle:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    handle.write(chunk)
            if expected_size is not None and tmp.stat().st_size != expected_size:
                raise RuntimeError(
                    f"Downloaded {tmp.stat().st_size} bytes for {destination.name}, expected {expected_size}"
                )
            tmp.replace(destination)
            return
        except Exception as exc:  # pragma: no cover - network failure path
            last_error = exc
            time.sleep(1.5 * (attempt + 1))
    assert last_error is not None
    raise last_error


def fetch_public_file_inventory() -> list[dict[str, str | float | int]]:
    try:
        return fetch_public_file_inventory_from_api()
    except Exception:
        cached = DATA / "apdrone_mendeley_file_inventory.csv"
        if cached.exists():
            with cached.open(newline="", encoding="utf-8") as handle:
                return [dict(row) for row in csv.DictReader(handle)]
        raise


def fetch_public_file_inventory_from_api() -> list[dict[str, str | float | int]]:
    folders_obj = fetch_json(f"{PUBLIC_API_BASE}/folders/{DATASET_VERSION}")
    if not isinstance(folders_obj, list):
        raise RuntimeError(f"Unexpected APdrone folder payload: {folders_obj!r}")
    folders = [item for item in folders_obj if isinstance(item, dict)]
    folder_by_id = {str(item["id"]): item for item in folders if "id" in item}

    def folder_path(folder_id: str) -> str:
        if folder_id == "root":
            return "root"
        parts: list[str] = []
        current = folder_id
        while current in folder_by_id:
            folder = folder_by_id[current]
            parts.insert(0, str(folder.get("name", current)))
            parent = folder.get("parent_id")
            current = str(parent) if parent else ""
        return "/".join(parts) if parts else "root"

    rows: list[dict[str, str | float | int]] = []
    for folder_id in ["root", *[str(item["id"]) for item in folders if "id" in item]]:
        files_obj = fetch_json(
            f"{PUBLIC_API_BASE}/files?folder_id={folder_id}&version={DATASET_VERSION}"
        )
        if not isinstance(files_obj, list):
            raise RuntimeError(f"Unexpected APdrone files payload for {folder_id}: {files_obj!r}")
        for item in files_obj:
            if not isinstance(item, dict):
                continue
            details = item.get("content_details", {})
            details = details if isinstance(details, dict) else {}
            filename = str(item.get("filename", ""))
            lower = filename.lower()
            if lower.endswith(".csv"):
                usefulness = "machine_readable_csv"
            elif lower.endswith((".txt", ".py")):
                usefulness = "machine_readable_text_or_script"
            elif lower.endswith(".rar"):
                usefulness = "compressed_archive_not_downloaded_by_default"
            elif lower.endswith(".pdf"):
                usefulness = "documentation_or_datasheet"
            elif lower.endswith((".mp4", ".jpg", ".png", ".dwg")):
                usefulness = "multimedia_or_cad_context"
            else:
                usefulness = "context"
            size = int(item.get("size", 0) or 0)
            rows.append(
                {
                    "row_type": "apdrone_mendeley_file_inventory",
                    "dataset_id": DATASET_ID,
                    "dataset_version": DATASET_VERSION,
                    "doi": DOI,
                    "source_page": SOURCE_PAGE,
                    "folder_path": folder_path(folder_id),
                    "filename": filename,
                    "file_id": str(item.get("id", "")),
                    "size_bytes": size,
                    "size_mib": size / (1024.0 * 1024.0),
                    "content_type": str(details.get("content_type", "")),
                    "status": str(item.get("status", "")),
                    "download_url": str(details.get("download_url", "")),
                    "usefulness": usefulness,
                }
            )
    return sorted(rows, key=lambda row: (str(row["folder_path"]), str(row["filename"])))


def download_selected_files(inventory_rows: list[dict[str, str | float | int]]) -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    by_name = {str(row["filename"]): str(row["download_url"]) for row in inventory_rows}
    for filename, destination in SELECTED_DOWNLOADS.items():
        if destination.exists():
            continue
        url = by_name.get(filename)
        if not url:
            raise RuntimeError(f"Missing APdrone public download URL for {filename}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(fetch_bytes(url))


def find_inventory_row(
    inventory_rows: list[dict[str, str | float | int]],
    folder_path: str,
    filename: str,
) -> dict[str, str | float | int]:
    for row in inventory_rows:
        if row.get("folder_path") == folder_path and row.get("filename") == filename:
            return row
    raise RuntimeError(f"Missing APdrone inventory row for {folder_path}/{filename}")


def download_battery_autonomy_files(inventory_rows: list[dict[str, str | float | int]]) -> None:
    for dataset in BATTERY_AUTONOMY_DATASETS:
        folder_path = str(dataset["folder_path"])
        for filename_key, path_key in [
            ("archive_filename", "archive_path"),
            ("script_filename", "script_path"),
            ("plot_filename", "plot_path"),
        ]:
            filename = str(dataset[filename_key])
            row = find_inventory_row(inventory_rows, folder_path, filename)
            fetch_to_path(
                str(row["download_url"]),
                Path(dataset[path_key]),
                int(row.get("size_bytes", 0) or 0),
            )


def download_flight_archive_files(inventory_rows: list[dict[str, str | float | int]]) -> None:
    for dataset in FLIGHT_ARCHIVE_DATASETS:
        row = find_inventory_row(
            inventory_rows,
            str(dataset["folder_path"]),
            str(dataset["archive_filename"]),
        )
        fetch_to_path(
            str(row["download_url"]),
            Path(dataset["archive_path"]),
            int(row.get("size_bytes", 0) or 0),
        )


def extracted_flight_csvs(extract_dir: Path) -> list[Path]:
    if not extract_dir.exists():
        return []
    paths = {
        *extract_dir.glob("flight*.csv"),
        *extract_dir.glob("Flight_*.csv"),
        *extract_dir.rglob("Flight_*.csv"),
    }
    return sorted(paths, key=lambda path: str(path).lower())


def find_archive_tool() -> tuple[str, list[str]] | None:
    local_7z = ROOT / "docs" / "data" / "raw" / "tools" / "7zip" / "app" / "7z.exe"
    if local_7z.exists():
        return str(local_7z), ["x"]
    for name in ["7z", "7za", "bsdtar", "tar"]:
        found = shutil.which(name)
        if found:
            return found, ["-xf"] if name in {"bsdtar", "tar"} else ["x"]
    return None


def extract_battery_autonomy_archives() -> None:
    for dataset in BATTERY_AUTONOMY_DATASETS:
        extract_dir = Path(dataset["extract_dir"])
        if len(extracted_flight_csvs(extract_dir)) >= 5:
            continue
        archive_path = Path(dataset["archive_path"])
        if not archive_path.exists():
            continue
        extract_dir.mkdir(parents=True, exist_ok=True)
        tool = find_archive_tool()
        if tool is None:
            print(f"Skipping extraction for {archive_path.name}: no archive extractor found")
            continue
        executable, base_args = tool
        if Path(executable).name.lower().startswith("7z"):
            command = [executable, *base_args, str(archive_path), f"-o{extract_dir}", "-y"]
        else:
            command = [executable, *base_args, str(archive_path), "-C", str(extract_dir)]
        try:
            subprocess.run(
                command,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except subprocess.CalledProcessError as exc:
            print(f"Skipping extraction for {archive_path.name}: {exc.stdout[-500:]}")


def extract_flight_archives() -> None:
    for dataset in FLIGHT_ARCHIVE_DATASETS:
        extract_dir = Path(dataset["extract_dir"])
        if len(extracted_flight_csvs(extract_dir)) >= 5:
            continue
        archive_path = Path(dataset["archive_path"])
        if not archive_path.exists():
            continue
        extract_dir.mkdir(parents=True, exist_ok=True)
        tool = find_archive_tool()
        if tool is None:
            print(f"Skipping extraction for {archive_path.name}: no archive extractor found")
            continue
        executable, base_args = tool
        if Path(executable).name.lower().startswith("7z"):
            command = [executable, *base_args, str(archive_path), f"-o{extract_dir}", "-y"]
        else:
            command = [executable, *base_args, str(archive_path), "-C", str(extract_dir)]
        try:
            subprocess.run(
                command,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except subprocess.CalledProcessError as exc:
            print(f"Skipping extraction for {archive_path.name}: {exc.stdout[-500:]}")


def percentile(values: list[float], pct: float) -> float:
    clean = sorted(value for value in values if math.isfinite(value))
    if not clean:
        return float("nan")
    index = (len(clean) - 1) * pct / 100.0
    lo = math.floor(index)
    hi = math.ceil(index)
    if lo == hi:
        return clean[lo]
    return clean[lo] * (hi - index) + clean[hi] * (index - lo)


def stats(values: list[float], scale: float = 1.0) -> dict[str, float]:
    scaled = [value / scale for value in values if math.isfinite(value)]
    if not scaled:
        return {}
    return {
        "n": float(len(scaled)),
        "min": min(scaled),
        "p05": percentile(scaled, 5.0),
        "median": percentile(scaled, 50.0),
        "mean": statistics.fmean(scaled),
        "p95": percentile(scaled, 95.0),
        "max": max(scaled),
    }


def parse_betaflight_config(path: Path) -> dict[str, str]:
    settings: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        stripped = line.strip()
        if stripped.startswith("set ") and " = " in stripped:
            key, value = stripped[4:].split(" = ", 1)
            settings[key.strip()] = value.strip()
        elif stripped.startswith("board_name "):
            settings["board_name"] = stripped.split(None, 1)[1]
        elif stripped.startswith("# Betaflight"):
            settings["firmware_line"] = stripped.lstrip("# ").strip()
    return settings


def summarize_selected_flight(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    def add(
        row_type: str,
        name: str,
        metric: str,
        value: str | float | int,
        unit: str = "",
        source: str = SOURCE_PAGE,
        note: str = "",
    ) -> None:
        rows.append(
            {
                "row_type": row_type,
                "name": name,
                "metric": metric,
                "value": value,
                "unit": unit,
                "source": source,
                "note": note,
            }
        )

    add("apdrone_dataset_metadata", "APdrone", "doi", DOI, note="Mendeley Data v2.")
    add("apdrone_dataset_metadata", "APdrone", "published_date", "2025-06-13")
    add("apdrone_dataset_metadata", "APdrone", "file_count_from_public_api", len(inventory_rows), "files")
    add(
        "apdrone_dataset_metadata",
        "APdrone",
        "csv_file_count_from_public_api",
        sum(1 for row in inventory_rows if str(row["filename"]).lower().endswith(".csv")),
        "files",
    )
    add(
        "apdrone_dataset_metadata",
        "APdrone",
        "archive_file_count_from_public_api",
        sum(1 for row in inventory_rows if str(row["filename"]).lower().endswith(".rar")),
        "files",
        note="RAR archives are inventoried but not downloaded by default.",
    )

    config_path = SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"]
    config = parse_betaflight_config(config_path)
    config_keys = [
        "firmware_line",
        "board_name",
        "gyro_lpf1_static_hz",
        "gyro_lpf2_static_hz",
        "dyn_notch_count",
        "dyn_notch_q",
        "dyn_notch_min_hz",
        "dyn_notch_max_hz",
        "rc_smoothing",
        "rc_smoothing_auto_factor",
        "serialrx_provider",
        "blackbox_sample_rate",
        "blackbox_disable_motors",
        "blackbox_disable_rpm",
        "blackbox_disable_bat",
        "blackbox_disable_gps",
        "motor_kv",
        "motor_pwm_protocol",
        "motor_pwm_rate",
        "motor_poles",
        "dshot_bidir",
        "bat_capacity",
        "vbat_scale",
        "ibata_scale",
        "p_pitch",
        "i_pitch",
        "d_pitch",
        "p_roll",
        "i_roll",
        "d_roll",
        "p_yaw",
        "i_yaw",
        "d_yaw",
        "rates_type",
        "roll_rate_limit",
        "pitch_rate_limit",
        "yaw_rate_limit",
        "roll_rc_rate",
        "pitch_rc_rate",
        "yaw_rc_rate",
    ]
    for key in config_keys:
        if key in config:
            add(
                "apdrone_betaflight_config_setting",
                "Betaflight Configuration for F722",
                key,
                config[key],
                source=repo_path(config_path),
                note="Extracted from APdrone Betaflight dump.",
            )

    selected_path = SELECTED_DOWNLOADS["Selected Flight.csv"]
    metadata: dict[str, str] = {}
    header_line = -1
    with selected_path.open(newline="", encoding="utf-8-sig") as handle:
        for line_no, line in enumerate(handle):
            if line.startswith('"loopIteration"'):
                header_line = line_no
                break
            parsed = next(csv.reader([line]))
            if len(parsed) >= 2:
                metadata[parsed[0]] = parsed[1]
    if header_line < 0:
        raise RuntimeError("Could not find APdrone Blackbox data header")

    metadata_keys = [
        "Product",
        "firmwareType",
        "firmware",
        "firmwarePatch",
        "firmwareVersion",
        "Firmware date",
        "Craft name",
        "P interval",
        "looptime",
        "gyro_sync_denom",
        "pid_process_denom",
        "vbatscale",
        "vbatref",
        "currentMeterScale",
        "currentMeterOffset",
    ]
    for key in metadata_keys:
        if key in metadata:
            add(
                "apdrone_selected_flight_blackbox_metadata",
                "Selected Flight.csv",
                key,
                metadata[key],
                source=repo_path(selected_path),
                note="Header rows before the Blackbox CSV table.",
            )

    tracked = [
        "time",
        "vbatLatest",
        "amperageLatest",
        "baroAlt",
        "GPS_numSat",
        "GPS_altitude",
        "GPS_speed",
        "gyroADC[0]",
        "gyroADC[1]",
        "gyroADC[2]",
        "accSmooth[0]",
        "accSmooth[1]",
        "accSmooth[2]",
        "rcCommands[0]",
        "rcCommands[1]",
        "rcCommands[2]",
        "rcCommands[3]",
        "setpoint[0]",
        "setpoint[1]",
        "setpoint[2]",
        "setpoint[3]",
    ]
    values = {key: [] for key in tracked}
    row_count = 0
    valid_gps_rows = 0
    armed_rows = 0
    with selected_path.open(newline="", encoding="utf-8-sig") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        columns = reader.fieldnames or []
        for row in reader:
            row_count += 1
            for key in tracked:
                raw = row.get(key, "")
                if raw and raw != "NaN":
                    try:
                        value = float(raw)
                    except ValueError:
                        continue
                    if math.isfinite(value):
                        values[key].append(value)
            if row.get("GPS_speed") not in ("", "NaN"):
                valid_gps_rows += 1
            try:
                if int(float(row.get("flightModeFlags", "0"))) & 1:
                    armed_rows += 1
            except ValueError:
                pass

    times = values["time"]
    duration_s = (max(times) - min(times)) / 1e6 if len(times) > 1 else float("nan")
    rate_hz = (len(times) - 1) / duration_s if duration_s > 0 else float("nan")
    selected_source = repo_path(selected_path)
    add("apdrone_selected_flight_metric", "Selected Flight.csv", "sample_rows", row_count, "rows", selected_source)
    add(
        "apdrone_selected_flight_metric",
        "Selected Flight.csv",
        "duration_s",
        duration_s,
        "s",
        selected_source,
        "Computed from time column in microseconds.",
    )
    add(
        "apdrone_selected_flight_metric",
        "Selected Flight.csv",
        "estimated_log_rate_hz",
        rate_hz,
        "Hz",
        selected_source,
        "Rows over duration; config reports blackbox_sample_rate=1/2.",
    )
    add(
        "apdrone_selected_flight_metric",
        "Selected Flight.csv",
        "column_count",
        len(columns),
        "columns",
        selected_source,
        ", ".join(columns),
    )
    add("apdrone_selected_flight_metric", "Selected Flight.csv", "valid_gps_rows", valid_gps_rows, "rows", selected_source)
    add("apdrone_selected_flight_metric", "Selected Flight.csv", "armed_rows_bit0", armed_rows, "rows", selected_source)

    stat_specs = [
        ("vbatLatest", 100.0, "V", "Blackbox export appears to store centivolts."),
        ("GPS_speed", 100.0, "m/s", "Betaflight GPS_speed interpreted as cm/s."),
        ("GPS_altitude", 100.0, "m", "Betaflight GPS altitude interpreted as cm."),
        ("baroAlt", 100.0, "m", "Blackbox baro altitude interpreted as cm relative altitude."),
        ("GPS_numSat", 1.0, "satellites", "GPS satellite count."),
        ("rcCommands[0]", 1.0, "blackbox_units", "Roll command in exported Blackbox units."),
        ("rcCommands[1]", 1.0, "blackbox_units", "Pitch command in exported Blackbox units."),
        ("rcCommands[2]", 1.0, "blackbox_units", "Yaw command in exported Blackbox units."),
        ("rcCommands[3]", 1.0, "blackbox_units", "Throttle command in exported Blackbox units."),
        ("setpoint[0]", 1.0, "deg/s_or_blackbox_units", "Roll setpoint; cross-check units before direct fit."),
        ("setpoint[1]", 1.0, "deg/s_or_blackbox_units", "Pitch setpoint; cross-check units before direct fit."),
        ("setpoint[2]", 1.0, "deg/s_or_blackbox_units", "Yaw setpoint; cross-check units before direct fit."),
        ("gyroADC[0]", 1.0, "rad/s", "Gyro export appears already scaled to rad/s-like units."),
        ("gyroADC[1]", 1.0, "rad/s", "Gyro export appears already scaled to rad/s-like units."),
        ("gyroADC[2]", 1.0, "rad/s", "Gyro export appears already scaled to rad/s-like units."),
    ]
    for column, scale, unit, note in stat_specs:
        for metric, value in stats(values[column], scale).items():
            add(
                "apdrone_selected_flight_metric",
                column,
                metric,
                value,
                "rows" if metric == "n" else unit,
                selected_source,
                note,
            )

    current_scalings = [
        ("raw", 1.0, "raw", "Do not use as absolute current without confirming Betaflight current-unit scaling."),
        ("candidate_centiamps", 100.0, "A", "Centiamps interpretation yields implausibly low FPV current here."),
        ("candidate_deciamps", 10.0, "A", "Deciamps interpretation gives plausible 5-inch light-flight current, but still needs sensor calibration."),
    ]
    for suffix, scale, unit, note in current_scalings:
        for metric, value in stats(values["amperageLatest"], scale).items():
            add(
                "apdrone_selected_flight_current_metric",
                f"amperageLatest_{suffix}",
                metric,
                value,
                "rows" if metric == "n" else unit,
                selected_source,
                note,
            )

    for suffix, current_scale in [("candidate_centiamps", 100.0), ("candidate_deciamps", 10.0)]:
        energy_j = 0.0
        mah = 0.0
        last_t: float | None = None
        with selected_path.open(newline="", encoding="utf-8-sig") as handle:
            for _ in range(header_line):
                next(handle)
            reader = csv.DictReader(handle)
            for row in reader:
                try:
                    t_s = float(row["time"]) / 1e6
                    voltage_v = float(row["vbatLatest"]) / 100.0
                    current_a = float(row["amperageLatest"]) / current_scale
                except (KeyError, ValueError):
                    continue
                if last_t is not None and math.isfinite(t_s) and 0.0 < t_s - last_t < 1.0:
                    dt = t_s - last_t
                    energy_j += voltage_v * current_a * dt
                    mah += current_a * dt / 3.6
                last_t = t_s
        add(
            "apdrone_selected_flight_current_metric",
            f"amperageLatest_{suffix}",
            "integrated_energy_wh",
            energy_j / 3600.0,
            "Wh",
            selected_source,
            "Integrated vbatLatest times candidate current scaling.",
        )
        add(
            "apdrone_selected_flight_current_metric",
            f"amperageLatest_{suffix}",
            "integrated_capacity_mah",
            mah,
            "mAh",
            selected_source,
            "Integrated candidate current scaling over flight duration.",
        )

    return rows


def finite_or_blank(value: object) -> object:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def split_top_level_commas(text: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depth = 0
    for index, char in enumerate(text):
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            parts.append(text[start:index].strip())
            start = index + 1
    tail = text[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def extract_drone_preset_body(preset_name: str) -> str:
    text = DRONE_CONFIG.read_text(encoding="utf-8")
    body_match = re.search(
        rf"public\s+static\s+DroneConfig\s+{re.escape(preset_name)}\(\)\s*\{{"
        rf"(?P<body>.*?)(?=\n\tpublic\s+static\s+DroneConfig\s+\w+\(\)|\n\}})",
        text,
        re.DOTALL,
    )
    if not body_match:
        raise RuntimeError(f"Could not locate {preset_name}() in {DRONE_CONFIG}")
    return body_match.group("body")


def evaluate_java_double_expression(expression: str, variables: dict[str, float]) -> float:
    clean = expression.strip()
    clean = re.sub(r"//.*", "", clean)
    clean = clean.replace("Math.sqrt", "sqrt")
    clean = clean.replace("Math.toRadians", "radians")
    clean = clean.replace("Math.PI", "pi")
    clean = re.sub(r"\b(?:RateEnvelopeCalibration|SensorNoiseCalibration|DroneConfig)\.", "", clean)
    allowed = {"sqrt": math.sqrt, "radians": math.radians, "pi": math.pi, **variables}
    try:
        return float(eval(clean, {"__builtins__": {}}, allowed))
    except Exception as exc:
        raise RuntimeError(f"Could not evaluate Java double expression {expression!r}") from exc


def parse_java_double_variables(body: str, initial_variables: dict[str, float] | None = None) -> dict[str, float]:
    variables: dict[str, float] = dict(initial_variables or {})
    for match in re.finditer(r"\bdouble\s+(?P<name>\w+)\s*=\s*(?P<expr>[^;]+);", body):
        expression = match.group("expr").strip()
        try:
            variables[match.group("name")] = evaluate_java_double_expression(expression, variables)
        except RuntimeError:
            continue
    return variables


def parse_java_numeric_constants() -> dict[str, float]:
    constants: dict[str, float] = {}
    for path in DRONE_CONFIG_NUMERIC_CONSTANT_FILES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"public\s+static\s+final\s+(?:double|int)\s+(?P<name>\w+)\s*=\s*(?P<expr>[^;]+);",
            text,
        ):
            expression = match.group("expr").strip()
            try:
                constants[match.group("name")] = evaluate_java_double_expression(expression, constants)
            except RuntimeError:
                continue
    return constants


def extract_new_drone_config_arguments(body: str) -> list[str]:
    marker = "return new DroneConfig("
    start = body.find(marker)
    if start < 0:
        raise RuntimeError("Could not locate return new DroneConfig(...)")
    index = start + len(marker)
    depth = 1
    while index < len(body):
        char = body[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return split_top_level_commas(body[start + len(marker):index])
        index += 1
    raise RuntimeError("Could not find end of new DroneConfig(...) arguments")


def parse_java_vec3(expression: str, variables: dict[str, float]) -> tuple[float, float, float]:
    match = re.search(r"new\s+Vec3\((?P<values>.*)\)\s*$", expression.strip(), re.DOTALL)
    if not match:
        raise RuntimeError(f"Could not parse Vec3 expression {expression!r}")
    values = split_top_level_commas(match.group("values"))
    if len(values) != 3:
        raise RuntimeError(f"Unexpected Vec3 arity in {expression!r}")
    return tuple(evaluate_java_double_expression(value, variables) for value in values)  # type: ignore[return-value]


def parse_project_drone_preset_model(preset_name: str) -> dict[str, str | float | int]:
    body = extract_drone_preset_body(preset_name)
    variables = parse_java_numeric_constants()
    variables.update(parse_java_double_variables(body, variables))
    args = extract_new_drone_config_arguments(body)
    if len(args) < 38:
        raise RuntimeError(f"Unexpected DroneConfig argument count for {preset_name}: {len(args)}")

    mass_kg = evaluate_java_double_expression(args[0], variables)
    inertia_x, inertia_y, inertia_z = parse_java_vec3(args[1], variables)
    gravity = evaluate_java_double_expression(args[6], variables)
    linear_drag = evaluate_java_double_expression(args[7], variables)
    body_drag_x, body_drag_y, body_drag_z = parse_java_vec3(args[8], variables)
    nominal_voltage = evaluate_java_double_expression(args[34], variables)
    empty_voltage = evaluate_java_double_expression(args[35], variables)
    battery_resistance = evaluate_java_double_expression(args[36], variables)
    battery_capacity = evaluate_java_double_expression(args[37], variables)
    max_battery_current = evaluate_java_double_expression(args[38], variables)
    max_pitch_rate_deg_s = math.degrees(evaluate_java_double_expression(args[39], variables))
    max_yaw_rate_deg_s = math.degrees(evaluate_java_double_expression(args[40], variables))
    max_roll_rate_deg_s = math.degrees(evaluate_java_double_expression(args[41], variables))
    gyro_lpf_hz = evaluate_java_double_expression(args[29], variables)
    rc_frame_rate_hz = evaluate_java_double_expression(args[57], variables)
    esc_command_frame_rate_hz = evaluate_java_double_expression(args[59], variables)
    esc_protocol = args[61].strip().replace("EscCommandProtocol.", "")
    rotor_count = len(re.findall(r"new\s+RotorSpec\s*\(", args[5]))
    if rotor_count <= 0:
        raise RuntimeError(f"Could not count RotorSpec rows for {preset_name}")
    max_rotor_thrust = variables.get("maxRotorThrust")
    thrust_coefficient = variables.get("thrustCoefficient")
    yaw_torque_per_thrust = variables.get("yawTorquePerThrust")
    rotor_radius = variables.get("rotorRadius")
    motor_radius = variables.get("motorCenterRadius")
    if motor_radius is None:
        arm = variables.get("arm")
        if arm is not None:
            motor_radius = abs(arm) * math.sqrt(2.0)

    if max_rotor_thrust is None:
        raise RuntimeError(f"Could not parse maxRotorThrust for {preset_name}")
    blade_count_match = re.search(r"\.withRotorBladeCount\((?P<value>[^)]*)\)", body)
    rotor_blade_count = (
        evaluate_java_double_expression(blade_count_match.group("value"), variables)
        if blade_count_match
        else 2.0
    )
    pitch_ratio_match = re.search(r"\.withRotorBladePitchToDiameterRatio\((?P<value>[^)]*)\)", body)
    blade_pitch_to_diameter_ratio = (
        evaluate_java_double_expression(pitch_ratio_match.group("value"), variables)
        if pitch_ratio_match
        else ""
    )

    weight_n = mass_kg * gravity
    max_total_thrust_n = rotor_count * max_rotor_thrust
    level_horizontal_thrust_margin_n = math.sqrt(max(max_total_thrust_n**2 - weight_n**2, 0.0))
    total_drag_x = equivalent_quadratic_c(linear_drag, body_drag_x, PROJECT_DRAG_REFERENCE_SPEED_M_S)
    total_drag_y = equivalent_quadratic_c(linear_drag, body_drag_y, PROJECT_DRAG_REFERENCE_SPEED_M_S)
    total_drag_z = equivalent_quadratic_c(linear_drag, body_drag_z, PROJECT_DRAG_REFERENCE_SPEED_M_S)

    def drag_limited_speed(body_drag: float) -> float:
        return terminal_speed_m_s(level_horizontal_thrust_margin_n, linear_drag, body_drag)

    row = {
        "row_type": "project_preset_model",
        "preset": preset_name,
        "source_page": repo_path(DRONE_CONFIG),
        "local_source_file": repo_path(DRONE_CONFIG),
        "mass_kg": mass_kg,
        "inertia_x_kg_m2": inertia_x,
        "inertia_y_kg_m2": inertia_y,
        "inertia_z_kg_m2": inertia_z,
        "yaw_axis": "project_y",
        "motor_center_radius_m": motor_radius if motor_radius is not None else "",
        "rotor_count": rotor_count,
        "max_rotor_thrust_n": max_rotor_thrust,
        "max_total_thrust_n": max_total_thrust_n,
        "hover_thrust_per_motor_n": weight_n / rotor_count,
        "thrust_to_weight_ratio": max_total_thrust_n / weight_n if weight_n > 0.0 else "",
        "level_horizontal_thrust_margin_n": level_horizontal_thrust_margin_n,
        "thrust_coefficient_n_per_rad2_s2": thrust_coefficient if thrust_coefficient is not None else "",
        "yaw_torque_per_thrust_m": yaw_torque_per_thrust if yaw_torque_per_thrust is not None else "",
        "rotor_radius_m": rotor_radius if rotor_radius is not None else "",
        "linear_drag_coefficient_n_per_mps": linear_drag,
        "linear_drag_coefficient_n_per_mps2": linear_drag,
        "total_drag_reference_speed_m_s": PROJECT_DRAG_REFERENCE_SPEED_M_S,
        "body_drag_x_n_per_mps2": body_drag_x,
        "body_drag_y_n_per_mps2": body_drag_y,
        "body_drag_z_n_per_mps2": body_drag_z,
        "total_drag_x_n_per_mps2": total_drag_x,
        "total_drag_y_n_per_mps2": total_drag_y,
        "total_drag_z_n_per_mps2": total_drag_z,
        "drag_limited_level_speed_x_m_s": drag_limited_speed(body_drag_x),
        "drag_limited_level_speed_z_m_s": drag_limited_speed(body_drag_z),
        "nominal_battery_voltage_v": nominal_voltage,
        "empty_battery_voltage_v": empty_voltage,
        "battery_internal_resistance_ohm": battery_resistance,
        "battery_capacity_ah": battery_capacity,
        "max_battery_current_a": max_battery_current,
        "max_pitch_rate_deg_s": max_pitch_rate_deg_s,
        "max_yaw_rate_deg_s": max_yaw_rate_deg_s,
        "max_roll_rate_deg_s": max_roll_rate_deg_s,
        "gyro_low_pass_cutoff_hz": gyro_lpf_hz,
        "rc_frame_rate_hz": rc_frame_rate_hz,
        "esc_command_frame_rate_hz": esc_command_frame_rate_hz,
        "esc_command_protocol": esc_protocol,
        "rotor_blade_count": rotor_blade_count,
        "rotor_blade_pitch_to_diameter_ratio": blade_pitch_to_diameter_ratio,
        "note": f"Parsed from DroneConfig.java. Runtime drag is F=linearDragCoefficient*V+bodyAxisDrag*V^2; total_drag_* columns are equivalent quadratic coefficients at {PROJECT_DRAG_REFERENCE_SPEED_M_S:g} m/s for CSV compatibility.",
    }
    return {key: finite_or_blank(value) for key, value in row.items()}


def add_inertia_derived_fields(row: dict[str, str | float | int]) -> dict[str, str | float | int]:
    enriched = dict(row)
    try:
        mass = float(enriched["mass_kg"])
        ix = float(enriched["inertia_x_kg_m2"])
        iy = float(enriched["inertia_y_kg_m2"])
        iz = float(enriched["inertia_z_kg_m2"])
    except (KeyError, TypeError, ValueError):
        return enriched

    if mass > 0.0:
        enriched["radius_of_gyration_x_m"] = math.sqrt(ix / mass) if ix > 0.0 else ""
        enriched["radius_of_gyration_y_m"] = math.sqrt(iy / mass) if iy > 0.0 else ""
        enriched["radius_of_gyration_z_m"] = math.sqrt(iz / mass) if iz > 0.0 else ""

    yaw_axis = str(enriched.get("yaw_axis", ""))
    if yaw_axis == "project_y":
        roll_pitch_mean = (ix + iz) / 2.0
        yaw_inertia = iy
    else:
        roll_pitch_mean = (ix + iy) / 2.0
        yaw_inertia = iz
    if roll_pitch_mean > 0.0:
        enriched["yaw_to_roll_pitch_mean_inertia_ratio"] = yaw_inertia / roll_pitch_mean

    return {key: finite_or_blank(value) for key, value in enriched.items()}


def parse_current_racing_quad_inertia() -> dict[str, str | float | int]:
    preset = parse_project_drone_preset_model("racingQuad")

    return add_inertia_derived_fields(
        {
            "row_type": "current_racing_quad_inertia",
            "name": "Current project racingQuad preset",
            "source_page": repo_path(DRONE_CONFIG),
            "local_source_file": repo_path(DRONE_CONFIG),
            "mass_kg": float(preset["mass_kg"]),
            "inertia_x_kg_m2": float(preset["inertia_x_kg_m2"]),
            "inertia_y_kg_m2": float(preset["inertia_y_kg_m2"]),
            "inertia_z_kg_m2": float(preset["inertia_z_kg_m2"]),
            "yaw_axis": "project_y",
            "motor_center_radius_m": float(preset["motor_center_radius_m"]),
            "note": "Parsed from racingQuad(); project coordinates use Y as the vertical/yaw axis and rotor positions in X/Z.",
        }
    )


def summarize_apdrone_inertia(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    pdf_name = "Moment of Inertia Calculations FPV Drone and Test Platforms.pdf"
    pdf_path = SELECTED_DOWNLOADS[pdf_name]
    pdf_inventory = next((row for row in inventory_rows if row.get("filename") == pdf_name), {})
    pdf_source = str(pdf_inventory.get("download_url", SOURCE_PAGE))

    rows: list[dict[str, str | float | int]] = [
        {
            "row_type": "apdrone_inertia_source_metadata",
            "name": pdf_name,
            "dataset_id": DATASET_ID,
            "dataset_version": DATASET_VERSION,
            "doi": DOI,
            "source_page": SOURCE_PAGE,
            "download_url": pdf_source,
            "local_source_file": repo_path(pdf_path),
            "note": "Small APdrone PDF downloaded from Mendeley public file API and transcribed into this generated CSV.",
        }
    ]

    apdrone_rows = []
    for source_row in APDRONE_INERTIA_ROWS:
        row = {
            **source_row,
            "dataset_id": DATASET_ID,
            "dataset_version": DATASET_VERSION,
            "doi": DOI,
            "source_page": SOURCE_PAGE,
            "download_url": pdf_source,
            "local_source_file": repo_path(pdf_path),
        }
        apdrone_rows.append(add_inertia_derived_fields(row))
    rows.extend(apdrone_rows)

    current_row = parse_current_racing_quad_inertia()
    rows.append(current_row)

    apdrone_total = next(row for row in apdrone_rows if row["name"] == "APdrone FPV drone total")

    def comparison(
        name: str,
        current_axis: str,
        reference_axis: str,
        current_inertia: float,
        reference_inertia: float,
        current_rg: float,
        reference_rg: float,
        note: str,
    ) -> None:
        current_mass = float(current_row["mass_kg"])
        reference_mass = float(apdrone_total["mass_kg"])
        scaled_reference = reference_inertia * current_mass / reference_mass
        rows.append(
            {
                "row_type": "current_vs_apdrone_inertia_axis",
                "name": name,
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(pdf_path),
                "current_preset": "racingQuad",
                "reference_name": "APdrone FPV drone total",
                "current_axis": current_axis,
                "reference_axis": reference_axis,
                "current_mass_kg": current_mass,
                "reference_mass_kg": reference_mass,
                "current_inertia_kg_m2": current_inertia,
                "reference_inertia_kg_m2": reference_inertia,
                "reference_scaled_same_mass_inertia_kg_m2": scaled_reference,
                "current_over_reference_raw": current_inertia / reference_inertia,
                "current_over_reference_scaled_same_rg": current_inertia / scaled_reference,
                "current_radius_of_gyration_m": current_rg,
                "reference_radius_of_gyration_m": reference_rg,
                "current_rg_over_reference_rg": current_rg / reference_rg,
                "note": note,
            }
        )

    comparison(
        "project_x_vs_apdrone_source_x",
        "project_x",
        "source_x",
        float(current_row["inertia_x_kg_m2"]),
        float(apdrone_total["inertia_x_kg_m2"]),
        float(current_row["radius_of_gyration_x_m"]),
        float(apdrone_total["radius_of_gyration_x_m"]),
        "Horizontal-axis comparison; APdrone uses Z as yaw, while this project uses Y as yaw.",
    )
    comparison(
        "project_z_vs_apdrone_source_y",
        "project_z",
        "source_y",
        float(current_row["inertia_z_kg_m2"]),
        float(apdrone_total["inertia_y_kg_m2"]),
        float(current_row["radius_of_gyration_z_m"]),
        float(apdrone_total["radius_of_gyration_y_m"]),
        "Other horizontal-axis comparison after mapping APdrone source X/Y into project X/Z.",
    )
    comparison(
        "project_y_yaw_vs_apdrone_source_z_yaw",
        "project_y_yaw",
        "source_z_yaw",
        float(current_row["inertia_y_kg_m2"]),
        float(apdrone_total["inertia_z_kg_m2"]),
        float(current_row["radius_of_gyration_y_m"]),
        float(apdrone_total["radius_of_gyration_z_m"]),
        "Yaw-axis comparison after mapping APdrone source Z yaw into project Y yaw.",
    )

    current_radius = float(current_row["motor_center_radius_m"])
    apdrone_radius = float(apdrone_total["motor_center_radius_m"])
    current_yaw_ratio = float(current_row["yaw_to_roll_pitch_mean_inertia_ratio"])
    apdrone_yaw_ratio = float(apdrone_total["yaw_to_roll_pitch_mean_inertia_ratio"])
    rows.extend(
        [
            {
                "row_type": "current_vs_apdrone_inertia_geometry",
                "name": "motor_center_radius_current_over_apdrone",
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(pdf_path),
                "current_preset": "racingQuad",
                "reference_name": "APdrone FPV drone total",
                "current_motor_center_radius_m": current_radius,
                "reference_motor_center_radius_m": apdrone_radius,
                "current_over_reference_motor_center_radius": current_radius / apdrone_radius,
                "note": "Radius is motor-center distance from body center, not prop radius.",
            },
            {
                "row_type": "current_vs_apdrone_inertia_geometry",
                "name": "yaw_to_roll_pitch_ratio_current_vs_apdrone",
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(pdf_path),
                "current_preset": "racingQuad",
                "reference_name": "APdrone FPV drone total",
                "current_yaw_to_roll_pitch_mean_inertia_ratio": current_yaw_ratio,
                "reference_yaw_to_roll_pitch_mean_inertia_ratio": apdrone_yaw_ratio,
                "current_over_reference_yaw_ratio": current_yaw_ratio / apdrone_yaw_ratio,
                "note": "Shape ratio check independent of absolute mass scaling.",
            },
        ]
    )

    return rows


def parse_optional_float(raw: str | None) -> float | str:
    if raw is None or raw == "":
        return ""
    return float(raw)


def summarize_pid_tuning(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    by_name = {str(row["filename"]): row for row in inventory_rows}
    rows: list[dict[str, str | float | int]] = []
    stage_best: dict[tuple[str, str], dict[str, str | float | int]] = {}

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    for expected_axis, tuning_stage, filename in PID_TUNING_RESULT_FILES:
        path = SELECTED_DOWNLOADS[filename]
        inventory = by_name.get(filename, {})
        source_url = str(inventory.get("download_url", SOURCE_PAGE))
        groups: dict[tuple[str, float | str, float | str, float | str], list[tuple[str, float]]] = {}

        with path.open(newline="", encoding="utf-8-sig") as handle:
            reader = csv.DictReader(handle)
            for raw_row in reader:
                axis = raw_row.get("Eje", expected_axis).strip().lower()
                kp = parse_optional_float(raw_row.get("Kp"))
                ki = parse_optional_float(raw_row.get("Ki"))
                kd = parse_optional_float(raw_row.get("Kd"))
                trial = raw_row.get("Prueba", "")
                try:
                    mae = float(raw_row["MAE"])
                except (KeyError, ValueError):
                    continue
                groups.setdefault((axis, kp, ki, kd), []).append((trial, mae))

        candidate_rows: list[dict[str, str | float | int]] = []
        for (axis, kp, ki, kd), trials in sorted(
            groups.items(),
            key=lambda item: (
                item[0][0],
                float(item[0][1]) if item[0][1] != "" else -1.0,
                float(item[0][2]) if item[0][2] != "" else -1.0,
                float(item[0][3]) if item[0][3] != "" else -1.0,
            ),
        ):
            maes = [mae for _, mae in trials]
            best_trial, best_trial_mae = min(trials, key=lambda item: item[1])
            candidate = {
                "row_type": "apdrone_pid_tuning_candidate",
                "axis": axis,
                "tuning_stage": tuning_stage,
                "filename": filename,
                "source_page": SOURCE_PAGE,
                "download_url": source_url,
                "local_source_file": repo_path(path),
                "kp": kp,
                "ki": ki,
                "kd": kd,
                "trial_count": len(maes),
                "mae_mean": statistics.fmean(maes),
                "mae_median": percentile(maes, 50.0),
                "mae_min": min(maes),
                "mae_max": max(maes),
                "mae_stddev": statistics.pstdev(maes) if len(maes) > 1 else 0.0,
                "best_trial": best_trial,
                "best_trial_mae": best_trial_mae,
                "note": "MAE units are the APdrone tuning script's gyro-vs-setpoint absolute-error units; use as relative tuning evidence.",
            }
            candidate_rows.append(candidate)
            add(candidate)

        if not candidate_rows:
            continue
        best = min(candidate_rows, key=lambda row: (float(row["mae_mean"]), float(row["mae_max"])))
        stage_best[(expected_axis, tuning_stage)] = best
        add(
            {
                **best,
                "row_type": "apdrone_pid_tuning_stage_best",
                "note": "Best candidate within this APdrone axis/stage by mean MAE across available trials.",
            }
        )

    stage_order = ["p_only", "p_i", "p_i_d"]
    for axis in ["pitch", "roll", "yaw"]:
        previous_best: dict[str, str | float | int] | None = None
        for stage in stage_order:
            best = stage_best.get((axis, stage))
            if not best:
                continue
            if previous_best:
                previous_mean = float(previous_best["mae_mean"])
                current_mean = float(best["mae_mean"])
                add(
                    {
                        "row_type": "apdrone_pid_tuning_stage_improvement",
                        "axis": axis,
                        "tuning_stage": stage,
                        "previous_tuning_stage": str(previous_best["tuning_stage"]),
                        "kp": best["kp"],
                        "ki": best["ki"],
                        "kd": best["kd"],
                        "mae_mean": current_mean,
                        "previous_mae_mean": previous_mean,
                        "mae_mean_ratio_vs_previous_stage": current_mean / previous_mean,
                        "mae_mean_reduction_fraction_vs_previous_stage": (previous_mean - current_mean) / previous_mean,
                        "source_page": SOURCE_PAGE,
                        "note": "Relative improvement from adding the next PID term in APdrone's staged tuning sweep.",
                    }
                )
            previous_best = best

    config = parse_betaflight_config(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"])
    for axis in ["pitch", "roll", "yaw"]:
        best = stage_best.get((axis, "p_i_d"))
        if not best:
            continue
        config_kp = parse_optional_float(config.get(f"p_{axis}"))
        config_ki = parse_optional_float(config.get(f"i_{axis}"))
        config_kd = parse_optional_float(config.get(f"d_{axis}"))
        config_d_min = parse_optional_float(config.get(f"d_min_{axis}"))
        add(
            {
                "row_type": "apdrone_betaflight_pid_config_vs_tuning_best",
                "axis": axis,
                "tuning_stage": "p_i_d",
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"]),
                "kp": best["kp"],
                "ki": best["ki"],
                "kd": best["kd"],
                "mae_mean": best["mae_mean"],
                "mae_median": best["mae_median"],
                "mae_max": best["mae_max"],
                "config_kp": config_kp,
                "config_ki": config_ki,
                "config_kd": config_kd,
                "config_d_min": config_d_min,
                "config_matches_best_kp": str(config_kp == best["kp"]).lower(),
                "config_matches_best_ki": str(config_ki == best["ki"]).lower(),
                "config_matches_best_kd": str(config_kd == best["kd"]).lower(),
                "config_d_min_matches_best_kd": str(config_d_min == best["kd"]).lower(),
                "note": "Compares APdrone Betaflight dump PID values against the best mean-MAE row from the APdrone staged PID sweep; D-sweep best maps to Betaflight d_min on this dump.",
            }
        )

    return rows


def summarize_component_specs(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    by_name = {str(row["filename"]): row for row in inventory_rows}
    rows: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    for filename in COMPONENT_DATASHEET_FILES:
        inventory = by_name.get(filename, {})
        local_path = SELECTED_DOWNLOADS[filename]
        add(
            {
                "row_type": "apdrone_component_datasheet_metadata",
                "component_category": "datasheet",
                "component_name": filename.removeprefix("P").split("_", 1)[-1].removesuffix(".pdf"),
                "dataset_filename": filename,
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "doi": DOI,
                "source_scope": "apdrone_mendeley_component_pdf",
                "source_page": SOURCE_PAGE,
                "download_url": str(inventory.get("download_url", "")),
                "local_source_file": repo_path(local_path),
                "size_bytes": int(inventory.get("size_bytes", 0) or 0),
                "content_type": str(inventory.get("content_type", "")),
                "note": "Cached APdrone component datasheet PDF. These PDFs are image-only under pypdf text extraction, so structured spec rows use filename evidence plus official/vendor web pages.",
            }
        )

    for spec in COMPONENT_SPEC_ROWS:
        filename = str(spec["dataset_filename"])
        row = {
            "row_type": "apdrone_component_spec",
            "dataset_id": DATASET_ID,
            "dataset_version": DATASET_VERSION,
            "doi": DOI,
            "local_source_file": repo_path(SELECTED_DOWNLOADS[filename]),
            **spec,
        }
        add(row)

    config = parse_betaflight_config(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"])
    component_motor_kv = 1800.0
    config_motor_kv = float(config.get("motor_kv", "nan"))
    if math.isfinite(config_motor_kv):
        add(
            {
                "row_type": "apdrone_component_config_crosscheck",
                "component_category": "motor",
                "component_name": "APdrone 2507 motor KV",
                "dataset_filename": "P03_2507 1800KV Brushless Motor.pdf",
                "source_scope": "apdrone_betaflight_config_vs_component_pdf_filename",
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"]),
                "spec_name": "betaflight_motor_kv_over_component_filename_kv",
                "component_value": component_motor_kv,
                "config_value": config_motor_kv,
                "value": config_motor_kv / component_motor_kv,
                "unit": "ratio",
                "note": "APdrone component PDF filename says 1800KV, while the Betaflight dump uses motor_kv=1960. Treat KV as unresolved until the exact motor datasheet is OCR-checked or weighed against RPM telemetry.",
            }
        )

    component_capacity_mah = 1500.0
    config_capacity_mah = float(config.get("bat_capacity", "nan"))
    if math.isfinite(config_capacity_mah):
        add(
            {
                "row_type": "apdrone_component_config_crosscheck",
                "component_category": "battery",
                "component_name": "Zeee 4S 1500mAh APdrone battery",
                "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
                "source_scope": "apdrone_betaflight_config_vs_component_pdf_filename",
                "source_page": SOURCE_PAGE,
                "local_source_file": repo_path(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"]),
                "spec_name": "betaflight_bat_capacity_matches_component_filename",
                "component_value": component_capacity_mah,
                "config_value": config_capacity_mah,
                "value": str(component_capacity_mah == config_capacity_mah).lower(),
                "unit": "boolean",
                "note": "APdrone component filename and Betaflight dump both indicate a 1500 mAh pack.",
            }
        )

    claimed_pack_current_a = 150.0
    esc_continuous_total_a = 65.0 * 4.0
    add(
        {
            "row_type": "apdrone_component_derived_limit",
            "component_category": "battery_vs_esc",
            "component_name": "APdrone claimed pack C-rating versus 4-in-1 ESC rating",
            "dataset_filename": "P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf",
            "source_scope": "derived_from_component_specs",
            "source_page": SOURCE_PAGE,
            "local_source_file": repo_path(SELECTED_DOWNLOADS["P08_Zeee 4S 1500mAh 14.8V 100C Lipo Battery.pdf"]),
            "spec_name": "claimed_pack_current_over_total_esc_continuous_current",
            "component_value": claimed_pack_current_a,
            "comparison_value": esc_continuous_total_a,
            "value": claimed_pack_current_a / esc_continuous_total_a,
            "unit": "ratio",
            "note": "1.5Ah*100C gives a 150A claim, while the 4-in-1 ESC continuous aggregate is 4*65A=260A. Battery C-rating is a claim, not measured usable current.",
        }
    )
    add(
        {
            "row_type": "apdrone_component_config_crosscheck",
            "component_category": "esc",
            "component_name": "Foxeer Reaper F4 65A ESC protocol",
            "dataset_filename": "P09_Foxeer Reaper F4 128K 65A BL32 4in1 ESC.pdf",
            "source_scope": "apdrone_betaflight_config_vs_official_esc_spec",
            "source_page": SOURCE_PAGE,
            "local_source_file": repo_path(SELECTED_DOWNLOADS["Betaflight Configuration for F722.txt"]),
            "spec_name": "betaflight_motor_protocol_supported_by_esc",
            "component_value": "DShot150/300/600/1200",
            "config_value": config.get("motor_pwm_protocol", ""),
            "value": str(config.get("motor_pwm_protocol", "").upper() == "DSHOT600").lower(),
            "unit": "boolean",
            "note": "APdrone Betaflight dump uses DSHOT600; official ESC page lists DShot600 support.",
        }
    )

    return rows


def summarize_battery_flight_csv(
    path: Path,
    scenario: str,
    label: str,
    archive_filename: str,
) -> dict[str, str | float | int]:
    header_line = -1
    metadata: dict[str, str] = {}
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for line_no, line in enumerate(handle):
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                header_line = line_no
                break
            parsed = next(csv.reader([line]))
            if len(parsed) >= 2:
                metadata[parsed[0]] = parsed[1]
    if header_line < 0:
        raise RuntimeError(f"Could not find Blackbox data header in {path}")

    row_count = 0
    armed_rows = 0
    times_s: list[float] = []
    vbat_v: list[float] = []
    current_raw: list[float] = []
    throttle: list[float] = []
    raw_current_ah_seconds = 0.0
    raw_current_vah_seconds = 0.0
    last_t: float | None = None

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        columns = reader.fieldnames or []
        for row in reader:
            row_count += 1
            try:
                t_s = float(row["time"]) / 1e6
            except (KeyError, ValueError):
                continue
            try:
                voltage_v = float(row.get("vbatLatest", "nan")) / 100.0
            except ValueError:
                voltage_v = float("nan")
            try:
                amp_raw = float(row.get("amperageLatest", "nan"))
            except ValueError:
                amp_raw = float("nan")
            try:
                rc_throttle = float(row.get("rcCommands[3]", "nan"))
            except ValueError:
                rc_throttle = float("nan")

            if math.isfinite(voltage_v):
                vbat_v.append(voltage_v)
            if math.isfinite(amp_raw):
                current_raw.append(amp_raw)
            if math.isfinite(rc_throttle):
                throttle.append(rc_throttle)
            try:
                if int(float(row.get("flightModeFlags", "0"))) & 1:
                    armed_rows += 1
            except ValueError:
                pass
            if last_t is not None and math.isfinite(t_s) and 0.0 < t_s - last_t < 2.0:
                dt = t_s - last_t
                if math.isfinite(amp_raw):
                    raw_current_ah_seconds += amp_raw * dt
                    if math.isfinite(voltage_v):
                        raw_current_vah_seconds += voltage_v * amp_raw * dt
            last_t = t_s
            times_s.append(t_s)

    duration_s = max(times_s) - min(times_s) if len(times_s) > 1 else float("nan")
    rate_hz = (len(times_s) - 1) / duration_s if duration_s > 0.0 else float("nan")

    def scaled_capacity_mah(raw_per_amp: float) -> float:
        return raw_current_ah_seconds / raw_per_amp / 3.6

    def scaled_energy_wh(raw_per_amp: float) -> float:
        return raw_current_vah_seconds / raw_per_amp / 3600.0

    raw_capacity_mah = scaled_capacity_mah(1.0)
    raw_per_amp_to_1500mah = raw_capacity_mah / 1500.0 if raw_capacity_mah > 0.0 else float("nan")
    mean_current_from_1500mah = 1.5 / (duration_s / 3600.0) if duration_s > 0.0 else float("nan")

    time_in_12_18_v = 0.0
    for i in range(1, min(len(times_s), len(vbat_v))):
        if (12.0 <= vbat_v[i] <= 18.0) or (12.0 <= vbat_v[i - 1] <= 18.0):
            dt = times_s[i] - times_s[i - 1]
            if 0.0 < dt < 2.0:
                time_in_12_18_v += dt

    vbat_stats = stats(vbat_v)
    current_raw_stats = stats(current_raw)
    throttle_stats = stats(throttle)
    return {
        "row_type": "apdrone_battery_autonomy_flight",
        "scenario": scenario,
        "label": label,
        "filename": path.name,
        "archive_filename": archive_filename,
        "source_page": SOURCE_PAGE,
        "local_source_file": repo_path(path),
        "firmware": metadata.get("firmware", ""),
        "looptime_us": metadata.get("looptime", ""),
        "pid_process_denom": metadata.get("pid_process_denom", ""),
        "blackbox_sample_rate": metadata.get("blackbox_sample_rate", ""),
        "current_meter_scale": metadata.get("currentMeterScale", ""),
        "vbat_scale": metadata.get("vbatscale", ""),
        "vbat_ref_cv": metadata.get("vbatref", ""),
        "row_count": row_count,
        "column_count": len(columns),
        "duration_s": duration_s,
        "estimated_log_rate_hz": rate_hz,
        "armed_rows": armed_rows,
        "time_in_12_18_v_s": time_in_12_18_v,
        "vbat_start_v": vbat_v[0] if vbat_v else "",
        "vbat_end_v": vbat_v[-1] if vbat_v else "",
        "vbat_min_v": vbat_stats.get("min", ""),
        "vbat_p05_v": vbat_stats.get("p05", ""),
        "vbat_mean_v": vbat_stats.get("mean", ""),
        "vbat_median_v": vbat_stats.get("median", ""),
        "vbat_p95_v": vbat_stats.get("p95", ""),
        "vbat_max_v": vbat_stats.get("max", ""),
        "amperage_raw_mean": current_raw_stats.get("mean", ""),
        "amperage_raw_p95": current_raw_stats.get("p95", ""),
        "amperage_raw_max": current_raw_stats.get("max", ""),
        "current_mean_a_if_raw_per_amp_10": current_raw_stats.get("mean", float("nan")) / 10.0,
        "current_p95_a_if_raw_per_amp_10": current_raw_stats.get("p95", float("nan")) / 10.0,
        "energy_wh_if_raw_per_amp_10": scaled_energy_wh(10.0),
        "capacity_mah_if_raw_per_amp_10": scaled_capacity_mah(10.0),
        "current_mean_a_if_raw_per_amp_20": current_raw_stats.get("mean", float("nan")) / 20.0,
        "current_p95_a_if_raw_per_amp_20": current_raw_stats.get("p95", float("nan")) / 20.0,
        "energy_wh_if_raw_per_amp_20": scaled_energy_wh(20.0),
        "capacity_mah_if_raw_per_amp_20": scaled_capacity_mah(20.0),
        "current_mean_a_if_raw_per_amp_100": current_raw_stats.get("mean", float("nan")) / 100.0,
        "current_p95_a_if_raw_per_amp_100": current_raw_stats.get("p95", float("nan")) / 100.0,
        "energy_wh_if_raw_per_amp_100": scaled_energy_wh(100.0),
        "capacity_mah_if_raw_per_amp_100": scaled_capacity_mah(100.0),
        "raw_per_amp_to_integrate_1500mah": raw_per_amp_to_1500mah,
        "mean_current_a_from_1500mah_over_duration": mean_current_from_1500mah,
        "rc_throttle_mean": throttle_stats.get("mean", ""),
        "rc_throttle_p95": throttle_stats.get("p95", ""),
        "rc_throttle_max": throttle_stats.get("max", ""),
        "note": "vbatLatest is interpreted as centivolts. amperageLatest absolute scaling is unresolved; raw_per_amp_20 is a capacity-consistency candidate for the 1500 mAh pack.",
    }


def summarize_battery_autonomy(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    for dataset in BATTERY_AUTONOMY_DATASETS:
        folder_path = str(dataset["folder_path"])
        archive_filename = str(dataset["archive_filename"])
        archive_row = find_inventory_row(inventory_rows, folder_path, archive_filename)
        archive_path = Path(dataset["archive_path"])
        extract_dir = Path(dataset["extract_dir"])
        flight_csvs = extracted_flight_csvs(extract_dir)
        add(
            {
                "row_type": "apdrone_battery_autonomy_archive_metadata",
                "scenario": str(dataset["scenario"]),
                "label": str(dataset["label"]),
                "filename": archive_filename,
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "doi": DOI,
                "source_page": SOURCE_PAGE,
                "download_url": str(archive_row.get("download_url", "")),
                "local_source_file": repo_path(archive_path),
                "size_bytes": int(archive_row.get("size_bytes", 0) or 0),
                "extracted_csv_count": len(flight_csvs),
                "extracted_csv_total_bytes": sum(path.stat().st_size for path in flight_csvs),
                "note": "APdrone battery-autonomy RAR archive; local extraction is required for flight-level rows.",
            }
        )
        script_row = find_inventory_row(inventory_rows, folder_path, str(dataset["script_filename"]))
        add(
            {
                "row_type": "apdrone_battery_autonomy_script_metadata",
                "scenario": str(dataset["scenario"]),
                "label": str(dataset["label"]),
                "filename": str(dataset["script_filename"]),
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "doi": DOI,
                "source_page": SOURCE_PAGE,
                "download_url": str(script_row.get("download_url", "")),
                "local_source_file": repo_path(Path(dataset["script_path"])),
                "analysis_window_samples": 500,
                "script_lower_voltage_threshold_raw": 1200,
                "script_upper_voltage_threshold_raw": 1800,
                "interpreted_lower_voltage_v": 12.0,
                "interpreted_upper_voltage_v": 18.0,
                "note": "The APdrone script labels vbatLatest as mV, but its 1200-1800 thresholds match Betaflight centivolts, i.e. 12-18 V.",
            }
        )

        flight_rows = [
            summarize_battery_flight_csv(
                path,
                str(dataset["scenario"]),
                str(dataset["label"]),
                archive_filename,
            )
            for path in flight_csvs
        ]
        for row in flight_rows:
            add(row)
        if not flight_rows:
            continue

        summary_fields = [
            "duration_s",
            "time_in_12_18_v_s",
            "vbat_start_v",
            "vbat_end_v",
            "vbat_min_v",
            "vbat_mean_v",
            "amperage_raw_mean",
            "amperage_raw_p95",
            "current_mean_a_if_raw_per_amp_20",
            "current_p95_a_if_raw_per_amp_20",
            "energy_wh_if_raw_per_amp_20",
            "capacity_mah_if_raw_per_amp_20",
            "raw_per_amp_to_integrate_1500mah",
            "mean_current_a_from_1500mah_over_duration",
            "rc_throttle_mean",
            "rc_throttle_p95",
        ]
        summary: dict[str, str | float | int] = {
            "row_type": "apdrone_battery_autonomy_scenario_summary",
            "scenario": str(dataset["scenario"]),
            "label": str(dataset["label"]),
            "filename": archive_filename,
            "source_page": SOURCE_PAGE,
            "flight_count": len(flight_rows),
            "note": "Mean/min/max are across the five APdrone battery-autonomy flights in this scenario.",
        }
        for field in summary_fields:
            values = [float(row[field]) for row in flight_rows if row.get(field) != ""]
            summary[f"{field}_mean"] = statistics.fmean(values)
            summary[f"{field}_min"] = min(values)
            summary[f"{field}_max"] = max(values)
        add(summary)

    return rows


def vector_rms(values: list[float]) -> float:
    clean = [value for value in values if math.isfinite(value)]
    if not clean:
        return float("nan")
    return math.sqrt(statistics.fmean([value * value for value in clean]))


def summarize_real_flight_csv(
    path: Path,
    scenario: str,
    label: str,
    archive_filename: str,
) -> dict[str, str | float | int]:
    header_line = -1
    metadata: dict[str, str] = {}
    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for line_no, line in enumerate(handle):
            if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                header_line = line_no
                break
            parsed = next(csv.reader([line]))
            if len(parsed) >= 2:
                metadata[parsed[0]] = parsed[1]
    if header_line < 0:
        raise RuntimeError(f"Could not find Blackbox data header in {path}")

    row_count = 0
    armed_rows = 0
    times_s: list[float] = []
    vbat_v: list[float] = []
    current_raw: list[float] = []
    throttle: list[float] = []
    gps_speed_m_s: list[float] = []
    gps_alt_m: list[float] = []
    baro_alt_m: list[float] = []
    gyro_mag: list[float] = []
    accel_mag: list[float] = []
    setpoint_abs_error = [[], [], []]
    setpoint_values = [[], [], []]
    raw_current_ah_seconds = 0.0
    raw_current_vah_seconds = 0.0
    last_t: float | None = None

    def parse_row_float(row: dict[str, str], key: str, scale: float = 1.0) -> float:
        raw = row.get(key, "")
        if raw in ("", "NaN"):
            return float("nan")
        try:
            return float(raw) / scale
        except ValueError:
            return float("nan")

    with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
        for _ in range(header_line):
            next(handle)
        reader = csv.DictReader(handle)
        columns = reader.fieldnames or []
        for row in reader:
            row_count += 1
            t_s = parse_row_float(row, "time", 1e6)
            voltage_v = parse_row_float(row, "vbatLatest", 100.0)
            amp_raw = parse_row_float(row, "amperageLatest")
            rc_throttle = parse_row_float(row, "rcCommands[3]")
            gps_speed = parse_row_float(row, "GPS_speed", 100.0)
            gps_alt = parse_row_float(row, "GPS_altitude", 100.0)
            baro_alt = parse_row_float(row, "baroAlt", 100.0)

            if math.isfinite(t_s):
                times_s.append(t_s)
            if math.isfinite(voltage_v):
                vbat_v.append(voltage_v)
            if math.isfinite(amp_raw):
                current_raw.append(amp_raw)
            if math.isfinite(rc_throttle):
                throttle.append(rc_throttle)
            if math.isfinite(gps_speed):
                gps_speed_m_s.append(gps_speed)
            if math.isfinite(gps_alt):
                gps_alt_m.append(gps_alt)
            if math.isfinite(baro_alt):
                baro_alt_m.append(baro_alt)

            gyro = [parse_row_float(row, f"gyroADC[{index}]") for index in range(3)]
            if all(math.isfinite(value) for value in gyro):
                gyro_mag.append(math.sqrt(sum(value * value for value in gyro)))
            accel = [parse_row_float(row, f"accSmooth[{index}]") for index in range(3)]
            if all(math.isfinite(value) for value in accel):
                accel_mag.append(math.sqrt(sum(value * value for value in accel)))
            for index in range(3):
                setpoint = parse_row_float(row, f"setpoint[{index}]")
                gyro_value = parse_row_float(row, f"gyroADC[{index}]")
                if math.isfinite(setpoint):
                    setpoint_values[index].append(setpoint)
                if math.isfinite(setpoint) and math.isfinite(gyro_value):
                    setpoint_abs_error[index].append(abs(setpoint - gyro_value))

            try:
                if int(float(row.get("flightModeFlags", "0"))) & 1:
                    armed_rows += 1
            except ValueError:
                pass
            if last_t is not None and math.isfinite(t_s) and 0.0 < t_s - last_t < 2.0:
                dt = t_s - last_t
                if math.isfinite(amp_raw):
                    raw_current_ah_seconds += amp_raw * dt
                    if math.isfinite(voltage_v):
                        raw_current_vah_seconds += voltage_v * amp_raw * dt
            last_t = t_s

    duration_s = max(times_s) - min(times_s) if len(times_s) > 1 else float("nan")
    rate_hz = (len(times_s) - 1) / duration_s if duration_s > 0.0 else float("nan")
    vbat_stats = stats(vbat_v)
    current_stats = stats(current_raw)
    throttle_stats = stats(throttle)
    gps_speed_stats = stats(gps_speed_m_s)
    baro_stats = stats(baro_alt_m)
    gyro_mag_stats = stats(gyro_mag)
    accel_mag_stats = stats(accel_mag)

    def axis_mae(index: int) -> float:
        values = setpoint_abs_error[index]
        return statistics.fmean(values) if values else float("nan")

    def axis_setpoint_range(index: int) -> float:
        values = setpoint_values[index]
        return max(values) - min(values) if values else float("nan")

    def normalized_mae_percent(index: int) -> float:
        range_value = axis_setpoint_range(index)
        mae = axis_mae(index)
        return 100.0 * mae / range_value if range_value > 0.0 else float("nan")

    return {
        "row_type": "apdrone_real_flight_archive_flight",
        "scenario": scenario,
        "label": label,
        "filename": path.name,
        "archive_filename": archive_filename,
        "source_page": SOURCE_PAGE,
        "local_source_file": repo_path(path),
        "firmware": metadata.get("firmware", ""),
        "looptime_us": metadata.get("looptime", ""),
        "pid_process_denom": metadata.get("pid_process_denom", ""),
        "blackbox_sample_rate": metadata.get("blackbox_sample_rate", ""),
        "current_meter_scale": metadata.get("currentMeterScale", ""),
        "vbat_scale": metadata.get("vbatscale", ""),
        "row_count": row_count,
        "column_count": len(columns),
        "duration_s": duration_s,
        "estimated_log_rate_hz": rate_hz,
        "armed_rows": armed_rows,
        "gps_speed_valid_rows": len(gps_speed_m_s),
        "gps_alt_valid_rows": len(gps_alt_m),
        "vbat_start_v": vbat_v[0] if vbat_v else "",
        "vbat_end_v": vbat_v[-1] if vbat_v else "",
        "vbat_min_v": vbat_stats.get("min", ""),
        "vbat_mean_v": vbat_stats.get("mean", ""),
        "vbat_p95_v": vbat_stats.get("p95", ""),
        "amperage_raw_mean": current_stats.get("mean", ""),
        "amperage_raw_p95": current_stats.get("p95", ""),
        "amperage_raw_max": current_stats.get("max", ""),
        "current_mean_a_if_raw_per_amp_20": current_stats.get("mean", float("nan")) / 20.0,
        "current_p95_a_if_raw_per_amp_20": current_stats.get("p95", float("nan")) / 20.0,
        "energy_wh_if_raw_per_amp_20": raw_current_vah_seconds / 20.0 / 3600.0,
        "capacity_mah_if_raw_per_amp_20": raw_current_ah_seconds / 20.0 / 3.6,
        "rc_throttle_mean": throttle_stats.get("mean", ""),
        "rc_throttle_p95": throttle_stats.get("p95", ""),
        "rc_throttle_max": throttle_stats.get("max", ""),
        "gps_speed_mean_m_s": gps_speed_stats.get("mean", ""),
        "gps_speed_p95_m_s": gps_speed_stats.get("p95", ""),
        "gps_speed_max_m_s": gps_speed_stats.get("max", ""),
        "baro_alt_min_m": baro_stats.get("min", ""),
        "baro_alt_max_m": baro_stats.get("max", ""),
        "gyro_vector_rms": vector_rms(gyro_mag),
        "gyro_vector_p95": gyro_mag_stats.get("p95", ""),
        "gyro_vector_max": gyro_mag_stats.get("max", ""),
        "accel_vector_rms": vector_rms(accel_mag),
        "accel_vector_p95": accel_mag_stats.get("p95", ""),
        "accel_vector_max": accel_mag_stats.get("max", ""),
        "roll_setpoint_gyro_mae": axis_mae(0),
        "pitch_setpoint_gyro_mae": axis_mae(1),
        "yaw_setpoint_gyro_mae": axis_mae(2),
        "roll_setpoint_range": axis_setpoint_range(0),
        "pitch_setpoint_range": axis_setpoint_range(1),
        "yaw_setpoint_range": axis_setpoint_range(2),
        "roll_normalized_mae_percent": normalized_mae_percent(0),
        "pitch_normalized_mae_percent": normalized_mae_percent(1),
        "yaw_normalized_mae_percent": normalized_mae_percent(2),
        "note": "vbatLatest is interpreted as centivolts. amperageLatest uses the APdrone battery-autonomy capacity-consistency candidate raw_per_amp=20 for current/energy/capacity fields.",
    }


def summarize_real_flight_archives(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    for dataset in FLIGHT_ARCHIVE_DATASETS:
        folder_path = str(dataset["folder_path"])
        archive_filename = str(dataset["archive_filename"])
        archive_row = find_inventory_row(inventory_rows, folder_path, archive_filename)
        archive_path = Path(dataset["archive_path"])
        extract_dir = Path(dataset["extract_dir"])
        flight_csvs = extracted_flight_csvs(extract_dir)
        add(
            {
                "row_type": "apdrone_real_flight_archive_metadata",
                "scenario": str(dataset["scenario"]),
                "label": str(dataset["label"]),
                "filename": archive_filename,
                "dataset_id": DATASET_ID,
                "dataset_version": DATASET_VERSION,
                "doi": DOI,
                "source_page": SOURCE_PAGE,
                "download_url": str(archive_row.get("download_url", "")),
                "local_source_file": repo_path(archive_path),
                "size_bytes": int(archive_row.get("size_bytes", 0) or 0),
                "extracted_csv_count": len(flight_csvs),
                "extracted_csv_total_bytes": sum(path.stat().st_size for path in flight_csvs),
                "note": "APdrone real-flight RAR archive; local extraction is required for flight-level rows.",
            }
        )
        flight_rows = [
            summarize_real_flight_csv(
                path,
                str(dataset["scenario"]),
                str(dataset["label"]),
                archive_filename,
            )
            for path in flight_csvs
        ]
        for row in flight_rows:
            add(row)
        if not flight_rows:
            continue

        summary_fields = [
            "duration_s",
            "estimated_log_rate_hz",
            "vbat_start_v",
            "vbat_end_v",
            "vbat_mean_v",
            "current_mean_a_if_raw_per_amp_20",
            "current_p95_a_if_raw_per_amp_20",
            "energy_wh_if_raw_per_amp_20",
            "capacity_mah_if_raw_per_amp_20",
            "rc_throttle_mean",
            "rc_throttle_p95",
            "gps_speed_mean_m_s",
            "gps_speed_max_m_s",
            "gyro_vector_rms",
            "gyro_vector_max",
            "roll_setpoint_gyro_mae",
            "pitch_setpoint_gyro_mae",
            "yaw_setpoint_gyro_mae",
        ]
        summary: dict[str, str | float | int] = {
            "row_type": "apdrone_real_flight_archive_scenario_summary",
            "scenario": str(dataset["scenario"]),
            "label": str(dataset["label"]),
            "filename": archive_filename,
            "source_page": SOURCE_PAGE,
            "flight_count": len(flight_rows),
            "gps_valid_flight_count": sum(1 for row in flight_rows if int(row.get("gps_speed_valid_rows", 0) or 0) > 0),
            "note": "Mean/min/max are across extracted APdrone real-flight CSVs in this scenario.",
        }
        for field in summary_fields:
            values = [
                float(row[field])
                for row in flight_rows
                if row.get(field) != "" and math.isfinite(float(row[field]))
            ]
            if not values:
                continue
            summary[f"{field}_mean"] = statistics.fmean(values)
            summary[f"{field}_min"] = min(values)
            summary[f"{field}_max"] = max(values)
        add(summary)

    return rows


def summarize_apdrone_open_field_speed_current_bins(
    inventory_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    open_dataset = next(
        dataset for dataset in FLIGHT_ARCHIVE_DATASETS if dataset["scenario"] == "open_field"
    )
    archive_filename = str(open_dataset["archive_filename"])
    archive_row = find_inventory_row(inventory_rows, str(open_dataset["folder_path"]), archive_filename)
    extract_dir = Path(open_dataset["extract_dir"])
    flight_csvs = extracted_flight_csvs(extract_dir)
    presets = {
        "racingQuad": parse_project_drone_preset_model("racingQuad"),
        "apDrone": parse_project_drone_preset_model("apDrone"),
    }
    if not flight_csvs:
        existing = DATA / "apdrone_open_field_speed_current_bins_reference.csv"
        if existing.exists():
            with existing.open(newline="", encoding="utf-8") as handle:
                existing_rows: list[dict[str, str | float | int]] = [dict(row) for row in csv.DictReader(handle)]
            for row in existing_rows:
                speed_mean = csv_float(row.get("gps_speed_mean_m_s"))
                speed_sq_mean = csv_float(row.get("speed_squared_mean_m2_s2"))
                for preset_name, preset in presets.items():
                    linear_x, body_x = project_drag_terms(preset, "x")
                    linear_z, body_z = project_drag_terms(preset, "z")
                    row[f"{preset_name}_project_drag_x_at_mean_speed_n"] = average_runtime_drag_force(
                        linear_x,
                        body_x,
                        speed_mean,
                        speed_sq_mean,
                    )
                    row[f"{preset_name}_project_drag_z_at_mean_speed_n"] = average_runtime_drag_force(
                        linear_z,
                        body_z,
                        speed_mean,
                        speed_sq_mean,
                    )
            return [{key: finite_or_blank(value) for key, value in row.items()} for row in existing_rows]
    add(
        {
            "row_type": "apdrone_open_field_speed_bin_metadata",
            "scenario": "open_field",
            "label": str(open_dataset["label"]),
            "filename": archive_filename,
            "dataset_id": DATASET_ID,
            "dataset_version": DATASET_VERSION,
            "doi": DOI,
            "source_page": SOURCE_PAGE,
            "download_url": str(archive_row.get("download_url", "")),
            "local_source_file": repo_path(Path(open_dataset["archive_path"])),
            "extracted_csv_count": len(flight_csvs),
            "standard_air_density_kg_m3": STANDARD_AIR_DENSITY_KG_M3,
            "note": "Speed bins use APdrone open-field Blackbox CSV rows. GPS_speed is interpreted as cm/s; current uses the raw_per_amp=20 capacity-consistency candidate.",
        }
    )

    speed_bins = [
        ("0_to_2_m_s", 0.0, 2.0),
        ("2_to_5_m_s", 2.0, 5.0),
        ("5_to_8_m_s", 5.0, 8.0),
        ("8_to_12_m_s", 8.0, 12.0),
        ("12_to_16_m_s", 12.0, 16.0),
        ("16_to_20_m_s", 16.0, 20.0),
    ]

    def make_bucket(name: str, low: float, high: float) -> dict[str, object]:
        return {
            "speed_bin": name,
            "speed_min_m_s": low,
            "speed_max_m_s": high,
            "duration_s": 0.0,
            "gps_speed": [],
            "gps_speed_sq": [],
            "vbat": [],
            "current_a": [],
            "throttle": [],
            "gyro_mag": [],
            "accel_mag": [],
        }

    def make_buckets() -> dict[str, dict[str, object]]:
        return {name: make_bucket(name, low, high) for name, low, high in speed_bins}

    def speed_bin_name(speed_m_s: float) -> str:
        for index, (name, low, high) in enumerate(speed_bins):
            if low <= speed_m_s < high or (index == len(speed_bins) - 1 and speed_m_s <= high):
                return name
        return ""

    def append_value(bucket: dict[str, object], key: str, value: float) -> None:
        if math.isfinite(value):
            values = bucket[key]
            assert isinstance(values, list)
            values.append(value)

    def record_sample(bucket: dict[str, object], values: dict[str, float], dt_s: float) -> None:
        speed = values["gps_speed"]
        append_value(bucket, "gps_speed", speed)
        append_value(bucket, "gps_speed_sq", speed * speed)
        append_value(bucket, "vbat", values["vbat"])
        append_value(bucket, "current_a", values["current_a"])
        append_value(bucket, "throttle", values["throttle"])
        append_value(bucket, "gyro_mag", values["gyro_mag"])
        append_value(bucket, "accel_mag", values["accel_mag"])
        if math.isfinite(dt_s) and 0.0 < dt_s < 0.5:
            bucket["duration_s"] = float(bucket["duration_s"]) + dt_s

    def parse_row_float(row: dict[str, str], key: str, scale: float = 1.0) -> float:
        raw = row.get(key, "")
        if raw in ("", "NaN"):
            return float("nan")
        try:
            return float(raw) / scale
        except ValueError:
            return float("nan")

    def header_line_for(path: Path) -> int:
        with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
            for line_no, line in enumerate(handle):
                if line.startswith('"loopIteration"') or line.startswith("loopIteration"):
                    return line_no
        raise RuntimeError(f"Could not find Blackbox data header in {path}")

    scenario_buckets = make_buckets()

    def emit_bucket(
        *,
        row_type: str,
        bucket: dict[str, object],
        flight_filename: str,
        local_source_file: str,
    ) -> None:
        gps_speed = bucket["gps_speed"]
        assert isinstance(gps_speed, list)
        if not gps_speed:
            return
        speed_stats = stats(gps_speed)
        speed_sq_stats = stats(bucket["gps_speed_sq"])  # type: ignore[arg-type]
        vbat_stats = stats(bucket["vbat"])  # type: ignore[arg-type]
        current_stats = stats(bucket["current_a"])  # type: ignore[arg-type]
        throttle_stats = stats(bucket["throttle"])  # type: ignore[arg-type]
        gyro_values = bucket["gyro_mag"]
        accel_values = bucket["accel_mag"]
        assert isinstance(gyro_values, list)
        assert isinstance(accel_values, list)
        speed_mean = speed_stats.get("mean", float("nan"))
        speed_sq_mean = speed_sq_stats.get("mean", float("nan"))
        row: dict[str, str | float | int] = {
            "row_type": row_type,
            "scenario": "open_field",
            "speed_bin": str(bucket["speed_bin"]),
            "speed_min_m_s": float(bucket["speed_min_m_s"]),
            "speed_max_m_s": float(bucket["speed_max_m_s"]),
            "flight_filename": flight_filename,
            "source_page": SOURCE_PAGE,
            "local_source_file": local_source_file,
            "sample_count": int(speed_stats.get("n", 0.0)),
            "sample_duration_s": float(bucket["duration_s"]),
            "gps_speed_mean_m_s": speed_mean,
            "gps_speed_p95_m_s": speed_stats.get("p95", ""),
            "gps_speed_max_m_s": speed_stats.get("max", ""),
            "speed_squared_mean_m2_s2": speed_sq_mean,
            "mean_dynamic_pressure_pa": 0.5 * STANDARD_AIR_DENSITY_KG_M3 * speed_sq_mean,
            "vbat_mean_v": vbat_stats.get("mean", ""),
            "vbat_min_v": vbat_stats.get("min", ""),
            "current_mean_a_if_raw_per_amp_20": current_stats.get("mean", ""),
            "current_p95_a_if_raw_per_amp_20": current_stats.get("p95", ""),
            "current_max_a_if_raw_per_amp_20": current_stats.get("max", ""),
            "rc_throttle_mean": throttle_stats.get("mean", ""),
            "rc_throttle_p95": throttle_stats.get("p95", ""),
            "rc_throttle_max": throttle_stats.get("max", ""),
            "gyro_vector_rms": vector_rms(gyro_values),
            "gyro_vector_p95": stats(gyro_values).get("p95", ""),
            "accel_vector_rms": vector_rms(accel_values),
            "accel_vector_p95": stats(accel_values).get("p95", ""),
            "note": "Rows are grouped by GPS ground speed. These are not steady-state force-balance bins unless acceleration/wind/attitude are separately filtered.",
        }
        for preset_name, preset in presets.items():
            linear_x, body_x = project_drag_terms(preset, "x")
            linear_z, body_z = project_drag_terms(preset, "z")
            row[f"{preset_name}_project_drag_x_at_mean_speed_n"] = average_runtime_drag_force(
                linear_x,
                body_x,
                speed_mean,
                speed_sq_mean,
            )
            row[f"{preset_name}_project_drag_z_at_mean_speed_n"] = average_runtime_drag_force(
                linear_z,
                body_z,
                speed_mean,
                speed_sq_mean,
            )
        add(row)

    for path in flight_csvs:
        file_buckets = make_buckets()
        last_t: float | None = None
        header_line = header_line_for(path)
        with path.open(newline="", encoding="utf-8-sig", errors="ignore") as handle:
            for _ in range(header_line):
                next(handle)
            reader = csv.DictReader(handle)
            for row in reader:
                t_s = parse_row_float(row, "time", 1e6)
                gps_speed = parse_row_float(row, "GPS_speed", 100.0)
                bin_name = speed_bin_name(gps_speed)
                if not bin_name:
                    if math.isfinite(t_s):
                        last_t = t_s
                    continue
                gyro = [parse_row_float(row, f"gyroADC[{index}]") for index in range(3)]
                accel = [parse_row_float(row, f"accSmooth[{index}]") for index in range(3)]
                sample_values = {
                    "gps_speed": gps_speed,
                    "vbat": parse_row_float(row, "vbatLatest", 100.0),
                    "current_a": parse_row_float(row, "amperageLatest") / 20.0,
                    "throttle": parse_row_float(row, "rcCommands[3]"),
                    "gyro_mag": math.sqrt(sum(value * value for value in gyro))
                    if all(math.isfinite(value) for value in gyro)
                    else float("nan"),
                    "accel_mag": math.sqrt(sum(value * value for value in accel))
                    if all(math.isfinite(value) for value in accel)
                    else float("nan"),
                }
                dt_s = t_s - last_t if last_t is not None and math.isfinite(t_s) else float("nan")
                record_sample(file_buckets[bin_name], sample_values, dt_s)
                record_sample(scenario_buckets[bin_name], sample_values, dt_s)
                if math.isfinite(t_s):
                    last_t = t_s

        for bucket in file_buckets.values():
            emit_bucket(
                row_type="apdrone_open_field_speed_bin_flight",
                bucket=bucket,
                flight_filename=path.name,
                local_source_file=repo_path(path),
            )

    for bucket in scenario_buckets.values():
        emit_bucket(
            row_type="apdrone_open_field_speed_bin_summary",
            bucket=bucket,
            flight_filename="all_open_field_flights",
            local_source_file=repo_path(extract_dir),
        )

    return rows


def csv_float(value: object) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return float("nan")
    return number if math.isfinite(number) else float("nan")


def project_drag_terms(row: dict[str, object], axis: str) -> tuple[float, float]:
    linear = csv_float(row.get("linear_drag_coefficient_n_per_mps"))
    if not math.isfinite(linear):
        linear = csv_float(row.get("linear_drag_coefficient_n_per_mps2"))
    body = csv_float(row.get(f"body_drag_{axis}_n_per_mps2"))
    return linear, body


def average_runtime_drag_force(
    linear_k_n_per_m_s: float,
    quadratic_c_n_per_m_s2: float,
    speed_mean_m_s: float,
    speed_sq_mean_m2_s2: float,
) -> float:
    if not (
        math.isfinite(linear_k_n_per_m_s)
        and math.isfinite(quadratic_c_n_per_m_s2)
        and math.isfinite(speed_mean_m_s)
        and math.isfinite(speed_sq_mean_m2_s2)
    ):
        return float("nan")
    return linear_k_n_per_m_s * speed_mean_m_s + quadratic_c_n_per_m_s2 * speed_sq_mean_m2_s2


def first_row(
    rows: list[dict[str, str | float | int]],
    *,
    row_type: str,
    scenario: str | None = None,
    name: str | None = None,
    metric: str | None = None,
) -> dict[str, str | float | int]:
    for row in rows:
        if row.get("row_type") != row_type:
            continue
        if scenario is not None and row.get("scenario") != scenario:
            continue
        if name is not None and row.get("name") != name:
            continue
        if metric is not None and row.get("metric") != metric:
            continue
        return row
    return {}


def load_mqtb_hq5x4x3_hover_row() -> dict[str, str | float | int]:
    path = DATA / "mqtb_hq5x4x3_current_model_reference.csv"
    if not path.exists():
        return {}
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if (
                row.get("row_type") == "current_vs_mqtb_hq5x4x3_current_model"
                and row.get("operating_point") == "hover"
                and row.get("preset") == "racingQuad"
            ):
                return row
    return {}


def mqtb_hq5x4x3_hover_current_a(preset: dict[str, str | float | int]) -> float:
    fit_row = load_mqtb_hq5x4x3_hover_row()
    fit_a = csv_float(fit_row.get("mqtb_fit_current_fit_a"))
    fit_b = csv_float(fit_row.get("mqtb_fit_current_fit_b"))
    if not math.isfinite(fit_a) or not math.isfinite(fit_b):
        return float("nan")
    hover_thrust = csv_float(preset.get("hover_thrust_per_motor_n"))
    rotor_count = csv_float(preset.get("rotor_count"))
    if hover_thrust <= 0.0 or rotor_count <= 0.0:
        return float("nan")
    return rotor_count * fit_a * hover_thrust**fit_b


def summarize_apdrone_flight_vs_model(
    selected_flight_rows: list[dict[str, str | float | int]],
    battery_autonomy_rows: list[dict[str, str | float | int]],
    real_flight_archive_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    presets = [parse_project_drone_preset_model("racingQuad")]
    try:
        presets.append(parse_project_drone_preset_model("apDrone"))
    except RuntimeError:
        pass

    for preset in presets:
        add(preset)

    selected_speed = csv_float(
        first_row(
            selected_flight_rows,
            row_type="apdrone_selected_flight_metric",
            name="GPS_speed",
            metric="max",
        ).get("value")
    )
    open_summary = first_row(
        real_flight_archive_rows,
        row_type="apdrone_real_flight_archive_scenario_summary",
        scenario="open_field",
    )
    urban_summary = first_row(
        real_flight_archive_rows,
        row_type="apdrone_real_flight_archive_scenario_summary",
        scenario="urban_environment",
    )
    max_power_summary = first_row(
        battery_autonomy_rows,
        row_type="apdrone_battery_autonomy_scenario_summary",
        scenario="max_power",
    )
    normal_power_summary = first_row(
        battery_autonomy_rows,
        row_type="apdrone_battery_autonomy_scenario_summary",
        scenario="normal_power",
    )

    speed_points = [
        (
            "selected_flight_gps_speed_max",
            selected_speed,
            "Selected Flight.csv GPS_speed max; this is a low-speed open-field subset.",
        ),
        (
            "open_field_mean_of_file_max_gps_speed",
            csv_float(open_summary.get("gps_speed_max_m_s_mean")),
            "Mean of per-file GPS-speed maxima across the five open-field real-flight CSVs.",
        ),
        (
            "open_field_fastest_gps_speed",
            csv_float(open_summary.get("gps_speed_max_m_s_max")),
            "Fastest GPS speed found across the five open-field real-flight CSVs.",
        ),
    ]

    for preset in presets:
        preset_name = str(preset["preset"])
        margin = csv_float(preset.get("level_horizontal_thrust_margin_n"))
        linear_x, body_drag_x = project_drag_terms(preset, "x")
        linear_z, body_drag_z = project_drag_terms(preset, "z")
        limit_x = csv_float(preset.get("drag_limited_level_speed_x_m_s"))
        limit_z = csv_float(preset.get("drag_limited_level_speed_z_m_s"))
        for speed_name, speed, note in speed_points:
            if not math.isfinite(speed):
                continue
            drag_force_x = drag_force(linear_x, body_drag_x, speed)
            drag_force_z = drag_force(linear_z, body_drag_z, speed)
            add(
                {
                    "row_type": "apdrone_speed_vs_project_drag",
                    "name": speed_name,
                    "preset": preset_name,
                    "source_page": SOURCE_PAGE,
                    "reference_dataset": "APdrone Mendeley Data v2",
                    "speed_m_s": speed,
                    "project_drag_x_n": drag_force_x,
                    "project_drag_z_n": drag_force_z,
                    "level_horizontal_thrust_margin_n": margin,
                    "drag_x_over_level_margin": drag_force_x / margin if margin > 0.0 else "",
                    "drag_z_over_level_margin": drag_force_z / margin if margin > 0.0 else "",
                    "drag_limited_level_speed_x_m_s": limit_x,
                    "drag_limited_level_speed_z_m_s": limit_z,
                    "speed_over_drag_limited_x": speed / limit_x if limit_x > 0.0 else "",
                    "speed_over_drag_limited_z": speed / limit_z if limit_z > 0.0 else "",
                    "note": note,
                }
            )

    current_points = [
        (
            "battery_max_power_mean_current_raw_per_amp_20",
            csv_float(max_power_summary.get("current_mean_a_if_raw_per_amp_20_mean")),
            "Mean candidate pack current across APdrone max-power battery-autonomy flights.",
        ),
        (
            "battery_max_power_p95_current_raw_per_amp_20",
            csv_float(max_power_summary.get("current_p95_a_if_raw_per_amp_20_mean")),
            "Mean of per-flight P95 candidate pack current across APdrone max-power battery-autonomy flights.",
        ),
        (
            "battery_normal_power_mean_current_raw_per_amp_20",
            csv_float(normal_power_summary.get("current_mean_a_if_raw_per_amp_20_mean")),
            "Mean candidate pack current across APdrone normal-power battery-autonomy flights.",
        ),
        (
            "open_field_mean_current_raw_per_amp_20",
            csv_float(open_summary.get("current_mean_a_if_raw_per_amp_20_mean")),
            "Mean candidate pack current across APdrone open-field real-flight CSVs.",
        ),
        (
            "urban_mean_current_raw_per_amp_20",
            csv_float(urban_summary.get("current_mean_a_if_raw_per_amp_20_mean")),
            "Mean candidate pack current across APdrone urban real-flight CSVs.",
        ),
    ]

    apdrone_pack_claim_a = 150.0
    for preset in presets:
        preset_name = str(preset["preset"])
        max_current = csv_float(preset.get("max_battery_current_a"))
        hover_current = mqtb_hq5x4x3_hover_current_a(preset)
        for current_name, current_a, note in current_points:
            if not math.isfinite(current_a):
                continue
            add(
                {
                    "row_type": "apdrone_current_vs_project_battery",
                    "name": current_name,
                    "preset": preset_name,
                    "source_page": SOURCE_PAGE,
                    "reference_dataset": "APdrone Mendeley Data v2",
                    "candidate_current_a": current_a,
                    "project_max_battery_current_a": max_current,
                    "candidate_current_over_project_limit": current_a / max_current if max_current > 0.0 else "",
                    "apdrone_battery_filename_claim_current_a": apdrone_pack_claim_a,
                    "candidate_current_over_apdrone_pack_claim": current_a / apdrone_pack_claim_a,
                    "mqtb_hq5x4x3_estimated_hover_current_a": hover_current,
                    "candidate_current_over_mqtb_hover_estimate": current_a / hover_current if hover_current > 0.0 else "",
                    "note": f"{note} Current uses the raw_per_amp=20 capacity-consistency candidate, not a lab-calibrated current sensor.",
                }
            )

    return rows


def summarize_apdrone_drag_speed_envelope(
    selected_flight_rows: list[dict[str, str | float | int]],
    real_flight_archive_rows: list[dict[str, str | float | int]],
    flight_vs_model_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []
    speed_points: list[dict[str, str | float | int]] = []

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    def add_speed_point(
        *,
        speed_point: str,
        speed_m_s: float,
        source_page: str,
        source_scope: str,
        note: str,
        flight_filename: str = "",
    ) -> None:
        if not math.isfinite(speed_m_s):
            return
        point: dict[str, str | float | int] = {
            "speed_point": speed_point,
            "speed_m_s": speed_m_s,
            "source_page": source_page,
            "source_scope": source_scope,
            "flight_filename": flight_filename,
            "note": note,
        }
        speed_points.append(point)
        add(
            {
                "row_type": "apdrone_speed_reference_point",
                "reference_dataset": "APdrone Mendeley Data v2",
                **point,
            }
        )

    add(
        {
            "row_type": "apdrone_drag_speed_envelope_method",
            "reference_dataset": "APdrone Mendeley Data v2",
            "source_page": SOURCE_PAGE,
            "doi": DOI,
            "standard_air_density_kg_m3": STANDARD_AIR_DENSITY_KG_M3,
            "equation": "F_drag = c * V^2; CdA_equiv = 2*c/rho; c_limit = level_horizontal_thrust_margin / V^2.",
            "note": "Level-flight full-thrust upper-bound check. GPS ground speed is used as an airspeed proxy; wind, attitude, acceleration, propwash/body coupling, and controller limits are ignored.",
        }
    )

    selected_source = str(
        first_row(
            selected_flight_rows,
            row_type="apdrone_selected_flight_metric",
            name="GPS_speed",
            metric="max",
        ).get("source", SOURCE_PAGE)
    )
    for metric, label in [
        ("mean", "Mean GPS_speed in Selected Flight.csv."),
        ("p95", "P95 GPS_speed in Selected Flight.csv."),
        ("max", "Max GPS_speed in Selected Flight.csv."),
    ]:
        selected_metric = first_row(
            selected_flight_rows,
            row_type="apdrone_selected_flight_metric",
            name="GPS_speed",
            metric=metric,
        )
        add_speed_point(
            speed_point=f"selected_flight_gps_speed_{metric}",
            speed_m_s=csv_float(selected_metric.get("value")),
            source_page=selected_source,
            source_scope="selected_flight_csv",
            note=f"{label} Betaflight GPS_speed is interpreted as cm/s.",
            flight_filename="Selected Flight.csv",
        )

    open_summary = first_row(
        real_flight_archive_rows,
        row_type="apdrone_real_flight_archive_scenario_summary",
        scenario="open_field",
    )
    add_speed_point(
        speed_point="open_field_mean_gps_speed_across_files",
        speed_m_s=csv_float(open_summary.get("gps_speed_mean_m_s_mean")),
        source_page=str(open_summary.get("source_page", SOURCE_PAGE)),
        source_scope="open_field_scenario_summary",
        note="Mean of per-file mean GPS speeds across the five open-field real-flight CSVs.",
        flight_filename=str(open_summary.get("filename", "")),
    )
    add_speed_point(
        speed_point="open_field_mean_of_file_max_gps_speed",
        speed_m_s=csv_float(open_summary.get("gps_speed_max_m_s_mean")),
        source_page=str(open_summary.get("source_page", SOURCE_PAGE)),
        source_scope="open_field_scenario_summary",
        note="Mean of per-file GPS-speed maxima across the five open-field real-flight CSVs.",
        flight_filename=str(open_summary.get("filename", "")),
    )
    add_speed_point(
        speed_point="open_field_fastest_gps_speed",
        speed_m_s=csv_float(open_summary.get("gps_speed_max_m_s_max")),
        source_page=str(open_summary.get("source_page", SOURCE_PAGE)),
        source_scope="open_field_scenario_summary",
        note="Fastest GPS speed found across the five open-field real-flight CSVs.",
        flight_filename=str(open_summary.get("filename", "")),
    )

    for flight_row in real_flight_archive_rows:
        if flight_row.get("row_type") != "apdrone_real_flight_archive_flight":
            continue
        if flight_row.get("scenario") != "open_field":
            continue
        filename = str(flight_row.get("filename", ""))
        add_speed_point(
            speed_point=f"open_field_{Path(filename).stem.lower()}_gps_speed_p95",
            speed_m_s=csv_float(flight_row.get("gps_speed_p95_m_s")),
            source_page=str(flight_row.get("source_page", SOURCE_PAGE)),
            source_scope="open_field_flight_csv",
            note="Per-file P95 GPS speed from an extracted APdrone open-field real-flight CSV.",
            flight_filename=filename,
        )
        add_speed_point(
            speed_point=f"open_field_{Path(filename).stem.lower()}_gps_speed_max",
            speed_m_s=csv_float(flight_row.get("gps_speed_max_m_s")),
            source_page=str(flight_row.get("source_page", SOURCE_PAGE)),
            source_scope="open_field_flight_csv",
            note="Per-file max GPS speed from an extracted APdrone open-field real-flight CSV.",
            flight_filename=filename,
        )

    preset_rows = [
        row
        for row in flight_vs_model_rows
        if row.get("row_type") == "project_preset_model"
        and row.get("preset") in {"racingQuad", "apDrone"}
    ]
    for preset in preset_rows:
        preset_name = str(preset.get("preset", ""))
        linear_x, body_x = project_drag_terms(preset, "x")
        linear_z, body_z = project_drag_terms(preset, "z")
        drag_x = equivalent_quadratic_c(linear_x, body_x, PROJECT_DRAG_REFERENCE_SPEED_M_S)
        drag_z = equivalent_quadratic_c(linear_z, body_z, PROJECT_DRAG_REFERENCE_SPEED_M_S)
        add(
            {
                "row_type": "project_preset_drag_model",
                "preset": preset_name,
                "source_page": preset.get("source_page", ""),
                "local_source_file": preset.get("local_source_file", ""),
                "mass_kg": preset.get("mass_kg", ""),
                "max_total_thrust_n": preset.get("max_total_thrust_n", ""),
                "level_horizontal_thrust_margin_n": preset.get("level_horizontal_thrust_margin_n", ""),
                "force_law": "F=linearDragCoefficient*V+bodyAxisDrag*V^2",
                "linear_drag_coefficient_n_per_mps": linear_x,
                "total_drag_reference_speed_m_s": PROJECT_DRAG_REFERENCE_SPEED_M_S,
                "body_drag_x_n_per_mps2": body_x,
                "body_drag_z_n_per_mps2": body_z,
                "total_drag_x_n_per_mps2": drag_x,
                "total_drag_z_n_per_mps2": drag_z,
                "equivalent_cda_x_m2": equivalent_cda(linear_x, body_x, PROJECT_DRAG_REFERENCE_SPEED_M_S),
                "equivalent_cda_z_m2": equivalent_cda(linear_z, body_z, PROJECT_DRAG_REFERENCE_SPEED_M_S),
                "drag_limited_level_speed_x_m_s": preset.get("drag_limited_level_speed_x_m_s", ""),
                "drag_limited_level_speed_z_m_s": preset.get("drag_limited_level_speed_z_m_s", ""),
                "note": f"Project runtime drag model. total_drag_* and equivalent CdA are force-equivalent values at {PROJECT_DRAG_REFERENCE_SPEED_M_S:g} m/s, not measured wind-tunnel CdA.",
            }
        )

    for point in speed_points:
        speed = csv_float(point.get("speed_m_s"))
        if speed <= 0.0:
            continue
        for preset in preset_rows:
            preset_name = str(preset.get("preset", ""))
            margin = csv_float(preset.get("level_horizontal_thrust_margin_n"))
            for axis in ["x", "z"]:
                linear, body = project_drag_terms(preset, axis)
                if not (math.isfinite(linear) and math.isfinite(body) and margin > 0.0):
                    continue
                required_drag = drag_force(linear, body, speed)
                if required_drag <= 0.0:
                    continue
                coefficient = equivalent_quadratic_c(linear, body, speed)
                speed_limit = terminal_speed_m_s(margin, linear, body)
                allowable_coefficient = margin / speed**2
                add(
                    {
                        "row_type": "apdrone_drag_speed_constraint",
                        "reference_dataset": "APdrone Mendeley Data v2",
                        "preset": preset_name,
                        "axis": axis,
                        "speed_point": point.get("speed_point", ""),
                        "speed_m_s": speed,
                        "speed_source_page": point.get("source_page", ""),
                        "source_scope": point.get("source_scope", ""),
                        "flight_filename": point.get("flight_filename", ""),
                        "standard_air_density_kg_m3": STANDARD_AIR_DENSITY_KG_M3,
                        "force_law": "F=linearDragCoefficient*V+bodyAxisDrag*V^2",
                        "linear_drag_coefficient_n_per_mps": linear,
                        "body_drag_coefficient_n_per_mps2": body,
                        "total_drag_coefficient_n_per_mps2": coefficient,
                        "equivalent_cda_m2": equivalent_cda(linear, body, speed),
                        "level_horizontal_thrust_margin_n": margin,
                        "required_drag_force_n": required_drag,
                        "residual_horizontal_margin_n": margin - required_drag,
                        "drag_over_level_margin": required_drag / margin,
                        "max_allowable_drag_coefficient_n_per_mps2": allowable_coefficient,
                        "max_allowable_equivalent_cda_m2": 2.0 * allowable_coefficient / STANDARD_AIR_DENSITY_KG_M3,
                        "coefficient_over_max_allowable": coefficient / allowable_coefficient,
                        "drag_limited_level_speed_m_s": speed_limit,
                        "speed_over_drag_limited": speed / speed_limit if speed_limit > 0.0 else "",
                        "note": "If coefficient_over_max_allowable exceeds 1, this simplified full-thrust level-flight check cannot sustain the logged GPS speed on that project axis. Coefficient and CdA are speed-point equivalents of the runtime linear-plus-quadratic drag law.",
                    }
                )

    return rows


def component_spec_row(
    rows: list[dict[str, str | float | int]],
    spec_name: str,
    component_category: str | None = None,
) -> dict[str, str | float | int]:
    for row in rows:
        if row.get("spec_name") != spec_name:
            continue
        if component_category is not None and row.get("component_category") != component_category:
            continue
        return row
    return {}


def config_setting(
    rows: list[dict[str, str | float | int]],
    metric: str,
) -> dict[str, str | float | int]:
    return first_row(rows, row_type="apdrone_betaflight_config_setting", metric=metric)


def summarize_apdrone_preset_source_match(
    selected_flight_rows: list[dict[str, str | float | int]],
    inertia_rows: list[dict[str, str | float | int]],
    component_spec_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []
    preset = parse_project_drone_preset_model("apDrone")
    source_total = next(
        (row for row in inertia_rows if row.get("name") == "APdrone FPV drone total"),
        {},
    )

    def source_value_from_component(spec_name: str, category: str | None = None) -> tuple[object, str, str]:
        row = component_spec_row(component_spec_rows, spec_name, category)
        return row.get("value", ""), str(row.get("unit", "")), str(row.get("source_page", SOURCE_PAGE))

    diameter_in, _, diameter_source = source_value_from_component("diameter_in", "propeller")
    pitch_in, _, pitch_source = source_value_from_component("pitch_in", "propeller")
    blade_count, blade_count_unit, blade_source = source_value_from_component("blade_count", "propeller")
    capacity_mah, _, capacity_source = source_value_from_component(
        "capacity_mah_from_component_pdf_filename",
        "battery",
    )
    nominal_voltage, _, voltage_source = source_value_from_component(
        "nominal_voltage_v_from_component_pdf_filename",
        "battery",
    )
    claimed_current, _, current_source = source_value_from_component(
        "claimed_max_discharge_current_a",
        "battery",
    )
    esc_protocols, _, esc_source = source_value_from_component("supported_protocols", "esc")

    def add(
        field: str,
        project_value: object,
        source_value: object,
        unit: str,
        source_name: str,
        source_page: str,
        match_status: str,
        transform: str = "",
        note: str = "",
    ) -> None:
        project_number = csv_float(project_value)
        source_number = csv_float(source_value)
        ratio = (
            project_number / source_number
            if math.isfinite(project_number) and math.isfinite(source_number) and source_number != 0.0
            else ""
        )
        relative_error = (
            (project_number - source_number) / source_number
            if math.isfinite(project_number) and math.isfinite(source_number) and source_number != 0.0
            else ""
        )
        rows.append(
            {
                "row_type": "apdrone_preset_source_match",
                "preset": "apDrone",
                "field": field,
                "project_value": project_value,
                "source_value": source_value,
                "unit": unit,
                "source_name": source_name,
                "source_page": source_page,
                "project_over_source": ratio,
                "relative_error": relative_error,
                "match_status": match_status,
                "transform": transform,
                "note": note,
            }
        )

    add("mass_kg", preset.get("mass_kg"), source_total.get("mass_kg"), "kg", "APdrone inertia PDF", SOURCE_PAGE, "matches_source")
    add(
        "inertia_x_kg_m2",
        preset.get("inertia_x_kg_m2"),
        source_total.get("inertia_x_kg_m2"),
        "kg*m^2",
        "APdrone inertia PDF",
        SOURCE_PAGE,
        "matches_source",
        "project X maps to APdrone source X",
    )
    add(
        "inertia_yaw_project_y_kg_m2",
        preset.get("inertia_y_kg_m2"),
        source_total.get("inertia_z_kg_m2"),
        "kg*m^2",
        "APdrone inertia PDF",
        SOURCE_PAGE,
        "matches_source_after_axis_mapping",
        "project Y is yaw; APdrone source Z is yaw",
    )
    add(
        "inertia_project_z_kg_m2",
        preset.get("inertia_z_kg_m2"),
        source_total.get("inertia_y_kg_m2"),
        "kg*m^2",
        "APdrone inertia PDF",
        SOURCE_PAGE,
        "matches_source_after_axis_mapping",
        "project Z maps to APdrone source Y",
    )
    add(
        "motor_center_radius_m",
        preset.get("motor_center_radius_m"),
        source_total.get("motor_center_radius_m"),
        "m",
        "APdrone inertia PDF",
        SOURCE_PAGE,
        "matches_source",
    )
    add(
        "rotor_radius_m",
        preset.get("rotor_radius_m"),
        csv_float(diameter_in) * 0.0254 * 0.5,
        "m",
        "Foxeer Donut 5145 prop spec",
        diameter_source,
        "matches_source_after_unit_conversion",
        "diameter_in * 0.0254 / 2",
    )
    add(
        "rotor_blade_count",
        preset.get("rotor_blade_count"),
        blade_count,
        blade_count_unit,
        "Foxeer Donut 5145 prop vendor/spec evidence",
        blade_source,
        "matches_source",
    )
    add(
        "rotor_blade_pitch_to_diameter_ratio",
        preset.get("rotor_blade_pitch_to_diameter_ratio"),
        csv_float(pitch_in) / csv_float(diameter_in),
        "pitch/diameter",
        "Foxeer Donut 5145 prop spec",
        pitch_source,
        "matches_source_after_unit_conversion",
        "pitch_in / diameter_in",
    )
    add(
        "battery_capacity_ah",
        preset.get("battery_capacity_ah"),
        csv_float(capacity_mah) / 1000.0,
        "Ah",
        "APdrone battery filename and Betaflight bat_capacity",
        capacity_source,
        "matches_source_after_unit_conversion",
        "capacity_mah / 1000",
    )
    add(
        "nominal_battery_voltage_v",
        preset.get("nominal_battery_voltage_v"),
        4.2 * 4.0,
        "V",
        "4S full-charge voltage convention",
        voltage_source,
        "matches_full_charge_voltage_not_filename_nominal",
        "4 cells * 4.2 V/cell",
        f"The APdrone filename nominal voltage is {nominal_voltage} V; project field name is nominalBatteryVoltage but value equals 4S full charge.",
    )
    add(
        "max_battery_current_a",
        preset.get("max_battery_current_a"),
        claimed_current,
        "A",
        "APdrone battery filename 1500mAh 100C claim",
        current_source,
        "matches_source",
    )

    for project_field, betaflight_metric, unit in [
        ("gyro_low_pass_cutoff_hz", "gyro_lpf1_static_hz", "Hz"),
        ("esc_command_frame_rate_hz", "motor_pwm_rate", "Hz"),
    ]:
        setting = config_setting(selected_flight_rows, betaflight_metric)
        add(
            project_field,
            preset.get(project_field),
            setting.get("value", ""),
            unit,
            f"APdrone Betaflight setting {betaflight_metric}",
            str(setting.get("source", SOURCE_PAGE)),
            "matches_betaflight_dump",
        )

    for project_field in ("max_roll_rate_deg_s", "max_pitch_rate_deg_s", "max_yaw_rate_deg_s"):
        add(
            project_field,
            preset.get(project_field),
            670.0,
            "deg/s",
            "APdrone selected Betaflight Actual Rates full-stick target",
            "docs/data/apdrone_rate_envelope_reference.csv",
            "matches_selected_actual_rate",
            note="The Betaflight dump/open-field rate_limit remains 1998 deg/s, but rate_limit is a final clamp; current apDrone selects the urban/battery Actual target rates=67 -> 670 deg/s.",
        )

    protocol_setting = config_setting(selected_flight_rows, "motor_pwm_protocol")
    add(
        "esc_command_protocol",
        preset.get("esc_command_protocol"),
        protocol_setting.get("value", ""),
        "protocol",
        "APdrone Betaflight motor_pwm_protocol",
        str(protocol_setting.get("source", SOURCE_PAGE)),
        "matches_betaflight_dump",
        note=f"Component ESC page lists supported protocols as {esc_protocols}.",
    )

    for field, unit, note in [
        ("battery_internal_resistance_ohm", "ohm", "No direct APdrone pack ESR measurement has been extracted yet."),
        ("linear_drag_coefficient_n_per_mps", "N/(m/s)", "No direct APdrone airframe linear damping fit is available; use apdrone_flight_vs_model_reference.csv as a speed-envelope sanity check."),
        ("linear_drag_coefficient_n_per_mps2", "N/(m/s)", "Backward-compatible alias for the runtime linear damping coefficient; it is not a quadratic drag coefficient."),
        ("body_drag_x_n_per_mps2", "N/(m/s)^2", "No direct APdrone axis-specific drag fit is available."),
        ("body_drag_z_n_per_mps2", "N/(m/s)^2", "No direct APdrone axis-specific drag fit is available."),
        ("max_rotor_thrust_n", "N", "APdrone exact motor/prop thrust table is still missing; current value needs static-thrust or RPM/current validation."),
        ("thrust_coefficient_n_per_rad2_s2", "N/(rad/s)^2", "APdrone exact motor/prop thrust coefficient is still missing."),
    ]:
        add(
            field,
            preset.get(field),
            "",
            unit,
            "not found in extracted APdrone source files",
            SOURCE_PAGE,
            "no_direct_apdrone_source",
            note=note,
        )

    return [{key: finite_or_blank(value) for key, value in row.items()} for row in rows]


def summarize_apdrone_article_performance(
    battery_autonomy_rows: list[dict[str, str | float | int]],
    flight_vs_model_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []
    model = first_row(flight_vs_model_rows, row_type="project_preset_model", name=None)
    for row in flight_vs_model_rows:
        if row.get("row_type") == "project_preset_model" and row.get("preset") == "apDrone":
            model = row
            break

    mass_kg = csv_float(model.get("mass_kg"))
    max_total_thrust_n = csv_float(model.get("max_total_thrust_n"))
    battery_capacity_ah = csv_float(model.get("battery_capacity_ah"))
    nominal_voltage_v = csv_float(model.get("nominal_battery_voltage_v"))
    configured_energy_wh = battery_capacity_ah * nominal_voltage_v if battery_capacity_ah > 0 and nominal_voltage_v > 0 else float("nan")

    article_standard_flight_time_s = 8.0 * 60.0
    article_max_payload_kg = 0.98
    gross_mass_with_payload_kg = mass_kg + article_max_payload_kg
    gross_weight_n = gross_mass_with_payload_kg * 9.80665
    empty_weight_n = mass_kg * 9.80665
    payload_weight_n = article_max_payload_kg * 9.80665

    normal_summary = first_row(
        battery_autonomy_rows,
        row_type="apdrone_battery_autonomy_scenario_summary",
        scenario="normal_power",
    )
    max_power_summary = first_row(
        battery_autonomy_rows,
        row_type="apdrone_battery_autonomy_scenario_summary",
        scenario="max_power",
    )
    normal_duration_s = csv_float(normal_summary.get("duration_s_mean"))
    max_power_duration_s = csv_float(max_power_summary.get("duration_s_mean"))
    normal_energy_wh = csv_float(normal_summary.get("energy_wh_if_raw_per_amp_20_mean"))
    max_power_energy_wh = csv_float(max_power_summary.get("energy_wh_if_raw_per_amp_20_mean"))

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    add(
        {
            "row_type": "apdrone_article_metadata",
            "name": "Design, assembly, and tuning of a multipurpose FPV drone",
            "doi": ARTICLE_DOI,
            "source_page": ARTICLE_PAGE,
            "dataset_page": SOURCE_PAGE,
            "note": "APdrone article-level performance claims are used as high-level envelope checks; detailed motor/drag coefficients still require raw flight or bench data.",
        }
    )
    add(
        {
            "row_type": "apdrone_article_performance_claim",
            "name": "standard_flight_time",
            "source_page": ARTICLE_PAGE,
            "value": article_standard_flight_time_s,
            "unit": "s",
            "note": "Article abstract reports a standard flight time of about eight minutes.",
        }
    )
    add(
        {
            "row_type": "apdrone_article_performance_claim",
            "name": "maximum_payload",
            "source_page": ARTICLE_PAGE,
            "value": article_max_payload_kg,
            "unit": "kg",
            "note": "Article abstract reports maximum load capacity of 0.98 kg.",
        }
    )
    add(
        {
            "row_type": "apdrone_article_vs_logs",
            "name": "standard_flight_time_vs_normal_power_logs",
            "source_page": ARTICLE_PAGE,
            "dataset_page": SOURCE_PAGE,
            "article_flight_time_s": article_standard_flight_time_s,
            "normal_power_log_duration_s": normal_duration_s,
            "normal_power_log_over_article": normal_duration_s / article_standard_flight_time_s if article_standard_flight_time_s > 0 else "",
            "max_power_log_duration_s": max_power_duration_s,
            "max_power_log_over_article": max_power_duration_s / article_standard_flight_time_s if article_standard_flight_time_s > 0 else "",
            "normal_power_energy_wh_raw_per_amp_20": normal_energy_wh,
            "max_power_energy_wh_raw_per_amp_20": max_power_energy_wh,
            "configured_battery_energy_wh_using_full_voltage_field": configured_energy_wh,
            "note": "Normal-power APdrone battery-autonomy logs agree with the article's eight-minute scale; max-power logs are shorter as expected.",
        }
    )
    add(
        {
            "row_type": "apdrone_article_vs_project_model",
            "name": "maximum_payload_vs_apdrone_thrust_margin",
            "source_page": ARTICLE_PAGE,
            "dataset_page": SOURCE_PAGE,
            "preset": "apDrone",
            "empty_mass_kg": mass_kg,
            "article_payload_kg": article_max_payload_kg,
            "gross_mass_with_payload_kg": gross_mass_with_payload_kg,
            "empty_weight_n": empty_weight_n,
            "payload_weight_n": payload_weight_n,
            "gross_weight_with_payload_n": gross_weight_n,
            "configured_max_total_thrust_n": max_total_thrust_n,
            "configured_empty_thrust_to_weight": max_total_thrust_n / empty_weight_n if empty_weight_n > 0 else "",
            "configured_payload_thrust_to_weight": max_total_thrust_n / gross_weight_n if gross_weight_n > 0 else "",
            "payload_weight_over_empty_weight": payload_weight_n / empty_weight_n if empty_weight_n > 0 else "",
            "note": "This is a static thrust-margin check against the article payload claim. It does not prove flight controllability with payload, propwash, or thermal limits.",
        }
    )
    return rows


def summarize_apdrone_battery_esr_proxy(
    battery_autonomy_rows: list[dict[str, str | float | int]],
    flight_vs_model_rows: list[dict[str, str | float | int]],
) -> list[dict[str, str | float | int]]:
    rows: list[dict[str, str | float | int]] = []

    model = next(
        (
            row
            for row in flight_vs_model_rows
            if row.get("row_type") == "project_preset_model" and row.get("preset") == "apDrone"
        ),
        {},
    )
    resistance_ohm = csv_float(model.get("battery_internal_resistance_ohm"))
    full_voltage_v = csv_float(model.get("nominal_battery_voltage_v"))

    summaries = {
        scenario: first_row(
            battery_autonomy_rows,
            row_type="apdrone_battery_autonomy_scenario_summary",
            scenario=scenario,
        )
        for scenario in ("normal_power", "max_power")
    }

    def add(row: dict[str, str | float | int]) -> None:
        rows.append({key: finite_or_blank(value) for key, value in row.items()})

    add(
        {
            "row_type": "apdrone_battery_esr_proxy_metadata",
            "source_page": SOURCE_PAGE,
            "preset": "apDrone",
            "configured_battery_internal_resistance_ohm": resistance_ohm,
            "note": "These are APdrone log-derived voltage-drop proxies, not laboratory DCIR/ACIR measurements. SOC, heating, and maneuver differences remain coupled.",
        }
    )

    scenario_metrics: dict[str, dict[str, float]] = {}
    for scenario, summary in summaries.items():
        current_mean = csv_float(summary.get("current_mean_a_if_raw_per_amp_20_mean"))
        current_p95 = csv_float(summary.get("current_p95_a_if_raw_per_amp_20_mean"))
        v_start = csv_float(summary.get("vbat_start_v_mean"))
        v_mean = csv_float(summary.get("vbat_mean_v_mean"))
        duration = csv_float(summary.get("duration_s_mean"))
        energy = csv_float(summary.get("energy_wh_if_raw_per_amp_20_mean"))
        capacity = csv_float(summary.get("capacity_mah_if_raw_per_amp_20_mean"))
        mean_power_from_energy = energy / (duration / 3600.0) if energy > 0 and duration > 0 else float("nan")
        mean_power_from_vi = current_mean * v_mean if current_mean > 0 and v_mean > 0 else float("nan")
        full_charge_start_drop = full_voltage_v - v_start if full_voltage_v > 0 and v_start > 0 else float("nan")
        start_drop_r = full_charge_start_drop / current_mean if current_mean > 0 else float("nan")
        configured_sag_mean = current_mean * resistance_ohm if current_mean > 0 and resistance_ohm > 0 else float("nan")
        configured_sag_p95 = current_p95 * resistance_ohm if current_p95 > 0 and resistance_ohm > 0 else float("nan")
        scenario_metrics[scenario] = {
            "current_mean_a": current_mean,
            "current_p95_a": current_p95,
            "vbat_start_v": v_start,
            "vbat_mean_v": v_mean,
            "duration_s": duration,
            "energy_wh": energy,
            "capacity_mah": capacity,
        }
        add(
            {
                "row_type": "apdrone_battery_esr_proxy_scenario",
                "scenario": scenario,
                "source_page": SOURCE_PAGE,
                "current_mean_a_raw_per_amp_20": current_mean,
                "current_p95_a_raw_per_amp_20": current_p95,
                "vbat_start_v_mean": v_start,
                "vbat_mean_v_mean": v_mean,
                "duration_s_mean": duration,
                "energy_wh_raw_per_amp_20": energy,
                "capacity_mah_raw_per_amp_20": capacity,
                "mean_power_w_from_energy_duration": mean_power_from_energy,
                "mean_power_w_from_vbat_mean_current_mean": mean_power_from_vi,
                "configured_battery_internal_resistance_ohm": resistance_ohm,
                "configured_sag_v_at_mean_current": configured_sag_mean,
                "configured_sag_v_at_p95_current": configured_sag_p95,
                "full_charge_minus_start_voltage_v": full_charge_start_drop,
                "start_drop_resistance_proxy_ohm": start_drop_r,
                "configured_resistance_over_start_drop_proxy": resistance_ohm / start_drop_r if start_drop_r > 0 else "",
                "note": "Start-drop proxy uses configured full-charge voltage minus logged start voltage, divided by scenario mean current. It is sensitive to initial SOC and sensor calibration.",
            }
        )

    normal = scenario_metrics.get("normal_power", {})
    maximum = scenario_metrics.get("max_power", {})
    delta_i = maximum.get("current_mean_a", float("nan")) - normal.get("current_mean_a", float("nan"))
    delta_v = normal.get("vbat_mean_v", float("nan")) - maximum.get("vbat_mean_v", float("nan"))
    inferred_r = delta_v / delta_i if delta_i > 0 else float("nan")
    add(
        {
            "row_type": "apdrone_battery_esr_proxy_cross_scenario",
            "name": "normal_vs_max_power_mean_voltage_current_delta",
            "source_page": SOURCE_PAGE,
            "normal_power_current_mean_a": normal.get("current_mean_a", ""),
            "max_power_current_mean_a": maximum.get("current_mean_a", ""),
            "delta_current_a": delta_i,
            "normal_power_vbat_mean_v": normal.get("vbat_mean_v", ""),
            "max_power_vbat_mean_v": maximum.get("vbat_mean_v", ""),
            "delta_vbat_mean_v": delta_v,
            "inferred_resistance_proxy_ohm": inferred_r,
            "configured_battery_internal_resistance_ohm": resistance_ohm,
            "configured_over_inferred_proxy": resistance_ohm / inferred_r if inferred_r > 0 else "",
            "configured_sag_delta_v_between_scenarios": delta_i * resistance_ohm if delta_i > 0 and resistance_ohm > 0 else "",
            "observed_mean_voltage_delta_over_configured_sag_delta": delta_v / (delta_i * resistance_ohm) if delta_i > 0 and resistance_ohm > 0 else "",
            "note": "Cross-scenario proxy divides the mean-voltage difference between normal and max-power logs by their mean-current difference. It over-couples SOC, discharge duration, and thermal state, but is a direct APdrone log sanity check for pack resistance magnitude.",
        }
    )
    return rows


def write_csv(path: Path, rows: list[dict[str, str | float | int]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    inventory_rows = fetch_public_file_inventory()
    download_selected_files(inventory_rows)
    download_battery_autonomy_files(inventory_rows)
    download_flight_archive_files(inventory_rows)
    extract_battery_autonomy_archives()
    extract_flight_archives()
    summary_rows = summarize_selected_flight(inventory_rows)
    inertia_rows = summarize_apdrone_inertia(inventory_rows)
    pid_tuning_rows = summarize_pid_tuning(inventory_rows)
    component_spec_rows = summarize_component_specs(inventory_rows)
    battery_autonomy_rows = summarize_battery_autonomy(inventory_rows)
    real_flight_archive_rows = summarize_real_flight_archives(inventory_rows)
    speed_current_bin_rows = summarize_apdrone_open_field_speed_current_bins(inventory_rows)
    flight_vs_model_rows = summarize_apdrone_flight_vs_model(
        summary_rows,
        battery_autonomy_rows,
        real_flight_archive_rows,
    )
    drag_speed_envelope_rows = summarize_apdrone_drag_speed_envelope(
        summary_rows,
        real_flight_archive_rows,
        flight_vs_model_rows,
    )
    preset_source_match_rows = summarize_apdrone_preset_source_match(
        summary_rows,
        inertia_rows,
        component_spec_rows,
    )
    article_performance_rows = summarize_apdrone_article_performance(
        battery_autonomy_rows,
        flight_vs_model_rows,
    )
    battery_esr_proxy_rows = summarize_apdrone_battery_esr_proxy(
        battery_autonomy_rows,
        flight_vs_model_rows,
    )
    write_csv(DATA / "apdrone_mendeley_file_inventory.csv", inventory_rows)
    write_csv(DATA / "apdrone_selected_flight_reference.csv", summary_rows)
    write_csv(DATA / "apdrone_inertia_reference.csv", inertia_rows)
    write_csv(DATA / "apdrone_pid_tuning_reference.csv", pid_tuning_rows)
    write_csv(DATA / "apdrone_component_specs_reference.csv", component_spec_rows)
    write_csv(DATA / "apdrone_battery_autonomy_reference.csv", battery_autonomy_rows)
    write_csv(DATA / "apdrone_flight_archive_reference.csv", real_flight_archive_rows)
    write_csv(DATA / "apdrone_open_field_speed_current_bins_reference.csv", speed_current_bin_rows)
    write_csv(DATA / "apdrone_flight_vs_model_reference.csv", flight_vs_model_rows)
    write_csv(DATA / "apdrone_drag_speed_envelope_reference.csv", drag_speed_envelope_rows)
    write_csv(DATA / "apdrone_preset_source_match_reference.csv", preset_source_match_rows)
    write_csv(DATA / "apdrone_article_performance_reference.csv", article_performance_rows)
    write_csv(DATA / "apdrone_battery_esr_proxy_reference.csv", battery_esr_proxy_rows)
    print("Wrote docs/data/apdrone_mendeley_file_inventory.csv")
    print("Wrote docs/data/apdrone_selected_flight_reference.csv")
    print("Wrote docs/data/apdrone_inertia_reference.csv")
    print("Wrote docs/data/apdrone_pid_tuning_reference.csv")
    print("Wrote docs/data/apdrone_component_specs_reference.csv")
    print("Wrote docs/data/apdrone_battery_autonomy_reference.csv")
    print("Wrote docs/data/apdrone_flight_archive_reference.csv")
    print("Wrote docs/data/apdrone_open_field_speed_current_bins_reference.csv")
    print("Wrote docs/data/apdrone_flight_vs_model_reference.csv")
    print("Wrote docs/data/apdrone_drag_speed_envelope_reference.csv")
    print("Wrote docs/data/apdrone_preset_source_match_reference.csv")
    print("Wrote docs/data/apdrone_article_performance_reference.csv")
    print("Wrote docs/data/apdrone_battery_esr_proxy_reference.csv")


if __name__ == "__main__":
    main()
