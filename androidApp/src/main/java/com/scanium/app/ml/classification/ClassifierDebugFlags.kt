package com.scanium.app.ml.classification

object ClassifierDebugFlags {
    @Volatile
    var saveCloudCropsEnabled: Boolean = false

    @Volatile
    var lowDataModeEnabled: Boolean = false

    @Volatile
    var analysisFps: Int = 10
}
