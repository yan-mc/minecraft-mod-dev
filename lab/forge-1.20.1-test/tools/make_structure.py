"""Generate a tiny 5x5x5 GameTest structure with a stone floor at y=0.

The Forge 1.20.1 GameTest framework loads structures from
data/<namespace>/structures/<name>.nbt.  This script writes a minimal
structure template using the vanilla NBT layout (big-endian, gzip-compressed).
"""

from __future__ import annotations

import gzip
import os
import struct

DATA_VERSION = 3465  # 1.20.1


def _str(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + _str(name) + payload


def _int(value: int) -> bytes:
    return struct.pack(">i", value)


def _list(elem_type: int, items: list[bytes]) -> bytes:
    return bytes([elem_type]) + _int(len(items)) + b"".join(items)


def _compound(items: list[bytes]) -> bytes:
    return b"".join(items) + b"\x00"


def build_structure() -> bytes:
    size = _tag(9, "size", _list(3, [_int(5), _int(5), _int(5)]))
    palette = _tag(
        9,
        "palette",
        _list(
            10,
            [
                _compound([_tag(8, "Name", _str("minecraft:air"))]),
                _compound([_tag(8, "Name", _str("minecraft:stone"))]),
            ],
        ),
    )
    blocks = []
    for x in range(5):
        for z in range(5):
            blocks.append(
                _compound(
                    [
                        _tag(9, "pos", _list(3, [_int(x), _int(0), _int(z)])),
                        _tag(3, "state", _int(1)),
                    ]
                )
            )
    blocks_tag = _tag(9, "blocks", _list(10, blocks))
    entities = _tag(9, "entities", _list(10, []))
    data_version = _tag(3, "DataVersion", _int(DATA_VERSION))
    root = _compound([size, palette, blocks_tag, entities, data_version])
    return bytes([10]) + _str("") + root


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    project = os.path.dirname(here)
    out = os.path.join(
        project, "src", "main", "resources", "data", "examplemod", "structures", "empty.nbt"
    )
    os.makedirs(os.path.dirname(out), exist_ok=True)
    payload = build_structure()
    with gzip.open(out, "wb", compresslevel=9) as fh:
        fh.write(payload)
    print(out, len(payload))


if __name__ == "__main__":
    main()
