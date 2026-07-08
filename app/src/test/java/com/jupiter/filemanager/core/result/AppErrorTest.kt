package com.jupiter.filemanager.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {

    @Test
    fun permissionDenied_displayMessage() {
        assertEquals("Storage permission is required.", AppError.PermissionDenied.displayMessage)
    }

    @Test
    fun permissionDenied_isSingleton() {
        assertSame(AppError.PermissionDenied, AppError.PermissionDenied)
    }

    @Test
    fun notFound_displayMessage() {
        assertEquals("Not found: /a/b.txt", AppError.NotFound("/a/b.txt").displayMessage)
    }

    @Test
    fun accessDenied_displayMessage() {
        assertEquals("Access denied: /secure", AppError.AccessDenied("/secure").displayMessage)
    }

    @Test
    fun alreadyExists_displayMessage() {
        assertEquals("Already exists: report.pdf", AppError.AlreadyExists("report.pdf").displayMessage)
    }

    @Test
    fun io_displayMessage_isDetail() {
        assertEquals("disk full", AppError.Io("disk full").displayMessage)
    }

    @Test
    fun unknown_displayMessage_isDetail() {
        assertEquals("boom", AppError.Unknown("boom").displayMessage)
    }

    @Test
    fun io_causeDefaultsToNull() {
        assertNull(AppError.Io("x").cause)
    }

    @Test
    fun io_causeIsExposed() {
        val t = IllegalStateException("bad")
        assertSame(t, AppError.Io("x", t).cause)
    }

    @Test
    fun unknown_causeDefaultsToNull() {
        assertNull(AppError.Unknown("x").cause)
    }

    @Test
    fun unknown_causeIsExposed() {
        val t = RuntimeException("oops")
        assertSame(t, AppError.Unknown("x", t).cause)
    }

    @Test
    fun permissionDenied_causeIsNull() {
        assertNull(AppError.PermissionDenied.cause)
    }

    @Test
    fun notFound_causeIsNull() {
        assertNull(AppError.NotFound("/a").cause)
    }

    @Test
    fun notFound_equalityAndHashCode() {
        val a = AppError.NotFound("/a")
        val b = AppError.NotFound("/a")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, AppError.NotFound("/b"))
    }

    @Test
    fun accessDenied_equality() {
        assertEquals(AppError.AccessDenied("/p"), AppError.AccessDenied("/p"))
        assertNotEquals(AppError.AccessDenied("/p"), AppError.AccessDenied("/q"))
    }

    @Test
    fun alreadyExists_equality() {
        assertEquals(AppError.AlreadyExists("n"), AppError.AlreadyExists("n"))
        assertNotEquals(AppError.AlreadyExists("n"), AppError.AlreadyExists("m"))
    }

    @Test
    fun io_equality() {
        assertEquals(AppError.Io("d"), AppError.Io("d"))
        assertNotEquals(AppError.Io("d"), AppError.Io("e"))
    }

    @Test
    fun unknown_equality() {
        assertEquals(AppError.Unknown("d"), AppError.Unknown("d"))
        assertNotEquals(AppError.Unknown("d"), AppError.Unknown("e"))
    }

    @Test
    fun differentVariants_areNotEqual() {
        assertNotEquals(
            AppError.NotFound("/x") as AppError,
            AppError.AccessDenied("/x") as AppError,
        )
    }

    @Test
    fun copy_producesEqualInstance() {
        val original = AppError.NotFound("/orig")
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun copy_withChangedField_isNotEqual() {
        val original = AppError.NotFound("/orig")
        val changed = original.copy(path = "/new")
        assertNotEquals(original, changed)
        assertEquals("Not found: /new", changed.displayMessage)
    }

    @Test
    fun allVariants_areAppError() {
        val variants: List<AppError> = listOf(
            AppError.PermissionDenied,
            AppError.NotFound("/a"),
            AppError.AccessDenied("/a"),
            AppError.AlreadyExists("n"),
            AppError.Io("d"),
            AppError.Unknown("d"),
        )
        assertTrue(variants.all { it.displayMessage.isNotEmpty() })
    }
}
