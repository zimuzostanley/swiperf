package com.swiperf.app.data.compare

class UnionFind(keys: List<String>) {
    private val parent = mutableMapOf<String, String>()
    private val rank = mutableMapOf<String, Int>()

    init {
        for (k in keys) {
            parent[k] = k
            rank[k] = 0
        }
    }

    fun find(x: String): String {
        var root = x
        while (parent[root] != root) root = parent[root]!!
        // Path compression
        var cur = x
        while (cur != root) {
            val next = parent[cur]!!
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: String, b: String) {
        val ra = find(a); val rb = find(b)
        if (ra == rb) return
        val rankA = rank[ra]!!; val rankB = rank[rb]!!
        if (rankA < rankB) parent[ra] = rb
        else if (rankA > rankB) parent[rb] = ra
        else { parent[rb] = ra; rank[ra] = rankA + 1 }
    }

    fun connected(a: String, b: String): Boolean = find(a) == find(b)

    fun components(): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        for (k in parent.keys) {
            val root = find(k)
            map.getOrPut(root) { mutableListOf() }.add(k)
        }
        return map
    }
}
