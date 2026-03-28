package com.swiperf.app.data.scoring

import org.junit.Assert.*
import org.junit.Test

class ScoringDictTest {

    private fun sig(vararg triples: Triple<String, String?, String?>): Set<Triple<String, String?, String?>> =
        triples.toSet()

    // ── Basic operations ──

    @Test
    fun emptyDict_sizeZero() {
        val dict = ScoringDictionary()
        assertEquals(0, dict.size)
        assertTrue(dict.all.isEmpty())
    }

    @Test
    fun addFromState_addsEntries() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        state.diffSignatures.add(sig(Triple("state", "Running", "Sleeping")))

        val dict = ScoringDictionary()
        dict.addFromState(state)
        assertEquals(2, dict.size)
    }

    @Test
    fun addFromState_deduplicates() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))

        val dict = ScoringDictionary()
        dict.addFromState(state)
        dict.addFromState(state) // same again
        assertEquals(1, dict.size)
    }

    @Test
    fun addFromState_normalizedTag() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))

        val dict = ScoringDictionary()
        dict.addFromState(state, normalized = true)
        assertTrue(dict.all[0].normalized)

        dict.addFromState(state, normalized = false)
        // Both should exist as separate entries (different normalized flag)
        assertEquals(2, dict.size)
        assertTrue(dict.all.any { it.normalized })
        assertTrue(dict.all.any { !it.normalized })
    }

    @Test
    fun remove_removesEntry() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        val dict = ScoringDictionary()
        dict.addFromState(state)
        assertEquals(1, dict.size)
        dict.remove(dict.all[0])
        assertEquals(0, dict.size)
    }

    @Test
    fun removeAll_removesMultiple() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "a", "b")))
        state.sameSignatures.add(sig(Triple("name", "c", "d")))
        state.diffSignatures.add(sig(Triple("state", "X", "Y")))
        val dict = ScoringDictionary()
        dict.addFromState(state)
        assertEquals(3, dict.size)
        dict.removeAll(dict.all.filter { it.verdict == RegionVerdict.SAME })
        assertEquals(1, dict.size)
        assertEquals(RegionVerdict.DIFFERENT, dict.all[0].verdict)
    }

    @Test
    fun clear_removesAll() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        val dict = ScoringDictionary()
        dict.addFromState(state)
        dict.clear()
        assertEquals(0, dict.size)
    }

    // ── Hit count ──

    @Test
    fun bumpHitCount_incrementsCorrectEntry() {
        val state = ScoringState(emptyList())
        val s = sig(Triple("name", "foo", "bar"))
        state.sameSignatures.add(s)
        val dict = ScoringDictionary()
        dict.addFromState(state)
        assertEquals(0, dict.all[0].hitCount)
        dict.bumpHitCount(s)
        assertEquals(1, dict.all[0].hitCount)
        dict.bumpHitCount(s)
        assertEquals(2, dict.all[0].hitCount)
    }

    @Test
    fun bumpHitCount_unknownSignature_noOp() {
        val dict = ScoringDictionary()
        dict.bumpHitCount(sig(Triple("name", "x", "y"))) // should not crash
        assertEquals(0, dict.size)
    }

    // ── Apply to state ──

    @Test
    fun applyTo_autoResolvesMatchingRegions() {
        // Dict knows name foo≈bar
        val dictState = ScoringState(emptyList())
        dictState.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        val dict = ScoringDictionary()
        dict.addFromState(dictState)

        // New scoring state with regions that match
        val regions = listOf(
            ScoringRegion(0.0, 0.5, "Running", "foo", null, null, "Running", "bar", null, null),
            ScoringRegion(0.5, 1.0, "Running", "baz", null, null, "Running", "qux", null, null),
        )
        val state = ScoringState(regions)
        dict.applyTo(state)

        // First region matches dict → auto-resolved as SAME
        assertEquals(RegionVerdict.SAME, state.verdicts[0])
        // Second region doesn't match → unresolved
        assertNull(state.verdicts[1])
        // Hit count bumped
        assertEquals(1, dict.all[0].hitCount)
    }

    @Test
    fun applyTo_differentVerdictApplied() {
        val dictState = ScoringState(emptyList())
        dictState.diffSignatures.add(sig(Triple("state", "Running", "Sleeping")))
        val dict = ScoringDictionary()
        dict.addFromState(dictState)

        val regions = listOf(
            ScoringRegion(0.0, 1.0, "Running", "foo", null, null, "Sleeping", "foo", null, null),
        )
        val state = ScoringState(regions)
        dict.applyTo(state)

        assertEquals(RegionVerdict.DIFFERENT, state.verdicts[0])
    }

    @Test
    fun applyTo_doesNotOverrideExistingVerdicts() {
        val dictState = ScoringState(emptyList())
        dictState.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        val dict = ScoringDictionary()
        dict.addFromState(dictState)

        val regions = listOf(
            ScoringRegion(0.0, 1.0, "Running", "foo", null, null, "Running", "bar", null, null),
        )
        val state = ScoringState(regions)
        state.verdicts[0] = RegionVerdict.DIFFERENT // already scored
        dict.applyTo(state)

        // Should NOT override
        assertEquals(RegionVerdict.DIFFERENT, state.verdicts[0])
    }

    // ── Serialization ──

    @Test
    fun toJson_fromJson_roundtrip() {
        val state = ScoringState(emptyList())
        state.sameSignatures.add(sig(Triple("name", "foo", "bar")))
        state.diffSignatures.add(sig(Triple("state", "Running", "Sleeping"), Triple("name", "a", "b")))
        val dict = ScoringDictionary()
        dict.addFromState(state, normalized = true)
        dict.bumpHitCount(sig(Triple("name", "foo", "bar")))

        val json = dict.toJson()
        val restored = ScoringDictionary.fromJson(json)

        assertEquals(dict.size, restored.size)
        val e1 = restored.all.find { it.verdict == RegionVerdict.SAME }!!
        assertEquals(1, e1.hitCount)
        assertTrue(e1.normalized)
        assertEquals(sig(Triple("name", "foo", "bar")), e1.signature)

        val e2 = restored.all.find { it.verdict == RegionVerdict.DIFFERENT }!!
        assertEquals(2, e2.signature.size)
        assertTrue(e2.normalized)
    }

    @Test
    fun fromJson_invalidJson_returnsEmpty() {
        val dict = ScoringDictionary.fromJson("not json")
        assertEquals(0, dict.size)
    }

    @Test
    fun fromJson_emptyArray_returnsEmpty() {
        val dict = ScoringDictionary.fromJson("[]")
        assertEquals(0, dict.size)
    }

    // ── Normalize digits ──

    @Test
    fun normalizeDigits_replacesNumbers() {
        assertEquals("foo [num]", normalizeDigits("foo 42"))
        assertEquals("binder transaction [num]", normalizeDigits("binder transaction 123"))
        assertEquals("[num]_[num]_thread", normalizeDigits("5_3_thread"))
    }

    @Test
    fun normalizeDigits_null_returnsNull() {
        assertNull(normalizeDigits(null))
    }

    @Test
    fun normalizeDigits_noDigits_unchanged() {
        assertEquals("binder transaction", normalizeDigits("binder transaction"))
    }

    // ── Display label ──

    @Test
    fun displayLabel_sameVerdict() {
        val entry = DictEntry(RegionVerdict.SAME, sig(Triple("name", "foo", "bar")))
        assertTrue(entry.displayLabel.contains("\u2248")) // ≈
        assertTrue(entry.displayLabel.contains("foo"))
        assertTrue(entry.displayLabel.contains("bar"))
    }

    @Test
    fun displayLabel_differentVerdict() {
        val entry = DictEntry(RegionVerdict.DIFFERENT, sig(Triple("state", "Running", "Sleeping")))
        assertTrue(entry.displayLabel.contains("\u2260")) // ≠
    }

    @Test
    fun displayLabel_nullValues() {
        val entry = DictEntry(RegionVerdict.SAME, sig(Triple("blocked fn", null, "function")))
        assertTrue(entry.displayLabel.contains("null"))
        assertTrue(entry.displayLabel.contains("function"))
    }
}
