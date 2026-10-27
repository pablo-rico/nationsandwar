"""The shared palette. One set of ramps across every texture is what makes generated art read
as one coherent style. Ramps run dark -> light; index 0 doubles as the outline."""

from pixelkit import Ramp, hexc

CLEAR = (0, 0, 0, 0)
OUTLINE = hexc("#14100f")

# --- materials --------------------------------------------------------------------------
STONE = Ramp("#3a3a3e", "#5b5b60", "#6e6e73", "#7f7f84", "#929297", "#a5a5aa")
DEEPSLATE = Ramp("#1e1e24", "#2e2e35", "#3a3a42", "#47474f", "#55555d", "#63636b")
NETHERRACK = Ramp("#2e0b0b", "#4a1212", "#611a19", "#742322", "#8a2d2b", "#9d3a36")
SALT = Ramp("#7d7480", "#b3aab4", "#d6cfd6", "#ece6ea", "#f8f4f6", "#ffffff")
ROSE_SALT = Ramp("#7a4f57", "#b07f86", "#d5a5aa", "#ebc6c6", "#f7e0dd", "#fff3f0")
SULFUR = Ramp("#5e4a10", "#8f7114", "#c49b1c", "#e2c235", "#f2df62", "#fff6a6")
CHALK = Ramp("#8f8a80", "#b8b2a6", "#d4cfc4", "#e6e2d8", "#f3f0e8", "#fffdf6")
BLOOD = Ramp("#1f0204", "#3d0509", "#5c0a10", "#7e1218", "#a01d22", "#c4352f")
HELLFIRE = Ramp("#3b0602", "#7a1404", "#c02d06", "#ec5a0c", "#ff9a26", "#ffd96a")
GRACE = Ramp("#5a4a1e", "#9c8436", "#cdb257", "#ecd683", "#fbedb4", "#ffffff")
CAGE = Ramp("#0e1a33", "#1d3460", "#2f5591", "#4f82c0", "#86b6e3", "#cfe9ff")
FROST = Ramp("#1d3a4f", "#30607d", "#4f8cab", "#7cb8d1", "#b2dceb", "#effbff")
ASH = Ramp("#141214", "#262224", "#3a3436", "#524a4a", "#6d6462", "#8c827d")
STEEL = Ramp("#2a2d33", "#4a4f58", "#6c727c", "#9097a1", "#b7bdc5", "#e4e8ec")
SILVER = Ramp("#3a3f48", "#626a76", "#8c95a1", "#b5bdc7", "#d9dfe6", "#ffffff")
GOLD = Ramp("#4b3108", "#7d5410", "#b07d18", "#d9a92b", "#f2d257", "#fff2a8")
LEATHER = Ramp("#1e120b", "#352015", "#4c2f1f", "#65412b", "#7e5638", "#986c48")
WOOD = Ramp("#2a1a0e", "#432b17", "#5c3d22", "#76512e", "#8f673d", "#a97e4f")
PARCHMENT = Ramp("#5d4a2e", "#8a7148", "#b39a6a", "#d3bd8c", "#e9d8ac", "#f7ecc9")
GLASS = Ramp(hexc("#3d4a52", 255), hexc("#8fa7b3", 200), hexc("#bcd4de", 150),
             hexc("#dcebf1", 120), hexc("#eef8fb", 170), hexc("#ffffff", 230))
WATER = Ramp("#0e2a5c", "#1b4690", "#2d68c0", "#4b8de0", "#7db4f2", "#c4e0ff")
SKIN = Ramp("#3b2219", "#6b4030", "#93604a", "#b78467", "#d3a588", "#e9c7ab")
COAT = Ramp("#0c0c10", "#17171d", "#222229", "#2e2e37", "#3b3b46", "#4a4a57")
DENIM = Ramp("#0f1622", "#1b2638", "#283850", "#374b68", "#4a6283", "#62799b")
ROBE = Ramp("#14060a", "#260b12", "#3b111b", "#521826", "#6a2232", "#842f40")
EMBER_EYE = hexc("#ff3b1f")
VOID = hexc("#050505")
