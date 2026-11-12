package com.example

import com.example.data.LanDiscoveryHelper
import com.example.data.P2PTransferManager
import com.example.data.ThumbnailDiskCache
import com.example.data.WifiP2pController
import com.example.model.CartoonAvatars
import com.example.model.DiscoveredDevice
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
    fun testThumbnailDiskCacheLowQualityDefaultsAnd20MbLimit() {
        assertEquals(20L * 1024L * 1024L, ThumbnailDiskCache.MAX_CACHE_BYTES)
        assertEquals(70, ThumbnailDiskCache.LOW_QUALITY_PERCENT)
        assertEquals(256, ThumbnailDiskCache.MAX_IMAGE_THUMB_PX)
        assertEquals(144, ThumbnailDiskCache.MAX_APP_ICON_PX)
    }

    @Test
    fun testThumbnailDiskCacheEnforces20MbCapWithLruEviction() {
        val cache = ThumbnailDiskCache(tempFolder.newFolder("thumb_cache"))
        val fourMb = 4 * 1024 * 1024
        val chunk1 = ByteArray(fourMb) { 1 }
        val chunk2 = ByteArray(fourMb) { 2 }
        val chunk3 = ByteArray(fourMb) { 3 }
        val chunk4 = ByteArray(fourMb) { 4 }
        val chunk5 = ByteArray(fourMb) { 5 }
        val chunk6 = ByteArray(fourMb) { 6 }

        assertFalse(cache.putBytes("empty", ByteArray(0)))
        assertFalse(cache.putBytes("too_big", ByteArray(fourMb * 6)))

        assertTrue(cache.putBytes("k1", chunk1))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k2", chunk2))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k3", chunk3))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k4", chunk4))
        Thread.sleep(15)
        assertTrue(cache.putBytes("k5", chunk5))
        assertEquals(20L * 1024L * 1024L, cache.currentSizeBytes())
        assertEquals(5, cache.entryCount())

        // Touch k1 so k2 becomes the least-recently-used entry
        Thread.sleep(15)
        assertArrayEquals(chunk1, cache.getBytes("k1"))
        Thread.sleep(15)

        // Adding k6 (4 MB) must evict k2 so total cache stays <= 20 MB
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

    @Test
    fun testPeerMergingPreservesLanIpPortAndP2pMac() {
        val avatarRes = CartoonAvatars.getAvatar(3).drawableRes
        val p2pEntry = DiscoveredDevice(
            id = "02:aa:bb:cc:dd:ee",
            name = "Alice",
            avatarRes = avatarRes,
            ip = "192.168.49.1",
            port = 8888,
            p2pMacAddress = "02:aa:bb:cc:dd:ee",
            lastSeenTimestamp = 1000L
        )
        val lanEntry = DiscoveredDevice(
            id = "nsd_192_168_1_42",
            name = "Alice",
            avatarRes = avatarRes,
            ip = "192.168.1.42",
            port = 9123,
            p2pMacAddress = null,
            lastSeenTimestamp = 900L
        )

        val merged = P2PTransferManager.mergeDiscoveredPeers(listOf(p2pEntry, lanEntry))
        assertEquals(1, merged.size)
        val single = merged.first()
        assertEquals("Alice", single.name)
        assertEquals("192.168.1.42", single.ip)
        assertEquals(9123, single.port)
        assertEquals("02:aa:bb:cc:dd:ee", single.p2pMacAddress)

        // Fallback generic device peer should upgrade name when MAC-matched profile appears
        val fallbackPeer = DiscoveredDevice(
            id = "fallback_11:22:33:44:55:66",
            name = "Android_XYZ",
            avatarRes = CartoonAvatars.getAvatar(0).drawableRes,
            ip = "192.168.49.1",
            port = 8888,
            p2pMacAddress = "11:22:33:44:55:66",
            lastSeenTimestamp = 500L
        )
        val realPeer = DiscoveredDevice(
            id = "11:22:33:44:55:66",
            name = "Bob",
            avatarRes = avatarRes,
            ip = "192.168.49.1",
            port = 8888,
            p2pMacAddress = "11:22:33:44:55:66",
            lastSeenTimestamp = 800L
        )
        val mergedFallback = P2PTransferManager.mergeDiscoveredPeers(listOf(fallbackPeer, realPeer))
        assertEquals(1, mergedFallback.size)
        assertEquals("Bob", mergedFallback.first().name)
        assertEquals(avatarRes, mergedFallback.first().avatarRes)
    }

    @Test
    fun testLanBeaconAndPongParsing() {
        val ownIps = setOf("192.168.1.10", "192.168.49.1")
        val targetAvatar = CartoonAvatars.getAvatar(4)
        val beacon = "CUTESHARE_BEACON|Priya|12345|9001|192.168.1.55|4|02:11:22:33:44:55"

        // Own IP echo must be suppressed
        assertNull(LanDiscoveryHelper.parseBeaconPayload(beacon, "192.168.1.10", ownIps))

        val parsed = LanDiscoveryHelper.parseBeaconPayload(beacon, "192.168.1.55", ownIps)
        assertEquals("Priya", parsed?.name)
        assertEquals(targetAvatar.drawableRes, parsed?.avatarRes)
        assertEquals(9001, parsed?.port)
        assertEquals("192.168.1.55", parsed?.ip)
        assertEquals("02:11:22:33:44:55", parsed?.p2pMacAddress)

        val pong = "CUTESHARE_PONG|Rohan|99999|8888|192.168.43.1|2|aa:bb:cc:dd:ee:ff"
        val parsedPong = LanDiscoveryHelper.parsePongPayload(pong, "192.168.43.1")
        assertEquals("Rohan", parsedPong?.name)
        assertEquals(CartoonAvatars.getAvatar(2).drawableRes, parsedPong?.avatarRes)
        assertEquals(8888, parsedPong?.port)
        assertEquals("aa:bb:cc:dd:ee:ff", parsedPong?.p2pMacAddress)
    }

    @Test
    fun testWifiP2pInstanceExtractionAndDeviceFiltering() {
        assertEquals("Alex", WifiP2pController.extractCuteShareNameFromInstance("CuteShare_Alex"))
        assertNull(WifiP2pController.extractCuteShareNameFromInstance("HP-Print-LaserJet"))
        assertNull(WifiP2pController.extractCuteShareNameFromInstance("CuteShare_   "))

        assertTrue(WifiP2pController.isPhoneOrTabletDeviceType("10-0050F204-5"))
        assertTrue(WifiP2pController.isPhoneOrTabletDeviceType("1-0050F204-1"))
        assertTrue(WifiP2pController.isPhoneOrTabletDeviceType(null))
        assertFalse(WifiP2pController.isPhoneOrTabletDeviceType("7-0050F204-1")) // TV / Display
        assertFalse(WifiP2pController.isPhoneOrTabletDeviceType("3-0050F204-1")) // Printer
    }
}
