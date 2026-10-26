package com.family.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File

/**
 * Ảnh gia đình: chép ảnh vào thư mục "TVHome" ở một trong các nơi sau
 *  - Bộ nhớ trong TV:  /sdcard/Pictures/TVHome
 *  - USB cắm vào TV:   <USB>/TVHome
 */
object Photos {
    private val exts = setOf("jpg", "jpeg", "png", "webp")

    fun folders(): List<File> {
        val list = mutableListOf<File>()
        list += File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TVHome")
        File("/storage").listFiles()?.forEach { vol ->
            if (vol.name != "emulated" && vol.name != "self") list += File(vol, "TVHome")
        }
        return list
    }

    fun list(): List<File> = folders().flatMap { dir ->
        dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in exts } ?: emptyList()
    }.sortedBy { it.name }

    /** Giải mã ảnh thu nhỏ vừa màn hình để không tốn RAM. */
    fun decode(file: File, targetW: Int, targetH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        var sample = 1
        while (opts.outWidth / sample > targetW * 1.5 || opts.outHeight / sample > targetH * 1.5) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun ensureFolder(ctx: Context) {
        try { folders().first().mkdirs() } catch (e: Exception) { }
    }
}
