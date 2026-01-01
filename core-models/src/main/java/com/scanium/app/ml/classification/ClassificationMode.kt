package com.scanium.app.ml.classification

/**
 * User-selectable classification modes.
 *
 * - ON_DEVICE: fast, offline classification using a bundled model
 * - CLOUD: higher-quality classification via remote API
 */
enum class ClassificationMode(val displayName: String) {
    ON_DEVICE("On-device"),
    CLOUD("Cloud"),
}
