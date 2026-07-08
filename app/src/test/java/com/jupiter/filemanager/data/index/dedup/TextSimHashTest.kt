package com.jupiter.filemanager.data.index.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the text/code SimHash near-duplicate layer. */
class TextSimHashTest {

    @Test
    fun `identical text has distance zero`() {
        val a = TextSimHash.of("The quick brown fox jumps over the lazy dog")
        val b = TextSimHash.of("The quick brown fox jumps over the lazy dog")
        assertEquals(0, TextSimHash.distance(a, b))
        assertTrue(TextSimHash.isNear(a, b))
    }

    @Test
    fun `reformatting and case changes stay near`() {
        val a = TextSimHash.of("fun main() {\n    println(\"hello world\")\n}")
        val b = TextSimHash.of("FUN MAIN( ) {   println( \"HELLO WORLD\" ) }")
        assertTrue("distance=${TextSimHash.distance(a, b)}", TextSimHash.isNear(a, b))
    }

    @Test
    fun `unrelated texts are far apart`() {
        val a = TextSimHash.of("quarterly revenue increased across all regions this fiscal year")
        val b = TextSimHash.of("the cat sat quietly on the warm windowsill all afternoon")
        assertFalse(TextSimHash.isNear(a, b))
        assertTrue("distance=${TextSimHash.distance(a, b)}", TextSimHash.distance(a, b) > TextSimHash.DEFAULT_NEAR_THRESHOLD)
    }

    @Test
    fun `empty or token-less text never matches`() {
        assertEquals(TextSimHash.EMPTY, TextSimHash.of(""))
        assertEquals(TextSimHash.EMPTY, TextSimHash.of("   \n\t  "))
        assertFalse(TextSimHash.isNear(TextSimHash.EMPTY, TextSimHash.EMPTY))
        assertEquals(0.0, TextSimHash.similarity(TextSimHash.EMPTY, 123L), 0.0)
    }

    @Test
    fun `similarity is in unit range and higher for closer texts`() {
        val base = TextSimHash.of("alpha beta gamma delta epsilon zeta eta theta")
        val near = TextSimHash.of("alpha beta gamma delta epsilon zeta eta iota") // one token changed
        val far = TextSimHash.of("completely different words with nothing shared here")
        assertTrue(TextSimHash.similarity(base, near) > TextSimHash.similarity(base, far))
        assertTrue(TextSimHash.similarity(base, near) in 0.0..1.0)
    }
}
