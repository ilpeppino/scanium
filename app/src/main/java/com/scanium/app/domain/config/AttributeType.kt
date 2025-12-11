package com.scanium.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Represents the data type of a domain attribute.
 *
 * Used in Domain Pack configuration to specify how attribute values should be handled.
 */
@Serializable
enum class AttributeType {
    /** String value (e.g., "Nike", "Red", "Good") */
    STRING,

    /** Numeric value (e.g., 2020, 15.5) */
    NUMBER,

    /** Enumerated value from a predefined set */
    ENUM,

    /** Boolean true/false value */
    BOOLEAN
}
