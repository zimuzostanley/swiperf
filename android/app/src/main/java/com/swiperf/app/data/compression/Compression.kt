package com.swiperf.app.data.compression

import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.Slice
import kotlin.math.ln1p
import kotlin.math.max

object Compression {

    private fun tokenDistance(a: MergedSlice, b: MergedSlice): Double {
        var d = 0.0
        if (a.state != b.state) {
            d += 4.0
        } else if (a.state == "Uninterruptible Sleep" && a.ioWait != b.ioWait) {
            d += 2.0
        }
        if (a.name != b.name) {
            d += if (a.name == null || b.name == null) 1.0 else 2.0
        }
        if (a.blockedFunction != b.blockedFunction) {
            d += if (a.blockedFunction == null || b.blockedFunction == null) 0.5 else 1.0
        }
        return d
    }

    private fun mergeCost(a: MergedSlice, b: MergedSlice): Double {
        val dist = tokenDistance(a, b)
        if (dist == 0.0) {
            return 0.01 * ln1p((a.dur + b.dur) / 1e6)
        }
        val loser = if (a.dur <= b.dur) a else b
        val loserWeight = ln1p(loser.dur / 1e6) *
            (1 + (if (loser.name != null) 1 else 0) + (if (loser.blockedFunction != null) 1 else 0))
        return dist * loserWeight
    }

    private fun mergeTwo(a: MergedSlice, b: MergedSlice): MergedSlice {
        val winner = if (a.dur >= b.dur) a else b
        return MergedSlice(
            ts = a.ts,
            tsRel = a.tsRel,
            dur = a.dur + b.dur,
            state = winner.state,
            ioWait = winner.ioWait,
            name = winner.name,
            depth = winner.depth,
            blockedFunction = winner.blockedFunction,
            merged = a.merged + b.merged
        )
    }

    fun buildMergeCache(rawData: List<Slice>): Map<Int, List<MergedSlice>> {
        val cache = mutableMapOf<Int, List<MergedSlice>>()
        if (rawData.isEmpty()) return cache

        val baseTs = rawData[0].ts
        var seq = rawData.map { d ->
            MergedSlice(
                ts = d.ts, tsRel = d.ts - baseTs, dur = d.dur,
                state = d.state, ioWait = d.ioWait, name = d.name,
                depth = d.depth, blockedFunction = d.blockedFunction, merged = 1
            )
        }.toMutableList()
        cache[seq.size] = seq.toList()

        while (seq.size > 2) {
            var bestI = 0
            var bestCost = Double.MAX_VALUE
            for (i in 0 until seq.size - 1) {
                val c = mergeCost(seq[i], seq[i + 1])
                if (c < bestCost) { bestCost = c; bestI = i }
            }
            val merged = mergeTwo(seq[bestI], seq[bestI + 1])
            seq.removeAt(bestI + 1)
            seq[bestI] = merged
            if (!cache.containsKey(seq.size)) {
                cache[seq.size] = seq.toList()
            }
        }
        return cache
    }

    fun getCompressed(cache: Map<Int, List<MergedSlice>>, origN: Int, target: Int): List<MergedSlice> {
        val t = max(2, target)
        var best = origN
        for (k in cache.keys) {
            if (k >= t && k < best) best = k
        }
        return cache[best] ?: cache[2] ?: emptyList()
    }
}
