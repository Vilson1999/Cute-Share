package com.example

import com.example.model.CartoonAvatars
import com.example.model.FileCategory
import com.example.model.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
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
    fun testFileCategories() {
        val categories = FileCategory.values().map { it.title }
        assertTrue(categories.contains("Image"))
        assertTrue(categories.contains("Video"))
        assertTrue(categories.contains("Audio"))
        assertTrue(categories.contains("App"))
        assertTrue(categories.contains("Documents"))
        assertTrue(categories.contains("Archives"))
        assertTrue(categories.contains("Storage"))
    }
}
