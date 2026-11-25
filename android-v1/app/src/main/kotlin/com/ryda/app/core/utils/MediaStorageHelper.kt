package com.ryda.app.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object MediaStorageHelper {

    suspend fun saveImageToInternalStorage(
        context: Context,
        sourceUriStr: String,
        folderName: String,
        fileNamePrefix: String
    ): String? = withContext(Dispatchers.IO) {
        if (sourceUriStr.isBlank()) return@withContext null

        // If already pointing to an internal file path, return as-is
        val filesDirPath = context.filesDir.absolutePath
        if (sourceUriStr.contains(filesDirPath) || sourceUriStr.startsWith("http://") || sourceUriStr.startsWith("https://")) {
            return@withContext sourceUriStr
        }

        try {
            val uri = Uri.parse(sourceUriStr)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext sourceUriStr
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext sourceUriStr
            inputStream.close()

            // Scale image if larger than 1920px to save storage and memory
            val maxDimension = max(originalBitmap.width, originalBitmap.height)
            val scaledBitmap = if (maxDimension > 1920) {
                val scaleFactor = 1920.0f / maxDimension
                val targetW = (originalBitmap.width * scaleFactor).toInt()
                val targetH = (originalBitmap.height * scaleFactor).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            // Create target directory in app's internal private storage
            val targetDir = File(context.filesDir, folderName)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, "${fileNamePrefix}_${UUID.randomUUID().toString().take(8)}.jpg")
            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            return@withContext Uri.fromFile(targetFile).toString()
        } catch (_: Exception) {
            return@withContext sourceUriStr
        }
    }
}
