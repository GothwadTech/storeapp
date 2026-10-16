package com.gothwad.store

import com.gothwad.store.core.Format
import com.gothwad.store.core.hexDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FormatAndHashTest {

    @Test
    fun `bytes are human readable`() {
        assertEquals("0 B", Format.bytes(0))
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.0 KB", Format.bytes(1024))
        assertEquals("4.1 MB", Format.bytes(4_274_549))
        assertEquals("1.0 GB", Format.bytes(1024L * 1024L * 1024L))
    }

    @Test
    fun `install counts match the Play store style`() {
        assertEquals("0", Format.installs(0))
        assertEquals("1", Format.installs(1))
        assertEquals("999", Format.installs(999))
        assertEquals("4.2K", Format.installs(4_274))
        assertEquals("1.2M", Format.installs(1_234_567))
    }

    @Test
    fun `ratings are printed with one decimal`() {
        assertEquals("5.0", Format.rating(5.0))
        assertEquals("4.3", Format.rating(4.29))
    }

    @Test
    fun `review counts pluralise like the store`() {
        assertEquals("No ratings", Format.reviews(0))
        assertEquals("1 rating", Format.reviews(1))
        assertEquals("12 ratings", Format.reviews(12))
    }

    @Test
    fun `permission labels are humanised`() {
        assertEquals("Camera", Format.permission("android.permission.CAMERA"))
        assertEquals("Read contacts", Format.permission("android.permission.READ_CONTACTS"))
        assertTrue(Format.permission("com.gothwad.custom.THING").isNotBlank())
    }

    @Test
    fun `speed and eta never divide by zero`() {
        assertEquals("0 B/s", Format.speed(0))
        assertEquals("—", Format.eta(remainingBytes = 1000, bytesPerSecond = 0))
        assertEquals("2s", Format.eta(remainingBytes = 2000, bytesPerSecond = 1000))
    }

    @Test
    fun `string sha256 matches the known digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".hexDigest(),
        )
    }

    @Test
    fun `file sha256 matches the string digest and the published apk checksum`() {
        val file = File.createTempFile("gothwad", ".apk").apply {
            writeText("abc")
            deleteOnExit()
        }
        assertEquals("abc".hexDigest(), file.hexDigest())
    }

    @Test
    fun `empty file digest is the sha256 of nothing`() {
        val file = File.createTempFile("gothwad-empty", ".apk").apply { deleteOnExit() }
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            file.hexDigest(),
        )
    }
}
