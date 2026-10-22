package io.github.nikoapps.dialer.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `restoring puts the old file back`() {
        val photo = folder.newFile("a.jpg").apply { writeText("old") }
        val backup = FileBackup.of(photo)
        photo.writeText("new")
        backup.restore()
        assertEquals("old", photo.readText())
        assertEquals(listOf("a.jpg"), folder.root.list()!!.toList())
    }

    @Test
    fun `restoring with no old file deletes the new one`() {
        val photo = folder.root.resolve("a.jpg")
        val backup = FileBackup.of(photo)
        photo.writeText("new")
        backup.restore()
        assertFalse(photo.exists())
    }

    @Test
    fun `dropping keeps the new file and nothing else`() {
        val photo = folder.newFile("a.jpg").apply { writeText("old") }
        val backup = FileBackup.of(photo)
        photo.writeText("new")
        backup.drop()
        assertEquals("new", photo.readText())
        assertEquals(listOf("a.jpg"), folder.root.list()!!.toList())
    }
}
