package com.ryda.app.core.sharing

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareHelper {

    suspend fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        isTransparent: Boolean = false
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "sharing").also { it.mkdirs() }
            cleanupOldCacheFiles(dir)
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                if (isTransparent) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        isTransparent: Boolean = false
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mimeType = if (isTransparent) "image/png" else "image/jpeg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RYDA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    if (isTransparent) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "RYDA"
                ).also { it.mkdirs() }
                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    if (isTransparent) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves raw SVG XML to the cache sharing directory without any rasterization or compression.
     */
    suspend fun saveSvgToCache(
        context: Context,
        svgContent: String,
        filename: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "sharing").also { it.mkdirs() }
            cleanupOldCacheFiles(dir)
            val file = File(dir, filename)
            file.writeText(svgContent, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves SVG file to the device Downloads/RYDA directory via MediaStore on Android Q+,
     * or to the public Downloads/RYDA directory on older Android versions.
     */
    suspend fun saveSvgToDownloads(
        context: Context,
        svgContent: String,
        filename: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RYDA")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(svgContent.toByteArray(Charsets.UTF_8))
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "RYDA"
                ).also { it.mkdirs() }
                val file = File(dir, filename)
                file.writeText(svgContent, Charsets.UTF_8)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareSvg(
        context: Context,
        svgUri: Uri,
        text: String = "Shared from RYDA \uD83C\uDFCD\uFE0F"
    ) {
        shareImage(context, svgUri, mimeType = "image/svg+xml", text = text)
    }

    fun shareToInstagramStory(context: Context, stickerUri: Uri) {
        val instagramPackage = "com.instagram.android"
        if (hasInstagramInstalled(context)) {
            val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(stickerUri, "image/png")
                clipData = ClipData.newRawUri("", stickerUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                `package` = instagramPackage
                putExtra("source_application", context.packageName)
            }
            context.grantUriPermission(instagramPackage, stickerUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Fallback to general share
        shareImage(context, stickerUri, "image/png")
    }

    fun shareImage(
        context: Context,
        imageUri: Uri,
        mimeType: String = "image/jpeg",
        text: String = "Shared from RYDA \uD83C\uDFCD\uFE0F"
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newRawUri("", imageUri)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ride via")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareMultipleImages(context: Context, uris: List<Uri>, text: String = "") {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (uris.isNotEmpty()) {
                val clipData = ClipData.newRawUri("", uris[0])
                for (i in 1 until uris.size) {
                    clipData.addItem(ClipData.Item(uris[i]))
                }
                this.clipData = clipData
            }
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ride cards via")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun hasInstagramInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    "com.instagram.android",
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("com.instagram.android", 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasMediaReadPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Purges cached files in the sharing cache folder older than 24 hours.
     */
    private fun cleanupOldCacheFiles(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            val maxAgeMs = 24 * 60 * 60 * 1000L
            val now = System.currentTimeMillis()
            for (f in files) {
                if (now - f.lastModified() > maxAgeMs) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
