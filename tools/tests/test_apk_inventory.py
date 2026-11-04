"""Synthetic ZIP tests only. These fixtures are not Android applications."""

import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile


spec = importlib.util.spec_from_file_location("apk_inventory", Path(__file__).parents[1] / "apk_inventory.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.apk = self.root / "synthetic.zip"
        self.output = self.root / "inventory.json"

    def archive(self, names):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "w") as archive:
                for name in names:
                    archive.writestr(name, b"synthetic test data")

    def invoke(self, output=None):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return module.main([str(self.apk), "--output", str(output or self.output)])

    def test_classifies_candidates_without_claiming_android_decoding(self):
        self.archive(["AndroidManifest.xml", "classes.dex", "classes2.dex", "lib/arm64-v8a/test.so", "assets/catalog.db"])
        before = self.apk.read_bytes()
        # A directory-only scan must never decompress or read entry contents.
        with patch.object(zipfile.ZipFile, "open", side_effect=AssertionError("Entry content read")):
            self.assertEqual(self.invoke(), 0)
        report = json.loads(self.output.read_text())
        self.assertEqual(report["source"]["sha256"], hashlib.sha256(before).hexdigest())
        self.assertEqual(report["summary"]["dex_files"], ["classes.dex", "classes2.dex"])
        self.assertEqual(report["summary"]["database_filename_candidates"], ["assets/catalog.db"])
        self.assertFalse(report["scope"]["signature_verified"])
        self.assertFalse(report["scope"]["android_metadata_decoded"])
        self.assertEqual(self.apk.read_bytes(), before)

    def test_reports_duplicate_and_traversal_names_without_extracting(self):
        self.archive(["../escape", "/absolute", "C:\\escape", "same", "same"])
        self.assertEqual(self.invoke(), 0)
        report = json.loads(self.output.read_text())
        self.assertEqual(report["warnings"]["duplicate_names"], ["same"])
        self.assertEqual(len(report["warnings"]["unsafe_path_names"]), 3)
        self.assertEqual(set(path.name for path in self.root.iterdir()), {"synthetic.zip", "inventory.json"})

    def test_invalid_archive_does_not_create_report(self):
        self.apk.write_bytes(b"not a ZIP or APK")
        self.assertEqual(self.invoke(), 1)
        self.assertFalse(self.output.exists())

    def test_refuses_to_overwrite_original_or_existing_output(self):
        self.archive(["test"])
        before = self.apk.read_bytes()
        self.assertEqual(self.invoke(self.apk), 1)
        self.assertEqual(self.apk.read_bytes(), before)
        self.output.write_text("preserve me")
        self.assertEqual(self.invoke(), 1)
        self.assertEqual(self.output.read_text(), "preserve me")

    def test_rejects_input_changed_during_scan(self):
        self.archive(["test"])
        with patch.object(module, "sha256", side_effect=["before", "after"]):
            self.assertEqual(self.invoke(), 1)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
