package com.swiperf.app.data.compare

enum class ComparisonResult { POSITIVE, NEGATIVE }
enum class Side { LEFT, RIGHT }

sealed class HistoryEntry {
    data class Compare(val keyA: String, val keyB: String, val result: ComparisonResult) : HistoryEntry()
    data class Discard(val key: String) : HistoryEntry()
}

data class CrossCompareProgress(val completed: Int, val total: Int, val pct: Int)

data class CrossCompareResults(
    val groups: List<List<String>>,
    val discarded: List<String>
)

class CrossCompareState(
    var uf: UnionFind,
    val negativeEdges: MutableSet<String> = mutableSetOf(),
    val comparisons: MutableMap<String, ComparisonResult> = mutableMapOf(),
    val skippedPairs: MutableSet<String> = mutableSetOf(),
    val discardedKeys: MutableSet<String> = mutableSetOf(),
    val history: MutableList<HistoryEntry> = mutableListOf(),
    val traceKeys: List<String>,
    var currentPair: Pair<String, String>? = null,
    var selectedSide: Side? = null,
    var isComplete: Boolean = false
)

object CrossCompare {

    fun edgeKey(a: String, b: String): String = if (a < b) "$a|$b" else "$b|$a"

    fun createState(traceKeys: List<String>): CrossCompareState {
        val state = CrossCompareState(
            uf = UnionFind(traceKeys),
            traceKeys = traceKeys
        )
        state.currentPair = nextPair(state)
        if (state.currentPair == null) state.isComplete = true
        return state
    }

    fun recordComparison(state: CrossCompareState, keyA: String, keyB: String, result: ComparisonResult) {
        val ek = edgeKey(keyA, keyB)
        state.comparisons[ek] = result
        state.skippedPairs.remove(ek)
        state.history.add(HistoryEntry.Compare(keyA, keyB, result))

        if (result == ComparisonResult.POSITIVE) {
            val rootA = state.uf.find(keyA); val rootB = state.uf.find(keyB)
            val toRekey = mutableListOf<Pair<String, String>>()
            for (neg in state.negativeEdges) {
                val parts = neg.split("|")
                val x = parts[0]; val y = parts[1]
                if (x == rootA || x == rootB || y == rootA || y == rootB) {
                    toRekey.add(x to y)
                }
            }
            state.uf.union(keyA, keyB)
            for ((x, y) in toRekey) {
                state.negativeEdges.remove(edgeKey(x, y))
                val rx = state.uf.find(x); val ry = state.uf.find(y)
                if (rx != ry) state.negativeEdges.add(edgeKey(rx, ry))
            }
        } else {
            val rootA = state.uf.find(keyA); val rootB = state.uf.find(keyB)
            if (rootA != rootB) state.negativeEdges.add(edgeKey(rootA, rootB))
        }
    }

    fun skipCurrentPair(state: CrossCompareState) {
        val pair = state.currentPair ?: return
        state.skippedPairs.add(edgeKey(pair.first, pair.second))
    }

    fun discardTrace(state: CrossCompareState, key: String) {
        state.discardedKeys.add(key)
        state.history.add(HistoryEntry.Discard(key))
    }

    fun undoComparison(state: CrossCompareState) {
        if (state.history.isEmpty()) return
        val prev = state.history.toList().dropLast(1)
        state.uf = UnionFind(state.traceKeys)
        state.negativeEdges.clear()
        state.comparisons.clear()
        state.skippedPairs.clear()
        state.discardedKeys.clear()
        state.history.clear()
        for (entry in prev) {
            when (entry) {
                is HistoryEntry.Compare -> recordComparison(state, entry.keyA, entry.keyB, entry.result)
                is HistoryEntry.Discard -> discardTrace(state, entry.key)
            }
        }
        state.currentPair = nextPair(state)
        state.isComplete = state.currentPair == null
        state.selectedSide = null
    }

    private fun activeComponents(state: CrossCompareState): Map<String, List<String>> {
        val comps = state.uf.components()
        if (state.discardedKeys.isEmpty()) return comps
        val filtered = mutableMapOf<String, List<String>>()
        for ((root, members) in comps) {
            val active = members.filter { it !in state.discardedKeys }
            if (active.isNotEmpty()) filtered[root] = active
        }
        return filtered
    }

    fun nextPair(state: CrossCompareState): Pair<String, String>? {
        val comps = activeComponents(state)
        val sorted = comps.entries.sortedByDescending { it.value.size }

        var fallback: Pair<String, String>? = null
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val rootI = sorted[i].key; val rootJ = sorted[j].key
                if (state.negativeEdges.contains(edgeKey(rootI, rootJ))) continue
                val pair = findUncomparedPair(state, sorted[i].value, sorted[j].value, state.skippedPairs)
                if (pair != null) return pair
                if (fallback == null) {
                    fallback = findUncomparedPair(state, sorted[i].value, sorted[j].value)
                }
            }
        }
        return fallback
    }

    fun nextPairForAnchor(state: CrossCompareState, anchorKey: String): Pair<String, String>? {
        if (anchorKey in state.discardedKeys) return null
        val comps = activeComponents(state)
        val anchorRoot = state.uf.find(anchorKey)
        val sorted = comps.entries
            .filter { it.key != anchorRoot }
            .sortedByDescending { it.value.size }

        var fallback: Pair<String, String>? = null
        for ((root, members) in sorted) {
            if (state.negativeEdges.contains(edgeKey(anchorRoot, root))) continue
            for (other in members) {
                val ek = edgeKey(anchorKey, other)
                if (!state.comparisons.containsKey(ek) && !state.skippedPairs.contains(ek)) {
                    return anchorKey to other
                }
                if (fallback == null && !state.comparisons.containsKey(ek)) {
                    fallback = anchorKey to other
                }
            }
        }
        return fallback
    }

    private fun findUncomparedPair(
        state: CrossCompareState,
        membersA: List<String>,
        membersB: List<String>,
        exclude: Set<String>? = null
    ): Pair<String, String>? {
        for (a in membersA) {
            for (b in membersB) {
                val ek = edgeKey(a, b)
                if (!state.comparisons.containsKey(ek) && (exclude == null || !exclude.contains(ek))) {
                    return a to b
                }
            }
        }
        return null
    }

    fun getProgress(state: CrossCompareState): CrossCompareProgress {
        val comps = activeComponents(state)
        val roots = comps.keys.toList()
        var total = 0; var resolved = 0
        for (i in roots.indices) {
            for (j in i + 1 until roots.size) {
                total++
                if (state.negativeEdges.contains(edgeKey(roots[i], roots[j]))) resolved++
            }
        }
        val n = state.traceKeys.size - state.discardedKeys.size
        val maxPairs = n * (n - 1) / 2
        val mergedPairs = maxPairs - total
        val totalWork = maxPairs
        val completedWork = mergedPairs + resolved
        val pct = if (totalWork > 0) Math.round(completedWork.toDouble() / totalWork * 100).toInt() else 100
        return CrossCompareProgress(completedWork, totalWork, pct)
    }

    fun getResults(state: CrossCompareState): CrossCompareResults {
        val comps = activeComponents(state)
        val groups = comps.values.sortedByDescending { it.size }
        return CrossCompareResults(groups, state.discardedKeys.toList())
    }
}
