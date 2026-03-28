package com.swiperf.app.data.scoring

import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.Slice

/**
 * A proportional time region with the dominant anchor and target slice info.
 * start/end are proportional (0.0 to 1.0) of the total trace duration.
 */
data class ScoringRegion(
    val start: Double,
    val end: Double,
    val anchorState: String?,
    val anchorName: String?,
    val anchorIoWait: Int?,
    val anchorBlockedFn: String?,
    val targetState: String?,
    val targetName: String?,
    val targetIoWait: Int?,
    val targetBlockedFn: String?,
) {
    val duration: Double get() = end - start

    /** Which fields differ between anchor and target. */
    val diffs: List<FieldDiff> by lazy {
        buildList {
            if (anchorState != targetState) add(FieldDiff("state", anchorState, targetState))
            if (anchorName != targetName) add(FieldDiff("name", anchorName, targetName))
            if (anchorIoWait != targetIoWait) add(FieldDiff("io_wait", anchorIoWait?.toString(), targetIoWait?.toString()))
            if (anchorBlockedFn != targetBlockedFn) add(FieldDiff("blocked_fn", anchorBlockedFn, targetBlockedFn))
        }
    }

    val isAutoSame: Boolean get() = diffs.isEmpty()

    /**
     * Signature of the diff: the exact set of (field, anchorVal, targetVal) tuples.
     * Two regions with the same diffSignature are structurally identical in what differs.
     */
    val diffSignature: Set<Triple<String, String?, String?>> by lazy {
        diffs.map { Triple(it.field, it.anchorVal, it.targetVal) }.toSet()
    }
}

data class FieldDiff(val field: String, val anchorVal: String?, val targetVal: String?)

enum class RegionVerdict { SAME, DIFFERENT, SKIPPED }

data class ScoringAction(
    val regionIndex: Int,
    val verdict: RegionVerdict,
    /** The diff signature that was learned from this verdict. */
    val learnedSignature: Set<Triple<String, String?, String?>>?,
    /** Indices of other regions auto-resolved by the learned signature. */
    val cascadedIndices: List<Int>
)

/**
 * Mutable scoring state. Holds all regions, verdicts, equivalences, and history.
 */
class ScoringState(
    val regions: List<ScoringRegion>,
    val verdicts: MutableMap<Int, RegionVerdict> = mutableMapOf(),
    val sameSignatures: MutableSet<Set<Triple<String, String?, String?>>> = mutableSetOf(),
    val diffSignatures: MutableSet<Set<Triple<String, String?, String?>>> = mutableSetOf(),
    val history: MutableList<ScoringAction> = mutableListOf()
) {
    // Queue: differing region indices sorted by time position (left to right)
    private val queue = java.util.TreeSet<Int>(compareBy<Int> { regions.getOrNull(it)?.start ?: 0.0 }.thenBy { it })

    // Signature → region indices map (built once, used for O(1) cascade)
    val sigIndex: Map<Set<Triple<String, String?, String?>>, List<Int>>

    init {
        val idx = HashMap<Set<Triple<String, String?, String?>>, MutableList<Int>>()
        for (i in regions.indices) {
            if (!regions[i].isAutoSame) {
                queue.add(i)
                idx.getOrPut(regions[i].diffSignature) { mutableListOf() }.add(i)
            }
        }
        sigIndex = idx
    }

    internal fun removeFromQueue(idx: Int) { queue.remove(idx) }
    internal fun addToQueue(idx: Int) { if (!regions[idx].isAutoSame) queue.add(idx) }

    /** Index of the next region to show the user, or null if done. O(1). */
    val nextRegionIndex: Int?
        get() = queue.firstOrNull { it !in verdicts }

    val isComplete: Boolean get() = nextRegionIndex == null

    /** Current score: (auto_same + user_same) / (scored total). NaN if nothing scored. */
    val score: Double get() {
        var sameDur = 0.0
        var scoredDur = 0.0
        for ((i, r) in regions.withIndex()) {
            if (r.isAutoSame) {
                sameDur += r.duration
                scoredDur += r.duration
            } else {
                when (verdicts[i]) {
                    RegionVerdict.SAME -> { sameDur += r.duration; scoredDur += r.duration }
                    RegionVerdict.DIFFERENT -> { scoredDur += r.duration }
                    RegionVerdict.SKIPPED -> { /* excluded */ }
                    null -> { /* unscored, excluded */ }
                }
            }
        }
        return if (scoredDur > 0) sameDur / scoredDur else Double.NaN
    }

    /** Differing regions resolved (for progress bar). */
    val differingResolved: Int get() = verdicts.size
    val differingTotal: Int get() = regions.count { !it.isAutoSame }
}

object ScoringEngine {

