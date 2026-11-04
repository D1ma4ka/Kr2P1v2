#!/usr/bin/env python3
"""Read-only ZIP inventory; does not decode, extract, install, or execute APKs."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import zipfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inventory(apk):
    """Return archive facts, not inferred Android metadata or capabilities."""
    apk = Path(apk).resolve(strict=True)
    before = sha256(apk)
    initial_size = apk.stat().st_size
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        names = [item.filename for item in entries]
        counts = Counter(names)
        suspicious = []
        for item in entries:
            normalized = item.filename.replace("\\", "/")
            parts = PurePosixPath(normalized).parts
            if normalized.startswith("/") or ".." in parts or re.match(r"^[A-Za-z]:", normalized):
                suspicious.append(item.filename)
        files = [item for item in entries if not item.is_dir()]
        report = {
            "schema_version": 1,
            "tool": "tools/apk_inventory.py",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "source": {"name": apk.name, "size_bytes": initial_size, "sha256": before},
            "scope": {
                "zip_directory_read": True,
                "entry_contents_decompressed": False,
                "crc_checked": False,
                "signature_verified": False,
                "android_metadata_decoded": False,
                "application_executed": False,
                "original_unchanged_during_inventory": None,
            },
            "summary": {
                "entries": len(entries),
                "file_entries": len(files),
                "declared_uncompressed_bytes": sum(item.file_size for item in files),
                "declared_compressed_bytes": sum(item.compress_size for item in files),
                "android_manifest_present": "AndroidManifest.xml" in counts,
                "dex_files": sorted(name for name in names if re.fullmatch(r"classes(?:[2-9][0-9]*|1[0-9]+)?\.dex", name)),
                "native_library_candidates": sorted(name for name in names if name.startswith("lib/") and name.endswith(".so")),
                "database_filename_candidates": sorted(name for name in names if name.lower().endswith((".db", ".sqlite", ".sqlite3"))),
                "assets_file_entries": sum(item.filename.startswith("assets/") for item in files),
            },
            "warnings": {
                "duplicate_names": sorted(name for name, count in counts.items() if count > 1),
                "unsafe_path_names": sorted(set(suspicious)),
                "encrypted_names": sorted(item.filename for item in files if item.flag_bits & 1),
                "note": "Names and sizes come from untrusted ZIP metadata. No entries were extracted. Database names do not prove database format; ZIP validity does not prove APK validity.",
            },
            "entries": [
                {
                    "name": item.filename,
                    "directory": item.is_dir(),
                    "size_bytes": item.file_size,
                    "compressed_bytes": item.compress_size,
                    "crc32_declared": f"{item.CRC:08x}",
                    "compression_method": item.compress_type,
                }
                for item in entries
            ],
        }
    if sha256(apk) != before or apk.stat().st_size != initial_size:
        raise RuntimeError("Input changed during inventory; discard this result and obtain a stable original")
    report["scope"]["original_unchanged_during_inventory"] = True
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", required=True, type=Path, help="New local JSON file; existing files are never overwritten")
    args = parser.parse_args(argv)
    try:
        source = args.apk.resolve(strict=True)
        target = args.output.resolve()
        if source == target:
            raise ValueError("Output must differ from the original")
        if target.exists():
            raise FileExistsError("Output already exists; choose a new path")
        report = inventory(source)
        # Exclusive creation also rejects an existing symlink or a concurrent writer.
        with args.output.open("x", encoding="utf-8") as output:
            json.dump(report, output, ensure_ascii=False, indent=2)
            output.write("\n")
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, zipfile.LargeZipFile) as error:
        print(f"Inventory failed: {error}", file=sys.stderr)
        return 1
    print(f"Inventory saved to {args.output}; no APK entry was extracted or executed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
