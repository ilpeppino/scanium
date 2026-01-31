package com.scanium.app.catalog.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogBrandsResponse(
    val subtype: String,
    val brands: List<String> = emptyList(),
)

@Serializable
data class CatalogModelsResponse(
    val subtype: String,
    val brand: String,
    val models: List<CatalogModel> = emptyList(),
)

@Serializable
data class CatalogModel(
    @SerialName("label")
    val modelLabel: String,
    @SerialName("id")
    val modelQid: String? = null,
    val aliases: List<String>? = null,
    val source: String? = null,
)
