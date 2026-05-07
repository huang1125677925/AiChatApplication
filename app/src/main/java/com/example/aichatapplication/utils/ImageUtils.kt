package com.example.aichatapplication.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ImageUtils {
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            val filename = "ToolResult_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            var imageUri: android.net.Uri? = null
            
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
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
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
