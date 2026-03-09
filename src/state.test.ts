import { describe, it, expect, beforeEach, vi } from 'vitest'
import m from 'mithril'
import { S, addCluster, activeCluster, setVerdict, removeCluster, switchCluster, filteredTraces, getPositiveTraces, getNegativeTraces, recomputeCounts, renameCluster, traceKey, exportSession, importSession, importSessionData } from './state'

// Mithril's redraw requires mount() — stub it for unit tests
vi.spyOn(m, 'redraw').mockImplementation(() => {})
import type { TraceEntry } from './models/types'

function makeTrace(uuid: string, sliceCount = 3, pkg = 'com.test', startupDur = 0): TraceEntry {
  const slices = Array.from({ length: sliceCount }, (_, i) => ({
    ts: i * 1000, dur: 1000, name: null, state: 'Running', depth: null, io_wait: null, blocked_function: null,
  }))
  return { trace_uuid: uuid, package_name: pkg, startup_dur: startupDur, slices }
}

function keyOf(uuid: string, pkg = 'com.test', startupDur = 0) {
  return traceKey({ trace_uuid: uuid, package_name: pkg, startup_dur: startupDur, slices: [] })
}

function resetState() {
  S.clusters = []
  S.activeClusterId = null
  S.importMsg = null
}

describe('cluster management', () => {
  beforeEach(resetState)

  it('addCluster creates a cluster and makes it active', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    expect(S.clusters.length).toBe(1)
    expect(S.activeClusterId).toBe(S.clusters[0].id)
    expect(activeCluster()!.name).toBe('test')
    expect(activeCluster()!.traces.length).toBe(2)
  })

  it('addCluster deduplicates by composite key', () => {
    addCluster('test', [makeTrace('a'), makeTrace('a'), makeTrace('b')])
    expect(activeCluster()!.traces.length).toBe(2)
  })

  it('addCluster keeps traces with same uuid but different startup_dur', () => {
    addCluster('test', [makeTrace('a', 3, 'com.test', 100), makeTrace('a', 3, 'com.test', 200)])
    expect(activeCluster()!.traces.length).toBe(2)
  })

  it('multiple clusters are independent', () => {
    addCluster('c1', [makeTrace('a')])
    addCluster('c2', [makeTrace('b'), makeTrace('c')])
    expect(S.clusters.length).toBe(2)
    expect(activeCluster()!.name).toBe('c2')
    expect(activeCluster()!.traces.length).toBe(2)
  })

  it('removeCluster switches to remaining cluster', () => {
    addCluster('c1', [makeTrace('a')])
    const id1 = S.clusters[0].id
    addCluster('c2', [makeTrace('b')])
    removeCluster(S.clusters[1].id)
    expect(S.clusters.length).toBe(1)
    expect(S.activeClusterId).toBe(id1)
  })

  it('removeCluster with no remaining sets null', () => {
    addCluster('c1', [makeTrace('a')])
    removeCluster(S.clusters[0].id)
    expect(S.clusters.length).toBe(0)
    expect(S.activeClusterId).toBeNull()
    expect(activeCluster()).toBeNull()
  })

  it('switchCluster changes active cluster', () => {
    addCluster('c1', [makeTrace('a')])
    const id1 = S.clusters[0].id
    addCluster('c2', [makeTrace('b')])
    switchCluster(id1)
    expect(S.activeClusterId).toBe(id1)
    expect(activeCluster()!.name).toBe('c1')
  })

  it('renameCluster updates name', () => {
    addCluster('old', [makeTrace('a')])
    const id = S.clusters[0].id
    renameCluster(id, 'new name')
    expect(activeCluster()!.name).toBe('new name')
  })

  it('renameCluster ignores empty string', () => {
    addCluster('keep', [makeTrace('a')])
    const id = S.clusters[0].id
    renameCluster(id, '  ')
    expect(activeCluster()!.name).toBe('keep')
  })
})

describe('verdicts', () => {
  beforeEach(resetState)

  it('setVerdict sets and toggles verdict using composite key', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    const keyA = cl.traces[0]._key
    setVerdict(cl, keyA, 'like')
    expect(cl.verdicts.get(keyA)).toBe('like')
    // calling same verdict again should remove it
    setVerdict(cl, keyA, 'like')
    expect(cl.verdicts.has(keyA)).toBe(false)
  })

  it('setVerdict switches from positive to negative', () => {
    addCluster('test', [makeTrace('a')])
    const cl = activeCluster()!
    const keyA = cl.traces[0]._key
    setVerdict(cl, keyA, 'like')
    expect(cl.verdicts.get(keyA)).toBe('like')
    setVerdict(cl, keyA, 'dislike')
    expect(cl.verdicts.get(keyA)).toBe('dislike')
  })

  it('recomputeCounts reflects verdicts correctly', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    cl.verdicts.set(cl.traces[0]._key, 'like')
    cl.verdicts.set(cl.traces[1]._key, 'dislike')
    recomputeCounts(cl)
    expect(cl.counts.positive).toBe(1)
    expect(cl.counts.negative).toBe(1)
    expect(cl.counts.pending).toBe(1)
  })

  it('same trace_uuid with different startup_dur get independent verdicts', () => {
    addCluster('test', [makeTrace('a', 3, 'com.test', 100), makeTrace('a', 3, 'com.test', 200)])
    const cl = activeCluster()!
    expect(cl.traces.length).toBe(2)
    const key0 = cl.traces[0]._key
    const key1 = cl.traces[1]._key
    expect(key0).not.toBe(key1)
    setVerdict(cl, key0, 'like')
    expect(cl.verdicts.get(key0)).toBe('like')
    expect(cl.verdicts.has(key1)).toBe(false)
  })
})

