#!/usr/bin/env python3

import ast
import re
import struct
import sys
from pathlib import Path


def decode_quoted(value: str) -> str:
    return ast.literal_eval(value.strip())


def read_catalog(path: Path) -> dict[str, str]:
    catalog: dict[str, str] = {}
    entry: dict[str, object] = {}
    active_field: tuple[str, int | None] | None = None
    fuzzy = False

    def finish_entry() -> None:
        nonlocal entry, active_field, fuzzy
        msgid = entry.get("msgid")
        if isinstance(msgid, str) and not fuzzy:
            context = entry.get("msgctxt")
            original = f"{context}\x04{msgid}" if isinstance(context, str) else msgid
            plural = entry.get("msgid_plural")
            if isinstance(plural, str):
                original += f"\x00{plural}"
                translations = entry.get("msgstr_plural", {})
                if isinstance(translations, dict):
                    translated = "\x00".join(
                        str(translations[index]) for index in sorted(translations)
                    )
                else:
                    translated = ""
            else:
                translated = str(entry.get("msgstr", ""))
            if translated or msgid == "":
                catalog[original] = translated
        entry = {}
        active_field = None
        fuzzy = False

    for raw_line in path.read_text(encoding="utf-8").splitlines() + [""]:
        line = raw_line.strip()
        if not line:
            finish_entry()
            continue
        if line.startswith("#~"):
            continue
        if line.startswith("#,"):
            fuzzy = fuzzy or "fuzzy" in line
            continue
        if line.startswith("#"):
            continue

        match = re.match(r"msgstr\[(\d+)]\s+(.*)", line)
        if match:
            index = int(match.group(1))
            translations = entry.setdefault("msgstr_plural", {})
            assert isinstance(translations, dict)
            translations[index] = decode_quoted(match.group(2))
            active_field = ("msgstr_plural", index)
            continue

        for field in ("msgctxt", "msgid_plural", "msgid", "msgstr"):
            prefix = f"{field} "
            if line.startswith(prefix):
                entry[field] = decode_quoted(line[len(prefix):])
                active_field = (field, None)
                break
        else:
            if line.startswith('"') and active_field is not None:
                value = decode_quoted(line)
                field, index = active_field
                if field == "msgstr_plural":
                    translations = entry[field]
                    assert isinstance(translations, dict) and index is not None
                    translations[index] = str(translations[index]) + value
                else:
                    entry[field] = str(entry.get(field, "")) + value
            else:
                raise ValueError(f"Unsupported PO line: {raw_line}")

    return catalog


def write_mo(catalog: dict[str, str], output: Path) -> None:
    messages = sorted(
        (original.encode("utf-8"), translated.encode("utf-8"))
        for original, translated in catalog.items()
    )
    count = len(messages)
    originals = b"".join(value + b"\0" for value, _ in messages)
    translations = b"".join(value + b"\0" for _, value in messages)

    original_table_offset = 28
    translation_table_offset = original_table_offset + count * 8
    original_strings_offset = translation_table_offset + count * 8
    translation_strings_offset = original_strings_offset + len(originals)

    original_table = bytearray()
    translation_table = bytearray()
    offset = 0
    for original, _ in messages:
        original_table.extend(struct.pack("<II", len(original), original_strings_offset + offset))
        offset += len(original) + 1
    offset = 0
    for _, translated in messages:
        translation_table.extend(struct.pack("<II", len(translated), translation_strings_offset + offset))
        offset += len(translated) + 1

    header = struct.pack(
        "<7I",
        0x950412DE,
        0,
        count,
        original_table_offset,
        translation_table_offset,
        0,
        0,
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(header + original_table + translation_table + originals + translations)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(f"Usage: {sys.argv[0]} INPUT.po OUTPUT.mo")
    write_mo(read_catalog(Path(sys.argv[1])), Path(sys.argv[2]))


if __name__ == "__main__":
    main()
