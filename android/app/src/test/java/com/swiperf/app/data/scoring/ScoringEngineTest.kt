package com.swiperf.app.data.scoring

import com.swiperf.app.data.model.Slice
import org.junit.Assert.*
import org.junit.Test

class ScoringEngineTest {

    private fun slice(ts: Long, dur: Long, state: String? = "Running", name: String? = null,
                      ioWait: Int? = null, blockedFn: String? = null) =
        Slice(ts, dur, name, state, null, ioWait, blockedFn)

    // ── Interval intersection ──

    @Test
    fun buildRegions_identicalTraces_allAutoSame() {
        val slices = listOf(slice(0, 100, "Running", "foo"), slice(100, 200, "Sleeping", "bar"))
        val regions = ScoringEngine.buildRegions(slices, 300, slices, 300)
        assertTrue(regions.all { it.isAutoSame })
    }

    @Test
    fun buildRegions_completelyDifferent_noneAutoSame() {
        val anchor = listOf(slice(0, 300, "Running", "foo"))
        val target = listOf(slice(0, 300, "Sleeping", "bar"))
        val regions = ScoringEngine.buildRegions(anchor, 300, target, 300)
        assertEquals(1, regions.size)
        assertFalse(regions[0].isAutoSame)
        assertEquals("Running", regions[0].anchorState)
        assertEquals("Sleeping", regions[0].targetState)
    }

    @Test
    fun buildRegions_partialOverlap_mixedRegions() {
        val anchor = listOf(slice(0, 150, "Running", "foo"), slice(150, 150, "Sleeping", "bar"))
        val target = listOf(slice(0, 100, "Running", "foo"), slice(100, 200, "Running", "baz"))
        val regions = ScoringEngine.buildRegions(anchor, 300, target, 300)
        // First region [0, 100/300] both Running/foo → auto same
        // Middle region [100/300, 150/300] anchor Running/foo, target Running/baz → name differs
        // Last region [150/300, 300/300] anchor Sleeping/bar, target Running/baz → state+name differ
        assertTrue(regions.size >= 3)
        assertTrue(regions[0].isAutoSame)
        assertFalse(regions[1].isAutoSame)
    }

    @Test
    fun buildRegions_differentDurations_proportionalAlignment() {
        // Anchor: 200ms total, Target: 400ms total
        // Both have same proportional structure: first half Running, second half Sleeping
        val anchor = listOf(slice(0, 100, "Running"), slice(100, 100, "Sleeping"))
        val target = listOf(slice(0, 200, "Running"), slice(200, 200, "Sleeping"))
        val regions = ScoringEngine.buildRegions(anchor, 200, target, 400)
        assertTrue(regions.all { it.isAutoSame })
    }

