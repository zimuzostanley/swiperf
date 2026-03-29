package com.swiperf.app.data.scoring

import com.swiperf.app.data.model.TraceState

data class GlobalScoringEntry(
    val signature: Set<Triple<String, String?, String?>>,
    val anchorState: String?, val anchorName: String?, val anchorIoWait: Int?, val anchorBlockedFn: String?,
    val targetState: String?, val targetName: String?, val targetIoWait: Int?, val targetBlockedFn: String?,
    val traceCount: Int,
    val totalDurationPct: Double, // average % across all traces
    var verdict: RegionVerdict? = null
)

class GlobalScoringState(
    val perTrace: Map<String, ScoringState>, // traceKey -> scoring state
    val entries: List<GlobalScoringEntry>,
    val totalTraces: Int
) {
    val nextEntryIndex: Int?
        get() = entries.indices.firstOrNull { entries[it].verdict == null }

    val isComplete: Boolean get() = nextEntryIndex == null

    val resolvedCount: Int get() = entries.count { it.verdict != null }

    // Average score across all traces (samePct)
    val avgScore: Int get() {
        if (perTrace.isEmpty()) return 0
        return perTrace.values.map { it.breakdown.samePct }.average().toInt()
    }
}

object GlobalScoring {
    fun createState(
        anchorTrace: TraceState,
        targets: List<TraceState>,
        normalize: Boolean = false,
        dict: ScoringDictionary? = null
    ): GlobalScoringState {
        anchorTrace.ensureCache()
        val perTrace = mutableMapOf<String, ScoringState>()

        for (target in targets) {
            target.ensureCache()
            val state = ScoringEngine.createStateFromMerged(
                anchorTrace.currentSeq, anchorTrace.totalDur,
                target.currentSeq, target.totalDur,
                normalize = normalize
            )
            dict?.applyTo(state)
            perTrace[target.key] = state
        }

        // Collect unique signatures across all traces
        val sigMap = mutableMapOf<Set<Triple<String, String?, String?>>, MutableList<Pair<String, ScoringRegion>>>()
        for ((traceKey, state) in perTrace) {
            for (i in state.regions.indices) {
                val r = state.regions[i]
                if (r.isAutoSame || state.verdicts.containsKey(i)) continue
                val sig = r.diffSignature
                sigMap.getOrPut(sig) { mutableListOf() }.add(traceKey to r)
            }
        }

        // Build entries sorted by trace count desc, then total duration desc
        val entries = sigMap.map { (sig, regions) ->
            val first = regions[0].second
            val avgDur = regions.map { it.second.duration * 100 }.average()
            GlobalScoringEntry(
                signature = sig,
                anchorState = first.anchorState, anchorName = first.anchorName,
                anchorIoWait = first.anchorIoWait, anchorBlockedFn = first.anchorBlockedFn,
                targetState = first.targetState, targetName = first.targetName,
                targetIoWait = first.targetIoWait, targetBlockedFn = first.targetBlockedFn,
                traceCount = regions.map { it.first }.distinct().size,
                totalDurationPct = avgDur
            )
        }.sortedWith(compareByDescending<GlobalScoringEntry> { it.traceCount }.thenByDescending { it.totalDurationPct })

        return GlobalScoringState(perTrace, entries, targets.size)
    }

    fun recordVerdict(state: GlobalScoringState, entryIndex: Int, verdict: RegionVerdict) {
        val entry = state.entries[entryIndex]
        entry.verdict = verdict

        // Apply to all per-trace states that have this signature
        for ((_, traceState) in state.perTrace) {
            for (i in traceState.regions.indices) {
                if (traceState.verdicts.containsKey(i) || traceState.regions[i].isAutoSame) continue
                if (traceState.regions[i].diffSignature == entry.signature) {
                    traceState.verdicts[i] = verdict
                }
            }
        }
    }

    fun undo(state: GlobalScoringState) {
        // Find last scored entry and revert
        val lastIdx = state.entries.indices.lastOrNull { state.entries[it].verdict != null } ?: return
        val entry = state.entries[lastIdx]
        val sig = entry.signature
        entry.verdict = null

        // Revert in all per-trace states
        for ((_, traceState) in state.perTrace) {
            for (i in traceState.regions.indices) {
                if (traceState.regions[i].isAutoSame) continue
                if (traceState.regions[i].diffSignature == sig) {
                    traceState.verdicts.remove(i)
                }
            }
        }
    }
}
