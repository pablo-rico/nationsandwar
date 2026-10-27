"""Nations & War notice icons: one 16x16 sprite per name at textures/gui/notice/<name>.png (the names NoticePayload and
client.notice use). Reuses the GUI icons where they fit. Run: python3 tools/artgen/noticeart.py"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png
from guiart import ICONS as GUI, PAL as GUI_PAL, rgb

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "nationsandwar", "textures", "gui", "notice")

PAL = dict(GUI_PAL, x=rgb("7a1c20"))

def recolor(art, swap):
    return ["".join(swap.get(ch, ch) for ch in line) for line in art]

NEW = {
    "siege": [
        ".......kk.......",
        "......kGGk......",
        ".kkkkkkddkkkkkk.",
        ".knnnnnnnnnnnnk.",
        ".kkkkkkkkkkkkkk.",
        "..kRrrrrrrrrRk..",
        "..kRrrrGGrrrRk..",
        "..kRrrGkkGrrRk..",
        "..kRrrGkkGrrRk..",
        "..kRrrrGGrrrRk..",
        "..kRrrrrrrrrRk..",
        "..kRrrrrrrrrRk..",
        "..kRrrkkkkrrRk..",
        "..kRrk.nn.krRk..",
        "..kRk..nn..kRk..",
        "..kk...kk...kk..",
    ],
    "shield": [
        "................",
        ".kkkkkkkkkkkkkk.",
        ".kSSSSSSSSSSSSk.",
        ".kSBbbbGGbbbbSk.",
        ".kSBbbbGGbbbbSk.",
        ".kSBbbbGGbbbbSk.",
        ".kSGGGGGGGGGGSk.",
        ".kSddddGGddddSk.",
        ".kSBbbbGGbbbbSk.",
        "..kSbbbGGbbbSk..",
        "..kSbbbGGbbbSk..",
        "...kSbbGGbbSk...",
        "....kSbGGbSk....",
        ".....kSSSSk.....",
        "......kkkk......",
        "................",
    ],
    "skull": [
        "................",
        "....kkkkkkkk....",
        "...kwwwwwwwwk...",
        "..kwwwwwwwwwwk..",
        "..kwwwwwwwwwSk..",
        "..kwkkkwwkkkSk..",
        "..kwkkkwwkkkSk..",
        "..kwkkkwwkkkSk..",
        "..kwwwwkkwwwSk..",
        "...kwwwkkwwSk...",
        "....kwwwwwSk....",
        "....kwkwkwkk....",
        "....kwkwkwSk....",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "pvp": [
        "................",
        "..kkkk....kkkk..",
        ".kRRrrk..krrrrk.",
        "kRwRrrrkkrrrrrxk",
        "kRRrrrrkrrrrrrxk",
        "kRrrrrrrkrrrrrxk",
        "kRrrrrrkrrrrrrxk",
        ".kRrrrrrkrrrrxk.",
        ".krrrrrkrrrrrxk.",
        "..krrrrrkrrrxk..",
        "...krrrkrrrxk...",
        "....krrrkrxk....",
        ".....krrkxk.....",
        "......krxk......",
        ".......kk.......",
        "................",
    ],
    "wild": [
        ".......kk.......",
        "......kEEk......",
        ".....kEEeek.....",
        "....kEEeeeek....",
        ".....kkEekk.....",
        "....kEEeeeek....",
        "...kEEeeeeeek...",
        "..kkkkEeeekkkk..",
        "....kEEeeeek....",
        "...kEEeeeeeek...",
        "..kEEeeeeeeeek..",
        ".kkkkkkkkkkkkkk.",
        "......knnk......",
        "......knNk......",
        "....kkknnkkk....",
        "................",
    ],
}

SPRITES = {
    "war": GUI["war"], "enemy": GUI["war"], "crown": GUI["crown"], "scroll": GUI["scroll"], "town": GUI["town"],
    "home": recolor(GUI["town"], {"R": "G", "r": "g"}), "province": GUI["provinces"],
    "flag": recolor(GUI["provinces"], {"e": "r", "E": "R"}), "nation": GUI["nation"],
    "handshake": GUI["diplomacy"], "ally": GUI["diplomacy"], **NEW,
}

def main():
    os.makedirs(OUT, exist_ok=True)
    for name, art in SPRITES.items():
        assert len(art) == 16, name
        rows = [[PAL[ch] for ch in (line + "." * 16)[:16]] for line in art]
        write_png(os.path.join(OUT, name + ".png"), rows)
    print("wrote", len(SPRITES), "icons to", OUT)

if __name__ == "__main__":
    main()