describe('filteredTraces', () => {
  beforeEach(resetState)

  it('returns all traces for filter "all"', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    cl.overviewFilter = 'all'
    expect(filteredTraces().length).toBe(2)
  })

  it('returns only positive traces for filter "positive"', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[0]._key, 'like')
    cl.overviewFilter = 'positive'
    expect(filteredTraces().length).toBe(1)
    expect(filteredTraces()[0].trace.trace_uuid).toBe('a')
  })

  it('returns only negative traces for filter "negative"', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[1]._key, 'dislike')
    cl.overviewFilter = 'negative'
    expect(filteredTraces().length).toBe(1)
    expect(filteredTraces()[0].trace.trace_uuid).toBe('b')
  })
})

describe('getPositiveTraces / getNegativeTraces', () => {
  beforeEach(resetState)

  it('returns only positive traces', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[0]._key, 'like')
    setVerdict(cl, cl.traces[1]._key, 'dislike')
    expect(getPositiveTraces().length).toBe(1)
    expect(getPositiveTraces()[0].trace.trace_uuid).toBe('a')
  })

  it('returns only negative traces', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[0]._key, 'like')
    setVerdict(cl, cl.traces[1]._key, 'dislike')
    expect(getNegativeTraces().length).toBe(1)
    expect(getNegativeTraces()[0].trace.trace_uuid).toBe('b')
  })

  it('returns empty array when no cluster active', () => {
    expect(getPositiveTraces()).toEqual([])
    expect(getNegativeTraces()).toEqual([])
  })
})

describe('session save/restore/append', () => {
  beforeEach(() => {
    S.clusters = []
    S.activeClusterId = null
    S.importMsg = null
    S.loadProgress = null
  })

  it('exportSession captures all clusters and verdicts', () => {
    addCluster('A', [makeTrace('a1'), makeTrace('a2')])
    addCluster('B', [makeTrace('b1')])
    const cl = S.clusters[0]
    setVerdict(cl, cl.traces[0]._key, 'like')
    setVerdict(cl, cl.traces[1]._key, 'dislike')

    const json = exportSession()
    const data = JSON.parse(json)
    expect(data.version).toBe(1)
    expect(data.clusters).toHaveLength(2)
    expect(data.clusters[0].verdicts).toHaveLength(2)
  })

  it('importSession restores clusters and verdicts', () => {
    addCluster('A', [makeTrace('a1'), makeTrace('a2')])
    const cl = S.clusters[0]
    setVerdict(cl, cl.traces[0]._key, 'like')
    const json = exportSession()

    // Clear state
    S.clusters = []
    S.activeClusterId = null

    importSession(json)
    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].name).toBe('A')
    expect(S.clusters[0].verdicts.size).toBe(1)
    expect(S.clusters[0].counts.positive).toBe(1)
  })

  it('importSessionData works with pre-parsed data', () => {
    addCluster('A', [makeTrace('a1')])
    const json = exportSession()
    const data = JSON.parse(json)

    S.clusters = []
    S.activeClusterId = null

    importSessionData(data)
    expect(S.clusters).toHaveLength(1)
  })

  it('session load + file import = merged state, save captures all', () => {
    // First: create session with one cluster
    addCluster('Session', [makeTrace('s1'), makeTrace('s2')])
    const cl = S.clusters[0]
    setVerdict(cl, cl.traces[0]._key, 'like')
    const sessionJson = exportSession()

    // Clear and restore session
    S.clusters = []
    S.activeClusterId = null
    importSession(sessionJson)

    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].name).toBe('Session')

    // Now add more data (simulates file import after session load)
    addCluster('NewFile', [makeTrace('n1'), makeTrace('n2'), makeTrace('n3')])

    expect(S.clusters).toHaveLength(2)
    expect(S.clusters[0].name).toBe('Session')
    expect(S.clusters[1].name).toBe('NewFile')

    // Save captures everything
    const fullJson = exportSession()
    const fullData = JSON.parse(fullJson)
    expect(fullData.clusters).toHaveLength(2)
    expect(fullData.clusters[0].verdicts).toHaveLength(1) // verdict preserved
    expect(fullData.clusters[1].traces).toHaveLength(3)

    // Re-import the full session
    S.clusters = []
    importSession(fullJson)
    expect(S.clusters).toHaveLength(2)
    expect(S.clusters[0].counts.positive).toBe(1) // verdict still there
    expect(S.clusters[1].traces).toHaveLength(3)
  })

  it('session preserves sort, filters, split view, and global slider', () => {
    addCluster('A', [makeTrace('a1', 3, 'com.a'), makeTrace('a2', 3, 'com.b')])
    const cl = S.clusters[0]
    cl.sortField = 'startup_dur'
    cl.sortDir = -1
    cl.splitView = true
    cl.globalSlider = 42

    const json = exportSession()
    S.clusters = []
    importSession(json)

    const restored = S.clusters[0]
    expect(restored.sortField).toBe('startup_dur')
    expect(restored.sortDir).toBe(-1)
    expect(restored.splitView).toBe(true)
    expect(restored.globalSlider).toBe(42)
  })

  it('session preserves discard verdicts', () => {
    addCluster('A', [makeTrace('a1'), makeTrace('a2'), makeTrace('a3')])
    const cl = S.clusters[0]
    setVerdict(cl, cl.traces[0]._key, 'like')
    setVerdict(cl, cl.traces[1]._key, 'discard')

    const json = exportSession()
    S.clusters = []
    importSession(json)

    const restored = S.clusters[0]
    expect(restored.counts.positive).toBe(1)
    expect(restored.counts.discarded).toBe(1)
    expect(restored.counts.pending).toBe(1)
  })
})
