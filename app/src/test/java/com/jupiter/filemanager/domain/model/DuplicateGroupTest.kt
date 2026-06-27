package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateGroupTest {

    private fun fileOf(name: String, sizeBytes: Long): FileItem =
        FileItem(
            path = "/storage/emulated/0/$name",
            name = name,
            isDirectory = false,
            sizeBytes = sizeBytes,
            lastModified = 1_000L,
            type = FileType.OTHER,
            extension = "",
        )

    @Test
    fun wastedBytes_isZero_whenGroupIsEmpty() {
        val group = DuplicateGroup(hash = "abc", files = emptyList())
        assertEquals(0L, group.wastedBytes)
    }

    @Test
    fun wastedBytes_isZero_whenGroupHasSingleFile() {
        val group = DuplicateGroup(hash = "abc", files = listOf(fileOf("a.bin", 9_999L)))
        assertEquals(0L, group.wastedBytes)
    }

    @Test
    fun wastedBytes_isSumOfAllButFirst_whenGroupHasManyFiles() {
        val files = listOf(
            fileOf("a.bin", 100L),
            fileOf("b.bin", 200L),
            fileOf("c.bin", 300L),
        )
        val group = DuplicateGroup(hash = "abc", files = files)
        // Keep one copy (the first), reclaim the rest: 200 + 300.
        assertEquals(500L, group.wastedBytes)
    }

    @Test
    fun wastedBytes_isSumOfAllButFirst_whenSizesDiffer() {
        val files = listOf(
            fileOf("first.bin", 42L),
            fileOf("dup1.bin", 10L),
            fileOf("dup2.bin", 20L),
            fileOf("dup3.bin", 30L),
        )
        val group = DuplicateGroup(hash = "xyz", files = files)
        assertEquals(60L, group.wastedBytes)
    }

    @Test
    fun wastedBytes_forPair_equalsSecondFileSize() {
        val files = listOf(
            fileOf("orig.bin", 1_024L),
            fileOf("copy.bin", 1_024L),
        )
        val group = DuplicateGroup(hash = "pair", files = files)
        assertEquals(1_024L, group.wastedBytes)
    }
}
