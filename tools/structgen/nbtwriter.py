"""Minimal writer for Minecraft structure template NBT (gzip, big-endian)."""
import gzip
import struct

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10
DATA_VERSION = 3955  # Minecraft 1.21.1


def _name(name):
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _int_payload(v):
    return struct.pack(">i", v)


def _value(value):
    """(tag type, payload) for a Python value."""
    if isinstance(value, bool):
        raise TypeError("booleans are written as strings in block state properties")
    if isinstance(value, int):
        return TAG_INT, _int_payload(value)
    if isinstance(value, str):
        return TAG_STRING, _name(value)
    if isinstance(value, dict):
        return TAG_COMPOUND, _compound_payload(value)
    if isinstance(value, list):
        if not value:
            return TAG_LIST, bytes([TAG_END]) + _int_payload(0)
        element_type = _value(value[0])[0]
        return TAG_LIST, bytes([element_type]) + _int_payload(len(value)) + b"".join(_value(v)[1] for v in value)
    raise TypeError(type(value))


def _compound_payload(entries):
    out = b""
    for key, value in entries.items():
        tag, payload = _value(value)
        out += bytes([tag]) + _name(key) + payload
    return out + bytes([TAG_END])


def structure(size, blocks):
    """
    size: (x, y, z); blocks: dict {(x, y, z): (name, {prop: value})}.
    Returns gzip-ready uncompressed NBT bytes.
    """
    palette = []
    index = {}
    entries = []
    for pos in sorted(blocks):
        name, props = blocks[pos]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            state = {"Name": name}
            if props:
                state["Properties"] = {k: str(v) for k, v in props.items()}
            palette.append(state)
        entries.append({"pos": list(pos), "state": index[key]})
    root = {
        "DataVersion": DATA_VERSION,
        "size": list(size),
        "palette": palette or [{"Name": "minecraft:air"}],
        "blocks": entries,
        "entities": [],
    }
    return bytes([TAG_COMPOUND]) + _name("") + _compound_payload(root)


def write(path, data):
    # mtime=0 keeps the output byte-identical between runs.
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as fh:
        fh.write(data)
