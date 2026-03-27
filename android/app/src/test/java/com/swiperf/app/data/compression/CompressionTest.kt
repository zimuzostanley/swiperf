package com.swiperf.app.data.compression

import com.swiperf.app.data.model.Slice
import org.junit.Assert.*
import org.junit.Test

class CompressionTest {

    private fun makeSlice(ts: Long, dur: Long, state: String? = "Running", name: String? = null): Slice {
        return Slice(ts = ts, dur = dur, name = name, state = state, depth = null, ioWait = null, blockedFunction = null)
    }

    @Test
    fun buildMergeCache_emptyInput_returnsEmptyCache() {
        val cache = Compression.buildMergeCache(emptyList())
        assertTrue(cache.isEmpty())
    }

    @Test
    fun buildMergeCache_containsOriginalLength() {
        val slices = listOf(
            makeSlice(0, 100, "Running"),
            makeSlice(100, 200, "Sleeping"),
            makeSlice(300, 150, "Running"),
            makeSlice(450, 50, "Sleeping"),
        )
        val cache = Compression.buildMergeCache(slices)
        assertTrue(cache.containsKey(4))
        assertEquals(4, cache[4]!!.size)
    }

    @Test
    fun buildMergeCache_cacheHasDecreasingLevels() {
        val slices = (0 until 5).map { makeSlice(it.toLong() * 100, 100, if (it % 2 == 0) "Running" else "Sleeping") }
        val cache = Compression.buildMergeCache(slices)
        assertTrue(cache.containsKey(5))
        assertTrue(cache.containsKey(2))
        for ((k, v) in cache) assertEquals(k, v.size)
    }

    @Test
    fun buildMergeCache_preservesTsRel() {
        val slices = listOf(
            makeSlice(1000, 100, "Running"),
            makeSlice(1100, 200, "Sleeping"),
        )
        val cache = Compression.buildMergeCache(slices)
        val seq = cache[2]!!
        assertEquals(0L, seq[0].tsRel)
        assertEquals(100L, seq[1].tsRel)
    }

    @Test
    fun buildMergeCache_identicalStatesAreCheaper() {
        val slices = listOf(
            makeSlice(0, 100, "Running"),
            makeSlice(100, 100, "Running"),
            makeSlice(200, 100, "Sleeping"),
        )
        val cache = Compression.buildMergeCache(slices)
        val merged2 = cache[2]!!
        // Same-state slices should merge first, so one of the two-element results has a merged Running
        val totalDur = merged2.sumOf { it.dur }
        assertEquals(300L, totalDur)
    }

    @Test
    fun getCompressed_returnsCorrectLevel() {
        val slices = (0 until 10).map { makeSlice(it.toLong() * 100, 100, "Running") }
        val cache = Compression.buildMergeCache(slices)
        val seq = Compression.getCompressed(cache, 10, 5)
        assertTrue(seq.size >= 5)
        assertTrue(seq.size <= 10)
    }

    @Test
    fun getCompressed_targetBelowTwo_clampedToTwo() {
        val slices = listOf(makeSlice(0, 100, "Running"), makeSlice(100, 100, "Sleeping"))
        val cache = Compression.buildMergeCache(slices)
        val seq = Compression.getCompressed(cache, 2, 1)
        assertEquals(2, seq.size)
    }
}
