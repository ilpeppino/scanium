***REMOVED***!/usr/bin/env python3
"""
Tests for append_brands_bundle.py script.

Run with: pytest test_append_brands_bundle.py -v
"""

import pytest
import json
import tempfile
from pathlib import Path
from unittest.mock import patch, MagicMock
import sys

***REMOVED*** Import the module under test
from append_brands_bundle import (
    normalize_brand,
    dedupe_brands,
    validate_payload,
    merge_brands,
    load_existing_catalog,
)


class TestNormalizeBrand:
    """Test brand normalization (whitespace trimming)."""

    def test_trims_leading_whitespace(self):
        assert normalize_brand("  Apple") == "Apple"

    def test_trims_trailing_whitespace(self):
        assert normalize_brand("Samsung  ") == "Samsung"

    def test_trims_both_sides(self):
        assert normalize_brand("  Sony  ") == "Sony"

    def test_no_trim_needed(self):
        assert normalize_brand("Dell") == "Dell"

    def test_empty_string(self):
        assert normalize_brand("   ") == ""


class TestDedupeBrands:
    """Test brand deduplication with case-insensitive matching."""

    def test_removes_case_insensitive_duplicates(self):
        brands = ["Apple", "apple", "APPLE", "Samsung"]
        result = dedupe_brands(brands)
        ***REMOVED*** Should have 2 items (Apple and Samsung), preserving first casing
        assert len(result) == 2
        assert "Apple" in result
        assert "Samsung" in result

    def test_preserves_first_occurrence_casing(self):
        brands = ["apple", "APPLE", "Apple"]
        result = dedupe_brands(brands)
        assert result[0] == "apple"  ***REMOVED*** First occurrence

    def test_sorts_alphabetically(self):
        brands = ["Sony", "Apple", "Samsung"]
        result = dedupe_brands(brands)
        ***REMOVED*** Sorted case-insensitively
        assert result == ["Apple", "Samsung", "Sony"]

    def test_ignores_empty_strings(self):
        brands = ["Apple", "  ", "Samsung", ""]
        result = dedupe_brands(brands)
        assert len(result) == 2
        assert "Apple" in result
        assert "Samsung" in result

    def test_normalizes_whitespace(self):
        brands = ["  Apple  ", "Samsung"]
        result = dedupe_brands(brands)
        assert result == ["Apple", "Samsung"]

    def test_empty_input(self):
        result = dedupe_brands([])
        assert result == []

    def test_single_item(self):
        result = dedupe_brands(["Apple"])
        assert result == ["Apple"]


class TestValidatePayload:
    """Test payload validation."""

    def test_valid_payload(self):
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", "Dell"],
                "electronics_phone": ["Apple", "Samsung"]
            }
        }
        valid, msg = validate_payload(payload)
        assert valid is True
        assert msg == ""

    def test_missing_brandsBySubtype_key(self):
        payload = {}
        valid, msg = validate_payload(payload)
        assert valid is False
        assert "brandsBySubtype" in msg

    def test_invalid_payload_not_dict(self):
        payload = []
        valid, msg = validate_payload(payload)
        assert valid is False

    def test_brandsBySubtype_not_dict(self):
        payload = {"brandsBySubtype": []}
        valid, msg = validate_payload(payload)
        assert valid is False

    def test_invalid_subtype_id_type(self):
        payload = {
            "brandsBySubtype": {
                123: ["Apple"]  ***REMOVED*** subtype ID must be string
            }
        }
        valid, msg = validate_payload(payload)
        assert valid is False

    def test_brands_not_list(self):
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": "Apple"  ***REMOVED*** must be list
            }
        }
        valid, msg = validate_payload(payload)
        assert valid is False

    def test_brand_not_string(self):
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", 123]  ***REMOVED*** brand must be string
            }
        }
        valid, msg = validate_payload(payload)
        assert valid is False

    def test_empty_brand(self):
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", "   "]  ***REMOVED*** empty after normalization
            }
        }
        valid, msg = validate_payload(payload)
        assert valid is False


