package com.stickertransfer.app.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stickertransfer.app.data.model.StickerPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {

    /**
     * Export a downloaded sticker pack as a ZIP file to the public Downloads folder.
     * Returns the display name of the saved file, or null on failure.
     */
    suspend fun exportPackAsZip(
        context: Context,
        pack: StickerPack
    ): String? = withContext(Dispatchers.IO) {
        val packDir = File(pack.localDirectory)
        if (!packDir.exists()) return@withContext null

        val zipName = "${pack.identifier}.zip"

        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage — use MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, zipName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext null

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ZipOutputStream(os).use { zip ->
                        packDir.walkTopDown()
                            .filter { it.isFile }
                            .forEach { file ->
                                zip.putNextEntry(ZipEntry(file.name))
                                FileInputStream(file).copyTo(zip)
                                zip.closeEntry()
                            }
                    }
                }
                zipName
            } else {
                // Legacy external storage
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                downloadsDir.mkdirs()
                val outFile = File(downloadsDir, zipName)
                ZipOutputStream(outFile.outputStream()).use { zip ->
                    packDir.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            FileInputStream(file).copyTo(zip)
                            zip.closeEntry()
                        }
                }
                zipName
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract a ZIP file to a temp directory and return the extracted folder.
     */
    suspend fun extractZip(context: Context, zipBytes: ByteArray): File? =
        withContext(Dispatchers.IO) {
            try {
                val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}").apply { mkdirs() }
                val zipFile = File(tempDir, "input.zip").apply { writeBytes(zipBytes) }
                val extractDir = File(tempDir, "extracted").apply { mkdirs() }

                java.util.zip.ZipFile(zipFile).use { zf ->
                    zf.entries().asSequence().forEach { entry ->
                        val outFile = File(extractDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            zf.getInputStream(entry).copyTo(outFile.outputStream())
                        }
                    }
                }
                extractDir
            } catch (e: Exception) {
                null
            }
        }
}
