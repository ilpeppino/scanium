package com.scanium.app.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper for saving files to a custom directory using Storage Access Framework (SAF).
 */
object StorageHelper {

    /**
     * Saves an image from an input stream to the specified directory URI.
     *
     * @param context Context
     * @param directoryUri Content URI of the directory (granted via SAF)
     * @param inputStream Input stream of the image data
     * @param mimeType Mime type of the image (e.g., "image/jpeg")
     * @param prefix Filename prefix
     * @return The URI of the saved file, or null if failed
     */
    suspend fun saveToDirectory(
        context: Context,
        directoryUri: Uri,
        inputStream: InputStream,
        mimeType: String,
        prefix: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
            if (dir == null || !dir.isDirectory || !dir.canWrite()) {
                throw IOException("Invalid directory or no write permission: $directoryUri")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "${prefix}_${timestamp}"
            // Note: DocumentFile.createFile uses mimeType and displayName.
            // The extension is usually appended automatically if not present in displayName depending on mimeType,
            // but DocumentFile implementation varies.
            // "image/jpeg" -> filename.jpg
            
            val file = dir.createFile(mimeType, filename) 
                ?: throw IOException("Failed to create file in $directoryUri")

            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            } ?: throw IOException("Failed to open output stream for ${file.uri}")

            return@withContext file.uri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Persists the permission for the selected directory URI so it survives reboots.
     */
    fun takePersistablePermissions(context: Context, uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }
    
    /**
     * Releases the persistable permission.
     */
    fun releasePersistablePermissions(context: Context, uri: Uri) {
         val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Ignore if permission was not tracked
        }
    }
    
    /**
     * Checks if we still have access to the URI.
     */
    fun hasAccess(context: Context, uri: Uri): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(context, uri)
            dir?.isDirectory == true && dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets a display name for the folder URI.
     */
    fun getFolderDisplayName(context: Context, uri: Uri): String {
        return try {
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uri.toString()
        } catch (e: Exception) {
            uri.lastPathSegment ?: uri.toString()
        }
    }
}
