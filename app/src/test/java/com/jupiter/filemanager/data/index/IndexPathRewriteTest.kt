package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [IndexPathRewrite] — the pure path arithmetic that lets a directory
 * move/rename rewrite its whole indexed subtree (preserving hashes) instead of dropping
 * every descendant. These run on the JVM (no Room/Android), so they gate in CI.
 */
class IndexPathRewriteTest {

    @Test
    fun `rewrite moves the root itself`() {
        assertEquals(
            "/storage/emulated/0/Photos2025",
            IndexPathRewrite.rewrite(
                "/storage/emulated/0/Photos",
                "/storage/emulated/0/Photos2025",
                "/storage/emulated/0/Photos",
            ),
        )
    }

    @Test
    fun `rewrite moves every descendant under the new prefix`() {
        val old = "/storage/emulated/0/Photos"
        val new = "/storage/emulated/0/Album"
        assertEquals("/storage/emulated/0/Album/2024/a.jpg",
            IndexPathRewrite.rewrite(old, new, "/storage/emulated/0/Photos/2024/a.jpg"))
        assertEquals("/storage/emulated/0/Album/2025/c.jpg",
            IndexPathRewrite.rewrite(old, new, "/storage/emulated/0/Photos/2025/c.jpg"))
    }

    @Test
    fun `rewrite never touches a sibling that only shares a textual prefix`() {
        // The classic bug: "Photos" must not rewrite "Photos_2024".
        assertNull(
            IndexPathRewrite.rewrite(
                "/storage/emulated/0/Photos",
                "/storage/emulated/0/Album",
                "/storage/emulated/0/Photos_2024/a.jpg",
            ),
        )
    }

    @Test
    fun `rewrite ignores unrelated paths and empty prefixes`() {
        assertNull(IndexPathRewrite.rewrite("/a/b", "/a/c", "/x/y/z"))
        assertNull(IndexPathRewrite.rewrite("", "/a/c", "/a/b"))
    }

    @Test
    fun `rewrite tolerates trailing slashes on the prefixes`() {
        assertEquals(
            "/a/new/child.txt",
            IndexPathRewrite.rewrite("/a/old/", "/a/new/", "/a/old/child.txt"),
        )
    }

    @Test
    fun `parentOf and nameOf split a path`() {
        assertEquals("/a/b", IndexPathRewrite.parentOf("/a/b/c.txt"))
        assertEquals("c.txt", IndexPathRewrite.nameOf("/a/b/c.txt"))
        assertNull(IndexPathRewrite.parentOf("file"))
    }

    @Test
    fun `identityUnchanged only preserves hashes for unchanged files`() {
        assertTrue(IndexPathRewrite.identityUnchanged(false, 100L, 5L, 100L, 5L))
        assertFalse("size changed -> re-hash",
            IndexPathRewrite.identityUnchanged(false, 100L, 5L, 200L, 5L))
        assertFalse("mtime changed -> re-hash",
            IndexPathRewrite.identityUnchanged(false, 100L, 5L, 100L, 6L))
        assertFalse("directories never carry a hash",
            IndexPathRewrite.identityUnchanged(true, 0L, 5L, 0L, 5L))
    }
}