    @Test
    fun buildRegions_empty_returnsEmpty() {
        val regions = ScoringEngine.buildRegions(emptyList(), 0, emptyList(), 0)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun buildRegions_mergesAdjacentSame() {
        // Two adjacent slices with same state in both anchor and target should merge
        val anchor = listOf(slice(0, 100, "Running", "a"), slice(100, 100, "Running", "a"))
        val target = listOf(slice(0, 200, "Running", "a"))
        val regions = ScoringEngine.buildRegions(anchor, 200, target, 200)
        // Should merge into a single auto-same region
        assertEquals(1, regions.size)
        assertTrue(regions[0].isAutoSame)
    }

    @Test
    fun buildRegions_diffs_onlyDifferingFields() {
        val anchor = listOf(slice(0, 100, "Running", "foo", ioWait = 0))
        val target = listOf(slice(0, 100, "Running", "bar", ioWait = 0))
        val regions = ScoringEngine.buildRegions(anchor, 100, target, 100)
        assertEquals(1, regions.size)
        assertEquals(1, regions[0].diffs.size)
        assertEquals("name", regions[0].diffs[0].field)
        assertEquals("foo", regions[0].diffs[0].anchorVal)
        assertEquals("bar", regions[0].diffs[0].targetVal)
    }

    // ── Region-to-slice correctness (blown-up must match full track) ──

    @Test
    fun buildRegions_eachRegionMatchesCorrectSlices() {
        // Complex case: anchor and target have different boundaries
        val anchor = listOf(
            slice(0, 40, "Running", "bind"),
            slice(40, 30, "IO", "binder", ioWait = 1),
            slice(70, 30, "Running", "inflate")
        )
        val target = listOf(
            slice(0, 50, "Running", "bind"),
            slice(50, 50, "Sleeping", "gc")
        )
        val regions = ScoringEngine.buildRegions(anchor, 100, target, 100)

        // Verify each region has the correct anchor and target values
        for (r in regions) {
            val mid = (r.start + r.end) / 2
            // Find what anchor slice covers this midpoint
            val expectedAnchor = anchor.find {
                val s = (it.ts).toDouble() / 100
                val e = (it.ts + it.dur).toDouble() / 100
                mid >= s && mid < e
            }
            val expectedTarget = target.find {
                val s = (it.ts).toDouble() / 100
                val e = (it.ts + it.dur).toDouble() / 100
                mid >= s && mid < e
            }
            // Blown-up values must match the source slices
            assertEquals("Region at ${r.start}-${r.end} anchor state", expectedAnchor?.state, r.anchorState)
            assertEquals("Region at ${r.start}-${r.end} anchor name", expectedAnchor?.name, r.anchorName)
            assertEquals("Region at ${r.start}-${r.end} target state", expectedTarget?.state, r.targetState)
            assertEquals("Region at ${r.start}-${r.end} target name", expectedTarget?.name, r.targetName)
        }
    }

    @Test
    fun buildRegions_timeOrderCoversFullTrace() {
        val anchor = listOf(slice(0, 60, "A", "x"), slice(60, 40, "B", "y"))
        val target = listOf(slice(0, 30, "A", "x"), slice(30, 70, "C", "z"))
        val regions = ScoringEngine.buildRegions(anchor, 100, target, 100)

        // Regions should be sorted by start time
        for (i in 0 until regions.size - 1) {
            assertTrue("Regions not sorted", regions[i].start <= regions[i + 1].start)
        }
        // First region starts at 0, last ends at 1
        assertEquals(0.0, regions.first().start, 0.001)
        assertEquals(1.0, regions.last().end, 0.001)
        // No gaps
        for (i in 0 until regions.size - 1) {
            assertEquals("Gap between regions", regions[i].end, regions[i + 1].start, 0.001)
        }
    }

    @Test
    fun buildRegions_allRegionsDurationsSumToOne() {
        val anchor = listOf(slice(0, 25, "A"), slice(25, 25, "B"), slice(50, 50, "C"))
        val target = listOf(slice(0, 40, "X"), slice(40, 60, "Y"))
        val regions = ScoringEngine.buildRegions(anchor, 100, target, 100)
        val totalDur = regions.sumOf { it.duration }
        assertEquals(1.0, totalDur, 0.001)
    }

    // ── Score computation ──

    @Test
    fun score_allAutoSame_is100() {
        val slices = listOf(slice(0, 100, "Running"))
        val state = ScoringEngine.createState(slices, 100, slices, 100)
        assertEquals(1.0, state.score, 0.001)
    }

    @Test
    fun score_allDifferent_userSaysDifferent_is0() {
        val anchor = listOf(slice(0, 100, "Running"))
        val target = listOf(slice(0, 100, "Sleeping"))
        val state = ScoringEngine.createState(anchor, 100, target, 100)
        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.DIFFERENT)
        assertEquals(0.0, state.score, 0.001)
    }

