package com.scanium.shared.core.models.listing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializes draft field maps to deterministic JSON for persistence.
 */
object DraftFieldsSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun toJson(fields: Map<DraftFieldKey, DraftField<String>>): String {
        if (fields.isEmpty()) return "{}"
        val sorted = fields.entries.sortedBy { it.key.wireValue }
        val obj =
            buildMap<String, JsonObject> {
                sorted.forEach { (key, field) ->
                    put(
                        key.wireValue,
                        JsonObject(
                            mapOf(
                                "value" to (field.value?.let { JsonPrimitive(it) } ?: JsonNull),
                                "confidence" to JsonPrimitive(field.confidence),
                                "source" to JsonPrimitive(field.source.name),
                            ),
                        ),
                    )
                }
            }
        return JsonObject(obj).toString()
    }

    fun fromJson(value: String?): Map<DraftFieldKey, DraftField<String>> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching { json.parseToJsonElement(value).jsonObject }
            .getOrElse { return emptyMap() }
            .mapNotNull { (key, element) ->
                val fieldKey = DraftFieldKey.fromWireValue(key) ?: return@mapNotNull null
                val obj = element.jsonObject
                val storedValue = obj["value"]?.jsonPrimitive?.contentOrNull
                val confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f
                val source =
                    obj["source"]?.jsonPrimitive?.contentOrNull
                        ?.let { runCatching { DraftProvenance.valueOf(it) }.getOrNull() }
                        ?: DraftProvenance.UNKNOWN

                fieldKey to
                    DraftField(
                        value = storedValue,
                        confidence = confidence,
                        source = source,
                    )
            }
            .toMap()
    }
}
