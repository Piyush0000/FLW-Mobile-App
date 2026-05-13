package org.piramalswasthya.sakhi.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncImageCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `returns a deterministic file path under the cache directory`() {
        val cacheDir = tempFolder.newFolder("cache")

        val file = resolveSyncImageFile(cacheDir, "saas_bahu_sammelan", itemId = 42L, index = 0, ext = "jpg")

        assertEquals(File(cacheDir, "saas_bahu_sammelan_42_img0.jpg"), file)
    }

    @Test
    fun `the same inputs always yield the same path so subsequent syncs overwrite`() {
        val cacheDir = tempFolder.newFolder("cache")

        val first = resolveSyncImageFile(cacheDir, "uwin", itemId = 7L, index = 1, ext = "png")
        first.writeBytes(byteArrayOf(0x01))

        val second = resolveSyncImageFile(cacheDir, "uwin", itemId = 7L, index = 1, ext = "png")
        second.writeBytes(byteArrayOf(0x02))

        assertEquals(first.absolutePath, second.absolutePath)
        assertEquals(1, cacheDir.listFiles().orEmpty().size)
        assertEquals(0x02.toByte(), cacheDir.listFiles()!!.single().readBytes().single())
    }

    @Test
    fun `removes a stale cache file when the same item-index reappears with a different extension`() {
        val cacheDir = tempFolder.newFolder("cache")
        val stale = File(cacheDir, "meeting_3_img0.jpg").apply { writeBytes(byteArrayOf(0x01)) }
        assertTrue(stale.exists())

        val resolved = resolveSyncImageFile(cacheDir, "meeting", itemId = 3L, index = 0, ext = "png")
        resolved.writeBytes(byteArrayOf(0x02))

        assertFalse("stale .jpg must be removed once the .png variant is materialised", stale.exists())
        assertTrue(resolved.exists())
        assertEquals(1, cacheDir.listFiles().orEmpty().size)
    }

    @Test
    fun `does not touch cache files belonging to a different item`() {
        val cacheDir = tempFolder.newFolder("cache")
        val sibling = File(cacheDir, "meeting_99_img0.jpg").apply { writeBytes(byteArrayOf(0x09)) }

        resolveSyncImageFile(cacheDir, "meeting", itemId = 3L, index = 0, ext = "png")
            .writeBytes(byteArrayOf(0x02))

        assertTrue("sibling item's cache file must survive", sibling.exists())
    }

    @Test
    fun `does not touch cache files at a different image index of the same item`() {
        val cacheDir = tempFolder.newFolder("cache")
        val otherIndex = File(cacheDir, "meeting_3_img1.jpg").apply { writeBytes(byteArrayOf(0x09)) }

        resolveSyncImageFile(cacheDir, "meeting", itemId = 3L, index = 0, ext = "png")
            .writeBytes(byteArrayOf(0x02))

        assertTrue("img1 entry must not be affected when resolving img0", otherIndex.exists())
    }

    @Test
    fun `does not touch unrelated cache content`() {
        val cacheDir = tempFolder.newFolder("cache")
        val unrelated = File(cacheDir, "incentive_3.bin").apply { writeBytes(byteArrayOf(0x09)) }
        val differentPrefixSameId = File(cacheDir, "uwin_3_img0.jpg").apply { writeBytes(byteArrayOf(0x07)) }

        resolveSyncImageFile(cacheDir, "meeting", itemId = 3L, index = 0, ext = "png")
            .writeBytes(byteArrayOf(0x02))

        assertTrue("files with another feature's prefix must not be deleted", unrelated.exists())
        assertTrue(
            "same itemId+index under a different feature prefix must not be deleted",
            differentPrefixSameId.exists()
        )
    }

    @Test
    fun `creates the cache directory if it does not yet exist`() {
        val cacheDir = File(tempFolder.root, "freshly-created/cache")
        assertFalse(cacheDir.exists())

        val resolved = resolveSyncImageFile(cacheDir, "meeting", itemId = 1L, index = 0, ext = "jpg")

        assertTrue("cacheDir should be created on demand", cacheDir.exists())
        assertEquals(cacheDir, resolved.parentFile)
    }

    @Test
    fun `is a no-op when there is nothing stale to clean up`() {
        val cacheDir = tempFolder.newFolder("cache")

        val resolved = resolveSyncImageFile(cacheDir, "meeting", itemId = 1L, index = 0, ext = "jpg")

        assertEquals(File(cacheDir, "meeting_1_img0.jpg"), resolved)
        assertEquals(0, cacheDir.listFiles().orEmpty().size)
    }

    @Test
    fun `tolerates an existing file with the canonical name so writers can overwrite`() {
        val cacheDir = tempFolder.newFolder("cache")
        val existing = File(cacheDir, "meeting_1_img0.jpg").apply { writeBytes(byteArrayOf(0x11)) }

        val resolved = resolveSyncImageFile(cacheDir, "meeting", itemId = 1L, index = 0, ext = "jpg")
        resolved.writeBytes(byteArrayOf(0x22))

        assertEquals(existing.absolutePath, resolved.absolutePath)
        assertTrue(resolved.exists())
        assertEquals(0x22.toByte(), resolved.readBytes().single())
    }
}
