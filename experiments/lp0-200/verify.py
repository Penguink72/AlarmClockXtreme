#!/usr/bin/env python3
"""LP0-200: deterministic, offline stdlib-only source and language-pack checks."""
from __future__ import annotations
import copy
import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
EXPECTED = {"alarm": 50, "snooze": 30, "music": 45, "history": 20, "settings": 40, "diagnostics": 15}
LOCALE_XML = {
    "en": ROOT / "app/src/main/res/values/strings.xml",
    "zh-TW": ROOT / "app/src/main/res/values-b+zh+Hant+TW/strings.xml",
    "ja": ROOT / "app/src/main/res/values-ja/strings.xml",
}
FORMAT = "acx-language-pack-probe/1"
CATALOG = json.loads((HERE / "catalog.json").read_text(encoding="utf-8"))
PACKS = {name: json.loads((HERE / "packs" / (name + ".json")).read_text(encoding="utf-8")) for name in LOCALE_XML}
ENTRY_MAP = {entry["key"]: entry for entry in CATALOG["entries"]}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAtT%]")

def placeholders(text: str) -> list[str]:
    return sorted(p for p in PLACEHOLDER.findall(text) if p != "%%")

def xml_strings(path: Path) -> dict[str, str]:
    output = {}
    for node in ET.parse(path).getroot().findall("string"):
        if node.get("translatable") == "false" or len(node):
            continue
        key = node.get("name")
        if key:
            output[key] = node.text or ""
    return output

def verify_import(pack: object) -> int:
    if not isinstance(pack, dict):
        raise ValueError("pack must be an object")
    if pack.get("format") != FORMAT or pack.get("catalogRevision") != CATALOG["revision"]:
        raise ValueError("unsupported format or catalog revision")
    locale = pack.get("locale")
    if not isinstance(locale, str) or not re.fullmatch(r"[a-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}", locale) or len(locale) > 30:
        raise ValueError("invalid locale tag")
    translations = pack.get("translations")
    if not isinstance(translations, dict) or len(translations) > 200:
        raise ValueError("invalid translations")
    translated = 0
    for key, value in translations.items():
        if key not in ENTRY_MAP:
            raise ValueError("unknown key: " + key)
        if not isinstance(value, str) or len(value) > 4096:
            raise ValueError("invalid translation: " + key)
        if not value.strip():
            continue
        if placeholders(value) != ENTRY_MAP[key]["placeholders"]:
            raise ValueError("placeholder mismatch: " + key)
        translated += 1
    return translated

class LP0ProbeChecks(unittest.TestCase):
    def test_200_distinct_keys_and_quotas(self):
        self.assertEqual(CATALOG["count"], 200)
        self.assertEqual(len(ENTRY_MAP), 200)
        self.assertEqual(len(CATALOG["entries"]), 200)
        self.assertEqual(CATALOG["format"], "acx-language-catalog-probe/1")
        self.assertEqual(
            {section: sum(e["section"] == section for e in CATALOG["entries"]) for section in EXPECTED},
            EXPECTED,
        )
    def test_exact_real_english_sources_and_placeholders(self):
        originals = xml_strings(LOCALE_XML["en"])
        for entry in CATALOG["entries"]:
            key = entry["key"]
            with self.subTest(key=key):
                self.assertIn(key, originals)
                self.assertEqual(entry["source"], originals[key])
                self.assertEqual(entry["placeholders"], placeholders(originals[key]))
    def test_three_real_language_packs(self):
        expected_counts = {"en": 200, "zh-TW": 155, "ja": 155}
        for locale, pack in PACKS.items():
            with self.subTest(locale=locale):
                self.assertEqual(verify_import(pack), expected_counts[locale])
                originals = xml_strings(LOCALE_XML[locale])
                for key, value in pack["translations"].items():
                    self.assertEqual(value, originals[key], (locale, key))
                if locale == "en":
                    self.assertEqual(set(pack["translations"]), set(ENTRY_MAP))
                else:
                    self.assertEqual(len(set(ENTRY_MAP) - set(pack["translations"])), 45)
    def test_fallback_is_explicit_and_not_fake_translation(self):
        for locale in ("zh-TW", "ja"):
            for key, entry in ENTRY_MAP.items():
                actual = PACKS[locale]["translations"].get(key, entry["source"])
                self.assertTrue(actual)
                if key not in PACKS[locale]["translations"]:
                    self.assertEqual(actual, PACKS["en"]["translations"][key])
    def test_negative_validation_cases(self):
        valid = copy.deepcopy(PACKS["zh-TW"])
        self.assertEqual(verify_import(valid), 155)
        broken = copy.deepcopy(valid)
        broken["catalogRevision"] = "not-the-same-catalog"
        with self.assertRaises(ValueError):
            verify_import(broken)
        broken = copy.deepcopy(valid)
        broken["translations"]["__unknown_key__"] = "bad"
        with self.assertRaises(ValueError):
            verify_import(broken)
        source_with_args = next(e for e in CATALOG["entries"] if e["placeholders"])
        broken = copy.deepcopy(valid)
        broken["translations"][source_with_args["key"]] = "No parameters remain"
        with self.assertRaises(ValueError):
            verify_import(broken)
        minimal = {**valid, "locale": "ko-KR", "translations": {}}
        self.assertEqual(verify_import(minimal), 0)
    def test_offline_embedded_catalog_matches_json(self):
        s = (HERE / "web/catalog-data.js").read_text(encoding="utf-8").strip()
        self.assertTrue(s.startswith("window.ACX_LP0_DATA = "))
        self.assertTrue(s.endswith(";"))
        embedded = json.loads(s[len("window.ACX_LP0_DATA = "):-1])
        self.assertEqual(embedded["catalog"], CATALOG)
        self.assertEqual(embedded["packs"], PACKS)

if __name__ == "__main__":
    unittest.main(verbosity=2)
