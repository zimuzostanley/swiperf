import { describe, it, expect, beforeEach, vi } from 'vitest'
import m from 'mithril'
import { S, addCluster, activeCluster, currentTrace, navigate, setVerdict, removeCluster, switchCluster, filteredTraces, getLikedTraces, recomputeCounts, renameCluster, jumpTo } from './state'

// Mithril's redraw requires mount() — stub it for unit tests
vi.spyOn(m, 'redraw').mockImplementation(() => {})
import type { TraceEntry } from './models/types'

function makeTrace(uuid: string, sliceCount = 3): TraceEntry {
  const slices = Array.from({ length: sliceCount }, (_, i) => ({
    ts: i * 1000, dur: 1000, name: null, state: 'Running', depth: null, io_wait: null, blocked_function: null,
  }))
  return { trace_uuid: uuid, package_name: 'com.test', startup_dur: 0, slices }
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

describe('navigation', () => {
  beforeEach(resetState)

  it('navigate moves currentIndex within bounds', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    expect(cl.currentIndex).toBe(0)
    navigate(1)
    expect(cl.currentIndex).toBe(1)
    navigate(1)
    expect(cl.currentIndex).toBe(2)
    navigate(1) // should clamp
    expect(cl.currentIndex).toBe(2)
    navigate(-1)
    expect(cl.currentIndex).toBe(1)
    navigate(-10) // should clamp to 0
    expect(cl.currentIndex).toBe(0)
  })

  it('jumpTo sets index and switches to single view', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    cl.viewMode = 'overview'
    jumpTo(1)
    expect(cl.currentIndex).toBe(1)
    expect(cl.viewMode).toBe('single')
  })

  it('jumpTo ignores out-of-range index', () => {
    addCluster('test', [makeTrace('a')])
    jumpTo(5)
    expect(activeCluster()!.currentIndex).toBe(0)
    jumpTo(-1)
    expect(activeCluster()!.currentIndex).toBe(0)
  })
})

describe('verdicts', () => {
  beforeEach(resetState)

  it('setVerdict toggles on second call', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    setVerdict('like')
    expect(cl.verdicts.get('a')).toBe('like')
    // calling same verdict again should remove it
    cl.currentIndex = 0 // reset (auto-advance may have moved it)
    setVerdict('like')
    expect(cl.verdicts.has('a')).toBe(false)
  })

  it('recomputeCounts reflects verdicts correctly', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    cl.verdicts.set('a', 'like')
    cl.verdicts.set('b', 'dislike')
    recomputeCounts(cl)
    expect(cl.counts.liked).toBe(1)
    expect(cl.counts.disliked).toBe(1)
    expect(cl.counts.pending).toBe(1)
  })

  it('auto-advance moves to next pending trace', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    cl.autoAdvance = true
    setVerdict('like') // likes 'a', should auto-advance to 'b'
    expect(cl.currentIndex).toBe(1)
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

  it('returns only liked traces for filter "liked"', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const cl = activeCluster()!
    cl.verdicts.set('a', 'like')
    cl.overviewFilter = 'liked'
    expect(filteredTraces().length).toBe(1)
    expect(filteredTraces()[0].trace.trace_uuid).toBe('a')
  })

  it('returns pending traces correctly', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    cl.verdicts.set('a', 'like')
    cl.overviewFilter = 'pending'
    expect(filteredTraces().length).toBe(2)
  })
})

describe('getLikedTraces', () => {
  beforeEach(resetState)

  it('returns only liked traces', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b'), makeTrace('c')])
    const cl = activeCluster()!
    cl.verdicts.set('a', 'like')
    cl.verdicts.set('b', 'dislike')
    const liked = getLikedTraces()
    expect(liked.length).toBe(1)
    expect(liked[0].trace.trace_uuid).toBe('a')
  })

  it('returns empty array when no cluster active', () => {
    expect(getLikedTraces()).toEqual([])
  })
})

describe('currentTrace', () => {
  beforeEach(resetState)

  it('returns null when no cluster', () => {
    expect(currentTrace()).toBeNull()
  })

  it('returns the trace at currentIndex', () => {
    addCluster('test', [makeTrace('a'), makeTrace('b')])
    const ts = currentTrace()!
    expect(ts.trace.trace_uuid).toBe('a')
  })
})
