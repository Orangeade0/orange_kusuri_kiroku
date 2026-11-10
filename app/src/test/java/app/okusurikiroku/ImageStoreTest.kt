package app.okusurikiroku

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.ImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStoreTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val images = ImageStore(app)

    private fun sourcePhoto(width: Int, height: Int): Uri {
        val file = File(app.cacheDir, "source.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun importCopiesAndShrinksLargePhotos() = runBlocking {
        val name = images.import(sourcePhoto(4000, 3000))
        assertNotNull(name)
        val stored = BitmapFactory.decodeFile(images.file(name!!).path)
        assertEquals(1280 to 960, stored.width to stored.height)
        assertNotNull(images.load(name, 512))
    }

    @Test
    fun importReturnsNullForUnreadableFile() = runBlocking {
        val broken = File(app.cacheDir, "broken.jpg").apply { writeText("not an image") }
        assertNull(images.import(Uri.fromFile(broken)))
        assertNull(images.import(Uri.fromFile(File(app.cacheDir, "missing.jpg"))))
    }

    @Test
    fun cleanupRemovesOnlyOldUnreferencedPhotos() = runBlocking {
        val kept = images.import(sourcePhoto(100, 100))!!
        val orphan = images.import(sourcePhoto(100, 100))!!
        val pending = images.import(sourcePhoto(100, 100))!! // 編集中でまだ保存していない写真
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000
        images.file(kept).setLastModified(twoHoursAgo)
        images.file(orphan).setLastModified(twoHoursAgo)

        images.cleanup(referenced = setOf(kept))

        assertTrue(images.file(kept).exists())
        assertFalse(images.file(orphan).exists())
        assertTrue(images.file(pending).exists())
    }
}