    @Test
    fun score_allDifferent_userSaysSame_is100() {
        val anchor = listOf(slice(0, 100, "Running"))
        val target = listOf(slice(0, 100, "Sleeping"))
        val state = ScoringEngine.createState(anchor, 100, target, 100)
        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.SAME)
        assertEquals(1.0, state.score, 0.001)
    }

    @Test
    fun score_mixed_weightedByDuration() {
        // 70% same (auto), 30% different
        val anchor = listOf(slice(0, 70, "Running", "a"), slice(70, 30, "Sleeping", "b"))
        val target = listOf(slice(0, 70, "Running", "a"), slice(70, 30, "Running", "c"))
        val state = ScoringEngine.createState(anchor, 100, target, 100)
        // Auto-same covers 70%. The 30% region needs user input.
        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.DIFFERENT)
        assertEquals(0.7, state.score, 0.05) // ~70%
    }

    @Test
    fun score_skippedExcluded() {
        val anchor = listOf(slice(0, 50, "Running"), slice(50, 50, "Sleeping"))
        val target = listOf(slice(0, 50, "IO", name = "x"), slice(50, 50, "Dead", name = "y"))
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        // Skip first, mark second as same
        val idx1 = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx1, RegionVerdict.SKIPPED)
        val idx2 = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx2, RegionVerdict.SAME)
        // Score based only on the non-skipped region: 100% of scored = same
        assertEquals(1.0, state.score, 0.001)
    }

    // ── Equivalence learning ──

    @Test
    fun sameEquivalence_cascadesToOtherRegions() {
        // Three regions, all with name "foo" vs "bar" (same name pair)
        val anchor = listOf(
            slice(0, 30, "Running", "foo"),
            slice(30, 30, "Sleeping", "foo"),
            slice(60, 40, "Running", "foo")
        )
        val target = listOf(
            slice(0, 30, "Running", "bar"),
            slice(30, 30, "Sleeping", "bar"),
            slice(60, 40, "Running", "bar")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        // All three differ only in name (foo vs bar)
        // User answers "same" on the first one
        val idx = state.nextRegionIndex!!
        val action = ScoringEngine.recordVerdict(state, idx, RegionVerdict.SAME)

        // The other two should auto-resolve via equivalence
        assertTrue(action.cascadedIndices.size >= 1) // at least some cascaded
        assertTrue(state.isComplete) // all resolved
        assertEquals(1.0, state.score, 0.001)
    }

    @Test
    fun diffEquivalence_cascadesToOtherRegions() {
        val anchor = listOf(
            slice(0, 50, "Running", "foo"),
            slice(50, 50, "Running", "foo")
        )
        val target = listOf(
            slice(0, 50, "Running", "bar"),
            slice(50, 50, "Running", "bar")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.DIFFERENT)

        assertTrue(state.isComplete)
        assertEquals(0.0, state.score, 0.001)
    }

    @Test
    fun equivalence_doesNotCrossFields() {
        // Region 1: name differs (foo vs bar), state same
        // Region 2: state differs (Running vs Sleeping), name same
        // Saying "same" on region 1 should NOT auto-resolve region 2
        val anchor = listOf(
            slice(0, 50, "Running", "foo"),
            slice(50, 50, "Running", "baz")
        )
        val target = listOf(
            slice(0, 50, "Running", "bar"),
            slice(50, 50, "Sleeping", "baz")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.SAME)

        // Region 2 differs in state, not name — should still be unresolved
        assertFalse(state.isComplete)
        assertNotNull(state.nextRegionIndex)
    }

    @Test
    fun signatureMatching_onlyCascadesExactSignature() {
        // Saying "same" on {state+name} diff signature does NOT cascade to {name-only} diff
        val anchor = listOf(
            slice(0, 33, "Running", "foo"),
            slice(33, 34, "Running", "foo"),
            slice(67, 33, "Sleeping", "foo")
        )
        val target = listOf(
            slice(0, 33, "Running", "bar"),
            slice(33, 34, "Sleeping", "bar"),
            slice(67, 33, "Sleeping", "bar")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        // Time-ordered: first region [0, 0.33] has sig {name: foo→bar} (size 1)
        val idx1 = state.nextRegionIndex!!
        val sig1 = state.regions[idx1].diffSignature
        assertEquals(1, sig1.size) // only name differs
        ScoringEngine.recordVerdict(state, idx1, RegionVerdict.SAME)

        // Third region [0.67, 1.0] has same sig {name: foo→bar} → auto-resolved
        // Second region [0.33, 0.67] has sig {state+name} → different signature, NOT resolved
        assertFalse(state.isComplete)
        val next = state.nextRegionIndex!!
        val sig2 = state.regions[next].diffSignature
        assertEquals(2, sig2.size) // state + name differ
    }

    // ── nextRegionIndex ordering ──

    @Test
    fun nextRegion_timeOrdered() {
        val anchor = listOf(
            slice(0, 20, "Running", "a"),
            slice(20, 80, "Running", "b")
        )
        val target = listOf(
            slice(0, 20, "Running", "x"),
            slice(20, 80, "Running", "y")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        val idx = state.nextRegionIndex!!
        val region = state.regions[idx]
        // Earliest differing region (time-ordered)
        assertTrue(region.start < 0.25)
    }

    // ── Undo ──

    @Test
    fun undo_revertsVerdict() {
        val anchor = listOf(slice(0, 100, "Running"))
        val target = listOf(slice(0, 100, "Sleeping"))
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.DIFFERENT)
        assertEquals(0.0, state.score, 0.001)

        ScoringEngine.undo(state)
        assertFalse(state.isComplete)
        assertTrue(state.score.isNaN()) // nothing scored
    }

    @Test
    fun undo_revertsCascadedRegions() {
        val anchor = listOf(
            slice(0, 50, "Running", "foo"),
            slice(50, 50, "Running", "foo")
        )
        val target = listOf(
            slice(0, 50, "Running", "bar"),
            slice(50, 50, "Running", "bar")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        val idx = state.nextRegionIndex!!
        ScoringEngine.recordVerdict(state, idx, RegionVerdict.SAME)
        assertTrue(state.isComplete)

        ScoringEngine.undo(state)
        assertFalse(state.isComplete) // cascaded regions reverted
        assertEquals(0, state.verdicts.size)
    }

    @Test
    fun undo_removesLearnedEquivalences() {
        val anchor = listOf(
            slice(0, 50, "Running", "foo"),
            slice(50, 50, "Running", "foo")
        )
        val target = listOf(
            slice(0, 50, "Running", "bar"),
            slice(50, 50, "Running", "bar")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        ScoringEngine.recordVerdict(state, state.nextRegionIndex!!, RegionVerdict.SAME)
        assertTrue(state.sameSignatures.isNotEmpty())

        ScoringEngine.undo(state)
        assertTrue(state.sameSignatures.isEmpty())
    }

    // ── Edge cases ──

    @Test
    fun emptyTraces_emptyState() {
        val state = ScoringEngine.createState(emptyList(), 0, emptyList(), 0)
        assertTrue(state.regions.isEmpty())
        assertTrue(state.isComplete)
        assertTrue(state.score.isNaN())
    }

    @Test
    fun singleSlice_autoSame() {
        val s = listOf(slice(0, 100, "Running", "foo"))
        val state = ScoringEngine.createState(s, 100, s, 100)
        assertTrue(state.isComplete)
        assertEquals(1.0, state.score, 0.001)
    }

    @Test
    fun nullFields_matchCorrectly() {
        // null == null is same, null != "something" is different
        val anchor = listOf(slice(0, 50, "Running", null), slice(50, 50, "Running", "foo"))
        val target = listOf(slice(0, 50, "Running", null), slice(50, 50, "Running", null))
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        // First half: both null name → auto same
        // Second half: "foo" vs null → differs
        assertFalse(state.isComplete)
        val idx = state.nextRegionIndex!!
        val r = state.regions[idx]
        assertEquals("foo", r.anchorName)
        assertNull(r.targetName)
    }

    @Test
    fun multipleUndos_fullyReverts() {
        val anchor = listOf(
            slice(0, 33, "A", "x"),
            slice(33, 34, "B", "y"),
            slice(67, 33, "C", "z")
        )
        val target = listOf(
            slice(0, 33, "A", "p"),
            slice(33, 34, "B", "q"),
            slice(67, 33, "C", "r")
        )
        val state = ScoringEngine.createState(anchor, 100, target, 100)

        // Record 3 verdicts
        repeat(3) {
            val idx = state.nextRegionIndex ?: return
            ScoringEngine.recordVerdict(state, idx, RegionVerdict.SAME)
        }

        // Undo all
        repeat(3) { ScoringEngine.undo(state) }

        assertEquals(0, state.verdicts.size)
        assertEquals(0, state.history.size)
        assertFalse(state.isComplete)
    }
}
