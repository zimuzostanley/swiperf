package com.swiperf.app.data.compare

import org.junit.Assert.*
import org.junit.Test

class UnionFindTest {

    @Test
    fun findReturnsKey() {
        val uf = UnionFind(listOf("a", "b", "c"))
        assertEquals("a", uf.find("a"))
    }

    @Test
    fun unionMerges() {
        val uf = UnionFind(listOf("a", "b", "c"))
        uf.union("a", "b")
        assertTrue(uf.connected("a", "b"))
        assertFalse(uf.connected("a", "c"))
    }

    @Test
    fun componentsGroupsCorrectly() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")
        val comps = uf.components()
        assertEquals(2, comps.size)
    }

    @Test
    fun transitiveUnion() {
        val uf = UnionFind(listOf("a", "b", "c"))
        uf.union("a", "b")
        uf.union("b", "c")
        assertTrue(uf.connected("a", "c"))
    }
}
