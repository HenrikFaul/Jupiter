package com.jupiter.filemanager.feature.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class VaultPinInputOwnershipTest {

    @Test
    fun `take copies before synchronously clearing caller input`() {
        val callerOwned = "5739".toCharArray()

        val transferred = VaultPinInputOwnership.take(callerOwned)

        assertNotSame(callerOwned, transferred)
        assertArrayEquals("5739".toCharArray(), transferred)
        assertArrayEquals(CharArray(4), callerOwned)

        transferred.fill('\u0000')
    }
}
