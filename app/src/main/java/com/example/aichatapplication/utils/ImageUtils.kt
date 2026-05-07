package com.example.aichatapplication.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ImageUtils {
    /**
     * Very tall/wide message captures can exceed the device canvas maximum bitmap
     * dimensions, which breaks encoding or truncates output. Scale down uniformly when needed.
     */
    fun scaleDownIfExceedsCanvasLimits(bitmap: Bitmap): Bitmap {
        val limitsCanvas = Canvas()
        val maxW = limitsCanvas.getMaximumBitmapWidth().coerceAtLeast(1)
        val maxH = limitsCanvas.getMaximumBitmapHeight().coerceAtLeast(1)
        if (bitmap.width <= maxW && bitmap.height <= maxH) return bitmap
        val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height)
        val newW = (bitmap.width * scale).toInt().coerceIn(1, maxW)
        val newH = (bitmap.height * scale).toInt().coerceIn(1, maxH)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            val filename = "ToolResult_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            var imageUri: android.net.Uri? = null
            val toSave = scaleDownIfExceedsCanvasLimits(bitmap)
            if (toSave !== bitmap) {
                bitmap.recycle()
            }

            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AiChat")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                imageUri?.let { uri ->
                    fos = resolver.openOutputStream(uri)
                    fos?.use {
                        toSave.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                fos?.close()
            }
        }
    }
}