class TestMergeBrands:
    """Test merging existing and new brands."""

    def test_merge_with_no_existing(self):
        result = merge_brands([], ["Apple", "Dell"])
        assert result == ["Apple", "Dell"]

    def test_merge_with_no_new(self):
        result = merge_brands(["Apple", "Dell"], [])
        assert result == ["Apple", "Dell"]

    def test_merge_removes_duplicates(self):
        result = merge_brands(["Apple", "Dell"], ["Apple", "Samsung"])
        assert len(result) == 3
        assert "Apple" in result
        assert "Dell" in result
        assert "Samsung" in result

    def test_merge_is_case_insensitive(self):
        result = merge_brands(["Apple"], ["apple", "APPLE", "Samsung"])
        ***REMOVED*** Should dedupe Apple variants and sort
        assert len(result) == 2
        assert "Apple" in result
        assert "Samsung" in result

    def test_merge_sorts_result(self):
        result = merge_brands(["Sony"], ["Apple", "Dell"])
        assert result == ["Apple", "Dell", "Sony"]


class TestLoadExistingCatalog:
    """Test loading existing catalog or creating empty structure."""

    def test_creates_empty_catalog_when_file_missing(self):
        with patch('append_brands_bundle.CATALOG_PATH', Path("/nonexistent/path.json")):
            catalog = load_existing_catalog()
            assert catalog["id"] == "brands_catalog"
            assert catalog["version"] == "1.0.0"
            assert catalog["brandsBySubtype"] == {}

    def test_loads_existing_catalog(self):
        catalog_data = {
            "id": "brands_catalog",
            "version": "1.0.0",
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", "Dell"]
            }
        }

        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(catalog_data, f)
            temp_path = Path(f.name)

        try:
            with patch('append_brands_bundle.CATALOG_PATH', temp_path):
                catalog = load_existing_catalog()
                assert catalog["brandsBySubtype"]["electronics_laptop"] == ["Apple", "Dell"]
        finally:
            temp_path.unlink()


class TestIntegration:
    """Integration tests for full workflow."""

    def test_payload_validation_and_merging(self):
        """Test that valid payload passes validation and can be merged."""
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", "Dell", "Lenovo"],
                "electronics_phone": ["Apple", "Samsung", "Google"]
            }
        }

        valid, msg = validate_payload(payload)
        assert valid is True

        ***REMOVED*** Simulate existing catalog
        existing_catalog = {
            "id": "brands_catalog",
            "version": "1.0.0",
            "brandsBySubtype": {
                "electronics_laptop": ["Apple"]  ***REMOVED*** Already has Apple
            }
        }

        ***REMOVED*** Merge
        new_brands = merge_brands(
            existing_catalog["brandsBySubtype"]["electronics_laptop"],
            payload["brandsBySubtype"]["electronics_laptop"]
        )

        ***REMOVED*** Should have unique brands sorted
        assert "Apple" in new_brands
        assert "Dell" in new_brands
        assert "Lenovo" in new_brands
        assert new_brands == ["Apple", "Dell", "Lenovo"]

    def test_dedup_with_case_variations(self):
        """Test complete deduplication with case variations."""
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["apple", "APPLE", "Apple", "Dell"]
            }
        }

        valid, msg = validate_payload(payload)
        assert valid is True

        result = dedupe_brands(payload["brandsBySubtype"]["electronics_laptop"])
        ***REMOVED*** Should preserve first occurrence (lowercase "apple") and sort
        assert result == ["apple", "Dell"]

    def test_empty_normalized_brands_rejected(self):
        """Test that brands that are empty after normalization are rejected."""
        payload = {
            "brandsBySubtype": {
                "electronics_laptop": ["Apple", "    "]  ***REMOVED*** Whitespace only
            }
        }

        valid, msg = validate_payload(payload)
        assert valid is False
        assert "Empty brand" in msg


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
