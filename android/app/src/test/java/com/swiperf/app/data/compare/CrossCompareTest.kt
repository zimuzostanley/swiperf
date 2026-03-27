package com.swiperf.app.data.compare

import org.junit.Assert.*
import org.junit.Test

class CrossCompareTest {

    @Test
    fun createState_initializesWithFirstPair() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        assertNotNull(state.currentPair)
        assertFalse(state.isComplete)
    }

    @Test
    fun positiveComparison_mergesComponents() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.POSITIVE)
        assertTrue(state.uf.connected("a", "b"))
    }

    @Test
    fun negativeComparison_addsEdge() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.NEGATIVE)
        val rootA = state.uf.find("a"); val rootB = state.uf.find("b")
        assertTrue(state.negativeEdges.contains(CrossCompare.edgeKey(rootA, rootB)))
    }

    @Test
    fun nextPair_prioritizesLargestComponents() {
        val state = CrossCompare.createState(listOf("a", "b", "c", "d", "e"))
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.POSITIVE)
        CrossCompare.recordComparison(state, "c", "d", ComparisonResult.POSITIVE)
        val pair = CrossCompare.nextPair(state)
        assertNotNull(pair)
        // Should compare between two largest groups
        val ab = setOf("a", "b"); val cd = setOf("c", "d")
        assertTrue(
            (pair!!.first in ab && pair.second in cd) ||
            (pair.first in cd && pair.second in ab) ||
            pair.first == "e" || pair.second == "e"
        )
    }

    @Test
    fun anchorMode_staysOnAnchor() {
        val keys = listOf("anchor", "b", "c", "d", "e")
        val state = CrossCompare.createState(keys)
        val pair = CrossCompare.nextPairForAnchor(state, "anchor")
        assertNotNull(pair)
        assertEquals("anchor", pair!!.first)
    }

    @Test
    fun progress_calculatesCorrectly() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        val p0 = CrossCompare.getProgress(state)
        assertEquals(0, p0.pct) // Not really 0 but close
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.POSITIVE)
        val p1 = CrossCompare.getProgress(state)
        assertTrue(p1.pct > p0.pct)
    }

    @Test
    fun undo_revertsLastAction() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.POSITIVE)
        assertTrue(state.uf.connected("a", "b"))
        CrossCompare.undoComparison(state)
        assertFalse(state.uf.connected("a", "b"))
    }

    @Test
    fun getResults_groupsByComponent() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        CrossCompare.recordComparison(state, "a", "b", ComparisonResult.POSITIVE)
        val results = CrossCompare.getResults(state)
        assertEquals(2, results.groups.size) // {a,b} and {c}
        assertEquals(2, results.groups[0].size) // Largest first
    }

    @Test
    fun discard_removesFromComparison() {
        val state = CrossCompare.createState(listOf("a", "b", "c"))
        CrossCompare.discardTrace(state, "a")
        val results = CrossCompare.getResults(state)
        assertEquals(listOf("a"), results.discarded)
    }
}
