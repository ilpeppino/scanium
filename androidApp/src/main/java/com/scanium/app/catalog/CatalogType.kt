package com.scanium.app.catalog

enum class CatalogType(
    val assetPath: String,
) {
    BRANDS("catalog/brands.json"),
    PRODUCT_TYPES("catalog/product_types.json"),
}
