#!/usr/bin/env python3
"""Writes empty structure templates used as GameTest arenas.

GameTests build everything they need in code, so the templates only reserve space.
Usage:  python3 tools/structgen/empty_template.py
"""
import pathlib

import nbtwriter

OUT = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/data/supernaturalcraft/structure/gametest"

# name -> (x, y, z)
TEMPLATES = {
    "empty_5x5x5": (5, 5, 5),
    "empty_11x6x11": (11, 6, 11),
    "empty_48x24x48": (48, 24, 48),
    "empty_64x48x64": (64, 48, 64),
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, size in TEMPLATES.items():
        path = OUT / f"{name}.nbt"
        nbtwriter.write(path, nbtwriter.structure(size, {}))
        print(f"wrote {path.name}")


if __name__ == "__main__":
    main()
