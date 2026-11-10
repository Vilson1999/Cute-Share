package com.example

import com.example.data.P2PTransferManager
import com.example.data.ThumbnailDiskCache
import com.example.model.CartoonAvatars
import com.example.model.FileCategory
import com.example.model.formatBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExampleUnitTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("500 B", formatBytes(500L))
        assertEquals("10 KB", formatBytes(10240L))
        assertEquals("1.5 MB", formatBytes(1572864L))
        assertEquals("1.0 GB", formatBytes(1073741824L))
    }

    @Test
    fun testCartoonAvatarsList() {
        assertEquals(22, CartoonAvatars.list.size)
        val first = CartoonAvatars.getAvatar(0)
        assertEquals("Cosmic Bunny", first.name)
        val cycled = CartoonAvatars.getAvatar(22)
        assertEquals(first.name, cycled.name)
    }

    @Test
    fun testPortableAvatarResolutionAcrossBuilds() {
        val target = CartoonAvatars.getAvatar(5)
        assertEquals(5, CartoonAvatars.avatarIndexOf(target.drawableRes))

        // Index hint takes precedence even if remote build's generated R.drawable integer differs
        val resolvedFromIndex = CartoonAvatars.resolveAvatarRes(
            rawValue = "999999999",
            indexHint = "5",
            fallbackSeed = "Alice"
        )
        assertEquals(target.drawableRes, resolvedFromIndex)

        // Unknown IDs without index hint deterministically resolve to a valid bundled avatar
        val fallbackResolved = CartoonAvatars.resolveAvatarRes(
            rawValue = "-123456",
            indexHint = null,
            fallbackSeed = "Bob"
        )
        assertTrue(CartoonAvatars.list.any { it.drawableRes == fallbackResolved })
    }

    @Test
    fun testDownloadFolderAndFilenameSanitization() {
        assertEquals("Cute Share", P2PTransferManager.DOWNLOAD_SUBFOLDER)
        assertEquals(
            "My_App_Pro_v1.apk",
            P2PTransferManager.sanitizeDownloadFileName("folder/My:App*Pro?v1.apk", timestamp = 123L)
        )
        assertEquals(
            "CuteShare_999",
            P2PTransferManager.sanitizeDownloadFileName("   ...  ", timestamp = 999L)
        )
        assertEquals(
            "photo.jpg",
            P2PTransferManager.sanitizeDownloadFileName(".photo.jpg", timestamp = 123L)
        )
    }

    @Test
    fun testFileCategories() {
        val categories = FileCategory.entries.map { it.title }
        assertTrue(categories.contains("Image"))
        assertTrue(categories.contains("Video"))
        assertTrue(categories.contains("Audio"))
        assertTrue(categories.contains("App"))
        assertTrue(categories.contains("Documents"))
        assertTrue(categories.contains("Archives"))
        assertTrue(categories.contains("Storage"))
    }

    @Test
    fun testThumbnailDiskCacheLowQualityDefaultsAnd10MbLimit() {
        assertEquals(10L * 1024L * 1024L, ThumbnailDiskCache.MAX_CACHE_BYTES)
        assertEquals(35, ThumbnailDiskCache.LOW_QUALITY_PERCENT)
        assertEquals(128, ThumbnailDiskCache.MAX_IMAGE_THUMB_PX)
        assertEquals(72, ThumbnailDiskCache.MAX_APP_ICON_PX)
    }

    @Test
    fun testThumbnailDiskCacheEnforces10MbCapWithLruEviction() {
        val cache = ThumbnailDiskCache(tempFolder.newFolder("thumb_cache"))
        val twoMb = 2 * 1024 * 1024
        val chunk1 = ByteArray(twoMb) { 1 }
        val chunk2 = ByteArray(twoMb) { 2 }
        val chunk3 = ByteArray(twoMb) { 3 }
        val chunk4 = ByteArray(twoMb) { 4 }
        val chunk5 = ByteArray(twoMb) { 5 }
        val chunk6 = ByteArray(twoMb) { 6 }

        assertFalse(cache.putBytes("empty", ByteArray(0)))
        assertFalse(cache.putBytes("too_big", ByteArray(twoMb * 6)))

        assertTrue(cache.putBytes("k1", chunk1))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k2", chunk2))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k3", chunk3))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k4", chunk4))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k5", chunk5))
        assertEquals(10L * 1024L * 1024L, cache.currentSizeBytes())
        assertEquals(5, cache.entryCount())

        // Touch k1 so k2 becomes the least-recently-used entry
        Thread.sleep(15)
        assertArrayEquals(chunk1, cache.getBytes("k1"))
        Thread.sleep(15)

        // Adding k6 (2 MB) must evict k2 so total cache stays <= 10 MB
        assertTrue(cache.putBytes("k6", chunk6))
        assertTrue(cache.currentSizeBytes() <= ThumbnailDiskCache.MAX_CACHE_BYTES)
        assertEquals(5, cache.entryCount())
        assertNull(cache.getBytes("k2"))
        assertArrayEquals(chunk1, cache.getBytes("k1"))
        assertArrayEquals(chunk6, cache.getBytes("k6"))

        cache.remove("k1")
        assertNull(cache.getBytes("k1"))
        assertEquals(4, cache.entryCount())

        cache.clear()
        assertEquals(0L, cache.currentSizeBytes())
        assertEquals(0, cache.entryCount())
    }
}