    /**
     * Build the unified interval grid from two traces' slices.
     * Uses proportional time (0.0 to 1.0) based on each trace's totalDur.
     */
    fun buildRegions(anchorSlices: List<Slice>, anchorTotalDur: Long,
                     targetSlices: List<Slice>, targetTotalDur: Long,
                     normalize: Boolean = false): List<ScoringRegion> {
        if (anchorSlices.isEmpty() || targetSlices.isEmpty()) return emptyList()
        if (anchorTotalDur == 0L || targetTotalDur == 0L) return emptyList()

        val anchorBaseTs = anchorSlices[0].ts
        val targetBaseTs = targetSlices[0].ts

        data class PropSlice(val start: Double, val end: Double, val state: String?,
                             val name: String?, val ioWait: Int?, val blockedFn: String?)

        val anchorProps = anchorSlices.map { s ->
            val start = (s.ts - anchorBaseTs).toDouble() / anchorTotalDur
            val normName = if (normalize) normalizeDigits(s.name) else s.name
            val normBf = if (normalize) normalizeDigits(s.blockedFunction) else s.blockedFunction
            PropSlice(start, (start + s.dur.toDouble() / anchorTotalDur).coerceAtMost(1.0),
                s.state, normName, s.ioWait, normBf)
        }
        val targetProps = targetSlices.map { s ->
            val start = (s.ts - targetBaseTs).toDouble() / targetTotalDur
            val normName = if (normalize) normalizeDigits(s.name) else s.name
            val normBf = if (normalize) normalizeDigits(s.blockedFunction) else s.blockedFunction
            PropSlice(start, (start + s.dur.toDouble() / targetTotalDur).coerceAtMost(1.0),
                s.state, normName, s.ioWait, normBf)
        }

        // Collect all boundaries
        val boundaries = sortedSetOf(0.0, 1.0)
        for (s in anchorProps) { boundaries.add(s.start); boundaries.add(s.end) }
        for (s in targetProps) { boundaries.add(s.start); boundaries.add(s.end) }

        val boundaryList = boundaries.toList()
        val regions = mutableListOf<ScoringRegion>()

        for (i in 0 until boundaryList.size - 1) {
            val start = boundaryList[i]
            val end = boundaryList[i + 1]
            if (end - start < 1e-12) continue

            val mid = (start + end) / 2
            val anchor = anchorProps.find { mid >= it.start && mid < it.end }
            val target = targetProps.find { mid >= it.start && mid < it.end }

            regions.add(ScoringRegion(
                start, end,
                anchor?.state, anchor?.name, anchor?.ioWait, anchor?.blockedFn,
                target?.state, target?.name, target?.ioWait, target?.blockedFn,
            ))
        }

        return mergeAdjacentSame(regions)
    }

    /** Build regions from compressed (merged) slices. */
    fun buildRegionsFromMerged(anchorSlices: List<MergedSlice>, anchorTotalDur: Long,
                               targetSlices: List<MergedSlice>, targetTotalDur: Long,
                               normalize: Boolean = false): List<ScoringRegion> {
        // Convert MergedSlice to Slice for reuse
        val toSlice = { m: MergedSlice -> Slice(m.ts, m.dur, m.name, m.state, m.depth, m.ioWait, m.blockedFunction) }
        return buildRegions(anchorSlices.map(toSlice), anchorTotalDur, targetSlices.map(toSlice), targetTotalDur, normalize)
    }

    fun createStateFromMerged(anchorSlices: List<MergedSlice>, anchorTotalDur: Long,
                              targetSlices: List<MergedSlice>, targetTotalDur: Long,
                              normalize: Boolean = false): ScoringState {
        return ScoringState(buildRegionsFromMerged(anchorSlices, anchorTotalDur, targetSlices, targetTotalDur, normalize))
    }

    private fun mergeAdjacentSame(regions: List<ScoringRegion>): List<ScoringRegion> {
        if (regions.isEmpty()) return emptyList()
        val merged = mutableListOf(regions[0])
        for (i in 1 until regions.size) {
            val prev = merged.last()
            val curr = regions[i]
            if (prev.anchorState == curr.anchorState && prev.anchorName == curr.anchorName &&
                prev.anchorIoWait == curr.anchorIoWait && prev.anchorBlockedFn == curr.anchorBlockedFn &&
                prev.targetState == curr.targetState && prev.targetName == curr.targetName &&
                prev.targetIoWait == curr.targetIoWait && prev.targetBlockedFn == curr.targetBlockedFn) {
                merged[merged.lastIndex] = prev.copy(end = curr.end)
            } else {
                merged.add(curr)
            }
        }
        return merged
    }

    /** Create initial scoring state from two traces. */
    fun createState(anchorSlices: List<Slice>, anchorTotalDur: Long,
                    targetSlices: List<Slice>, targetTotalDur: Long,
                    normalize: Boolean = false): ScoringState {
        return ScoringState(buildRegions(anchorSlices, anchorTotalDur, targetSlices, targetTotalDur, normalize))
    }

    /**
     * Record a user verdict. Learns the exact diff signature and cascades to
     * other regions with the identical signature. O(cascade_count).
     */
    fun recordVerdict(state: ScoringState, regionIndex: Int, verdict: RegionVerdict): ScoringAction {
        state.verdicts[regionIndex] = verdict
        val region = state.regions[regionIndex]
        val cascaded = mutableListOf<Int>()
        var learnedSig: Set<Triple<String, String?, String?>>? = null

        if (verdict != RegionVerdict.SKIPPED && region.diffs.isNotEmpty()) {
            val sig = region.diffSignature
            learnedSig = sig

            if (verdict == RegionVerdict.SAME) state.sameSignatures.add(sig)
            else state.diffSignatures.add(sig)

            // Cascade via cached sig index — O(matching_count)
            val matching = state.sigIndex[sig] ?: emptyList()
            for (i in matching) {
                if (i == regionIndex || state.verdicts.containsKey(i)) continue
                state.verdicts[i] = verdict
                cascaded.add(i)
            }
        }

        val action = ScoringAction(regionIndex, verdict, learnedSig, cascaded)
        state.history.add(action)
        return action
    }

    /** Undo the last action. */
    fun undo(state: ScoringState) {
        if (state.history.isEmpty()) return
        val action = state.history.removeAt(state.history.lastIndex)

        // Revert cascaded
        for (i in action.cascadedIndices) state.verdicts.remove(i)

        // Revert main verdict
        state.verdicts.remove(action.regionIndex)

        // Remove learned signature
        action.learnedSignature?.let { sig ->
            if (action.verdict == RegionVerdict.SAME) state.sameSignatures.remove(sig)
            else if (action.verdict == RegionVerdict.DIFFERENT) state.diffSignatures.remove(sig)
        }
    }
}
