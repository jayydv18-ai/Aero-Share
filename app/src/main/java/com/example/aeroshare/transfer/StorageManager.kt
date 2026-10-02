package com.example.aeroshare.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.aeroshare.data.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class StorageManager(private val context: Context) {

    suspend fun getFileInfoFromUri(uri: Uri): TransferItem = withContext(Dispatchers.IO) {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // If size is still 0 (e.g., custom provider), attempt stream measurement
        if (size <= 0L) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    size = it.length
                }
            }
        }

        TransferItem(
            id = UUID.randomUUID().toString(),
            fileName = sanitizeFilename(name),
            fileSize = size.coerceAtLeast(0L),
            mimeType = mimeType,
            uriString = uri.toString()
        )
    }

    fun openInputStream(uriString: String): InputStream? {
        val uri = Uri.parse(uriString)
        return runCatching {
            context.contentResolver.openInputStream(uri)
        }.getOrNull()
    }

    fun createPartialFile(fileName: String): File {
        val cleanName = sanitizeFilename(fileName)
        val tempDir = File(context.cacheDir, "aeroshare_partials")
        if (!tempDir.exists()) tempDir.mkdirs()
        return File(tempDir, "${System.currentTimeMillis()}_${cleanName}.partial")
    }

    suspend fun finalizeReceivedFile(
        partialFile: File,
        originalFileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFilename(originalFileName)
        val extension = getExtensionFromFilename(cleanName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AeroShare")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = context.contentResolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry for $cleanName")

            try {
                context.contentResolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(partialFile).use { input ->
                        copyStreamBuffered(input, out)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, contentValues, null, null)
                partialFile.delete()
                itemUri.toString()
            } catch (e: Exception) {
                context.contentResolver.delete(itemUri, null, null)
                throw e
            }
        } else {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AeroShare"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            var targetFile = File(downloadDir, cleanName)
            var counter = 1
            val baseName = cleanName.substringBeforeLast(".")
            while (targetFile.exists()) {
                val nextName = if (extension.isNotEmpty()) "$baseName ($counter).$extension" else "$baseName ($counter)"
                targetFile = File(downloadDir, nextName)
                counter++
            }

            FileInputStream(partialFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    copyStreamBuffered(input, output)
                }
            }
            partialFile.delete()
            targetFile.absolutePath
        }
    }

    private fun copyStreamBuffered(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024) // 64 KB buffer for streaming large files
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            .ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    private fun getExtensionFromFilename(name: String): String {
        return name.substringAfterLast('.', "")
    }
}
