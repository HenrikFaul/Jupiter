package com.jupiter.filemanager.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    private val sampleError: AppError = AppError.NotFound("/missing/path")

    // --- onSuccess ---

    @Test
    fun onSuccess_invokesBlock_onSuccessVariant() {
        val result: AppResult<Int> = AppResult.Success(42)
        var captured: Int? = null

        result.onSuccess { captured = it }

        assertEquals(42, captured)
    }

    @Test
    fun onSuccess_doesNotInvokeBlock_onFailureVariant() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)
        var invoked = false

        result.onSuccess { invoked = true }

        assertFalse(invoked)
    }

    @Test
    fun onSuccess_returnsSameInstance() {
        val result: AppResult<Int> = AppResult.Success(7)

        val returned = result.onSuccess { }

        assertSame(result, returned)
    }

    @Test
    fun onSuccess_returnsSameInstance_onFailure() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)

        val returned = result.onSuccess { }

        assertSame(result, returned)
    }

    // --- onFailure ---

    @Test
    fun onFailure_invokesBlock_onFailureVariant() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)
        var captured: AppError? = null

        result.onFailure { captured = it }

        assertSame(sampleError, captured)
    }

    @Test
    fun onFailure_doesNotInvokeBlock_onSuccessVariant() {
        val result: AppResult<Int> = AppResult.Success(1)
        var invoked = false

        result.onFailure { invoked = true }

        assertFalse(invoked)
    }

    @Test
    fun onFailure_returnsSameInstance() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)

        val returned = result.onFailure { }

        assertSame(result, returned)
    }

    @Test
    fun onFailure_returnsSameInstance_onSuccess() {
        val result: AppResult<Int> = AppResult.Success(99)

        val returned = result.onFailure { }

        assertSame(result, returned)
    }

    // --- getOrNull ---

    @Test
    fun getOrNull_returnsData_onSuccess() {
        val result: AppResult<String> = AppResult.Success("hello")

        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun getOrNull_returnsNull_onFailure() {
        val result: AppResult<String> = AppResult.Failure(sampleError)

        assertNull(result.getOrNull())
    }

    @Test
    fun getOrNull_returnsNullData_whenSuccessHoldsNull() {
        val result: AppResult<String?> = AppResult.Success(null)

        assertNull(result.getOrNull())
    }

    // --- map ---

    @Test
    fun map_transformsSuccessValue() {
        val result: AppResult<Int> = AppResult.Success(21)

        val mapped = result.map { it * 2 }

        assertTrue(mapped is AppResult.Success)
        assertEquals(42, (mapped as AppResult.Success).data)
    }

    @Test
    fun map_passesThroughFailure_unchanged() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)

        val mapped = result.map { it * 2 }

        assertTrue(mapped is AppResult.Failure)
        assertSame(sampleError, (mapped as AppResult.Failure).error)
    }

    @Test
    fun map_doesNotInvokeTransform_onFailure() {
        val result: AppResult<Int> = AppResult.Failure(sampleError)
        var invoked = false

        result.map { invoked = true; it }

        assertFalse(invoked)
    }

    @Test
    fun map_canChangeType() {
        val result: AppResult<Int> = AppResult.Success(5)

        val mapped: AppResult<String> = result.map { "value=$it" }

        assertEquals("value=5", (mapped as AppResult.Success).data)
    }
}
