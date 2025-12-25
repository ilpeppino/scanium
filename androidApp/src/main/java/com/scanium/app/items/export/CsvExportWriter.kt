package com.scanium.app.items.export

import android.content.Context
import com.scanium.core.export.ExportPayload
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExportWriter(
    private val serializer: CsvExportSerializer = CsvExportSerializer()
) {
    fun writeToCache(context: Context, payload: ExportPayload): Result<File> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "scanium-export-$timestamp.csv"
        val file = File(context.cacheDir, fileName)
        val csv = serializer.serialize(payload)
        file.writeText(csv, Charsets.UTF_8)
        file
    }
}
