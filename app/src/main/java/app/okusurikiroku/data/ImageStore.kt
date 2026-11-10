package app.okusurikiroku.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * お薬の写真。選んだ・撮った写真は縮小してアプリ内のフォルダにコピーし、ファイル名で参照する。
 */
class ImageStore(private val context: Context) {
    private val directory: File get() = File(context.filesDir, "med_images").apply { mkdirs() }
    private val cache = LruCache<String, Bitmap>(24)

    fun file(name: String): File = File(directory, name)

    /** カメラアプリに渡す一時ファイルと、その content:// URI。 */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")
        file.delete()
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** [uri] の画像を取り込み、保存したファイル名を返す。読み込めなければ null。 */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decode(uri) ?: return@withContext null
            val name = "${UUID.randomUUID()}.jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_STORED_SIZE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        // カメラの写真は向きが EXIF にだけ入っていることがあるので、ここで回転しておく
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide > MAX_STORED_SIZE) {
            val scale = MAX_STORED_SIZE.toFloat() / longSide
            matrix.postScale(scale, scale)
        }
        return if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
    }

    /** 表示用に読み込む。長辺がおよそ [maxSize] ピクセルになるよう間引く。 */
    suspend fun load(name: String, maxSize: Int): Bitmap? {
        val key = "$name@$maxSize"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val path = file(name).path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            }
            BitmapFactory.decodeFile(path, options)?.also { cache.put(key, it) }
        }
    }

    /**
     * どのお薬からも参照されていない写真を消す。
     * 編集画面で選んだだけの未保存の写真を消さないよう、新しいファイルは残す。
     */
    suspend fun cleanup(referenced: Set<String>, minAgeMillis: Long = 60 * 60 * 1000L) =
        withContext(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - minAgeMillis
            directory.listFiles().orEmpty()
                .filter { it.name !in referenced && it.lastModified() <= threshold }
                .forEach { it.delete() }
        }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }

    companion object {
        private const val MAX_STORED_SIZE = 1280
    }
}
