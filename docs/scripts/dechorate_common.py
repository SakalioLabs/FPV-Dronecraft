#!/usr/bin/env python3
"""Lightweight shared constants for dEchorate geometry analyses."""

from __future__ import annotations

import numpy as np


FS = 48000
SPEED_OF_SOUND = 346.98
ROOM_SIZE = np.array([5.705, 5.965, 2.355], dtype=float)
WALL_CODE = {
    "d": "direct",
    "f": "floor",
    "c": "ceiling",
    "w": "west",
    "s": "south",
    "e": "east",
    "n": "north",
}
