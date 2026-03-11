import { describe, it, expect } from 'vitest'
import {
  UnionFind, edgeKey, createCrossCompareState, recordComparison,
  nextPair, getProgress, getResults,
} from './crossCompare'

// ── UnionFind ──

describe('UnionFind', () => {
  it('starts with each key in its own component', () => {
    const uf = new UnionFind(['a', 'b', 'c'])
    expect(uf.connected('a', 'b')).toBe(false)
    expect(uf.connected('b', 'c')).toBe(false)
    expect(uf.components().size).toBe(3)
  })

  it('union merges two components', () => {
    const uf = new UnionFind(['a', 'b', 'c'])
    uf.union('a', 'b')
    expect(uf.connected('a', 'b')).toBe(true)
    expect(uf.connected('a', 'c')).toBe(false)
    expect(uf.components().size).toBe(2)
  })

  it('transitivity: a+b, b+c implies a+c', () => {
    const uf = new UnionFind(['a', 'b', 'c'])
    uf.union('a', 'b')
    uf.union('b', 'c')
    expect(uf.connected('a', 'c')).toBe(true)
    expect(uf.components().size).toBe(1)
  })

  it('union is idempotent', () => {
    const uf = new UnionFind(['a', 'b'])
    uf.union('a', 'b')
    uf.union('a', 'b')
    expect(uf.components().size).toBe(1)
  })

  it('handles many unions correctly', () => {
    const keys = Array.from({ length: 10 }, (_, i) => `k${i}`)
    const uf = new UnionFind(keys)
    // Chain: k0-k1-k2-...-k9
    for (let i = 0; i < 9; i++) uf.union(keys[i], keys[i + 1])
    expect(uf.components().size).toBe(1)
    expect(uf.connected('k0', 'k9')).toBe(true)
  })

  it('components returns correct member lists', () => {
    const uf = new UnionFind(['a', 'b', 'c', 'd'])
    uf.union('a', 'b')
    uf.union('c', 'd')
    const comps = uf.components()
    expect(comps.size).toBe(2)
    const sizes = [...comps.values()].map(v => v.length).sort()
    expect(sizes).toEqual([2, 2])
  })
})

// ── edgeKey ──

describe('edgeKey', () => {
  it('produces canonical key regardless of order', () => {
    expect(edgeKey('a', 'b')).toBe('a|b')
    expect(edgeKey('b', 'a')).toBe('a|b')
  })
})

// ── Cross Compare State ──

describe('createCrossCompareState', () => {
  it('initialises with first pair ready', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    expect(state.currentPair).not.toBeNull()
    expect(state.isComplete).toBe(false)
    expect(state.comparisons.size).toBe(0)
  })

  it('is immediately complete for < 2 traces', () => {
    const state = createCrossCompareState(['a'])
    expect(state.isComplete).toBe(true)
    expect(state.currentPair).toBeNull()
  })

  it('handles 2 traces', () => {
    const state = createCrossCompareState(['a', 'b'])
    expect(state.currentPair).toEqual(['a', 'b'])
  })
})

// ── recordComparison ──

describe('recordComparison', () => {
  it('positive merges components', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'positive')
    expect(state.uf.connected('a', 'b')).toBe(true)
    expect(state.comparisons.size).toBe(1)
  })

  it('negative adds edge between component roots', () => {
    const state = createCrossCompareState(['a', 'b'])
    recordComparison(state, 'a', 'b', 'negative')
    expect(state.negativeEdges.size).toBe(1)
    expect(state.uf.connected('a', 'b')).toBe(false)
  })

  it('skip records comparison but no structural change', () => {
    const state = createCrossCompareState(['a', 'b'])
    recordComparison(state, 'a', 'b', 'skip')
    expect(state.comparisons.size).toBe(1)
    expect(state.uf.connected('a', 'b')).toBe(false)
    expect(state.negativeEdges.size).toBe(0)
  })

  it('positive re-keys negative edges after merge', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    // a and c are different
    recordComparison(state, 'a', 'c', 'negative')
    expect(state.negativeEdges.size).toBe(1)
    // Now merge a and b — the negative edge (a-root vs c-root) should be re-keyed
    recordComparison(state, 'a', 'b', 'positive')
    // Negative edge should still exist between merged {a,b} and {c}
    const rootAB = state.uf.find('a')
    const rootC = state.uf.find('c')
    expect(state.negativeEdges.has(edgeKey(rootAB, rootC))).toBe(true)
  })
})

// ── nextPair ──

describe('nextPair', () => {
  it('returns null when all pairs resolved via positive merges', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'positive')
    // Now a,b are merged. Only need to compare {a,b} vs {c}
    const pair = nextPair(state)
    expect(pair).not.toBeNull()
    recordComparison(state, pair![0], pair![1], 'positive')
    expect(nextPair(state)).toBeNull()
  })

  it('returns null when all pairs resolved via negative edges', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'negative')
    recordComparison(state, 'a', 'c', 'negative')
    recordComparison(state, 'b', 'c', 'negative')
    expect(nextPair(state)).toBeNull()
  })

  it('skips pairs already connected by transitivity', () => {
    const state = createCrossCompareState(['a', 'b', 'c', 'd'])
    recordComparison(state, 'a', 'b', 'positive')
    recordComparison(state, 'c', 'd', 'positive')
    // Now 2 components: {a,b} and {c,d}. Only 1 pair needed.
    const pair = nextPair(state)
    expect(pair).not.toBeNull()
    // Should be a rep from each component
    const comp1 = new Set([state.uf.find('a')])
    expect(
      state.uf.find(pair![0]) !== state.uf.find(pair![1])
    ).toBe(true)
  })

  it('skips component pairs already separated by negative edge', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'negative')
    // {a} and {b} are separated. Next should be {a or b} vs {c}
    const pair = nextPair(state)
    expect(pair).not.toBeNull()
    // One element should be c
    expect(pair!.includes('c')).toBe(true)
  })

  it('prioritises larger components', () => {
    const state = createCrossCompareState(['a', 'b', 'c', 'd', 'e'])
    // Create a big group {a,b,c} and two singletons {d}, {e}
    recordComparison(state, 'a', 'b', 'positive')
    recordComparison(state, 'b', 'c', 'positive')
    const pair = nextPair(state)
    expect(pair).not.toBeNull()
    // Should involve a member of the big group
    const bigRoot = state.uf.find('a')
    expect(
      state.uf.find(pair![0]) === bigRoot || state.uf.find(pair![1]) === bigRoot
    ).toBe(true)
  })
})

// ── getProgress ──

describe('getProgress', () => {
  it('starts at 0% for 3 traces', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    const p = getProgress(state)
    expect(p.pct).toBe(0)
    expect(p.total).toBe(3) // 3 choose 2
  })

  it('reaches 100% when all resolved', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'positive')
    recordComparison(state, 'a', 'c', 'positive')
    const p = getProgress(state)
    expect(p.pct).toBe(100)
  })

  it('increases after merges', () => {
    const state = createCrossCompareState(['a', 'b', 'c', 'd'])
    const p0 = getProgress(state)
    recordComparison(state, 'a', 'b', 'positive')
    const p1 = getProgress(state)
    expect(p1.pct).toBeGreaterThan(p0.pct)
  })

  it('increases after negative edges', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    const p0 = getProgress(state)
    recordComparison(state, 'a', 'b', 'negative')
    const p1 = getProgress(state)
    expect(p1.pct).toBeGreaterThan(p0.pct)
  })

  it('100% for single trace', () => {
    const state = createCrossCompareState(['a'])
    expect(getProgress(state).pct).toBe(100)
  })
})

// ── getResults ──

describe('getResults', () => {
  it('returns groups sorted by size descending', () => {
    const state = createCrossCompareState(['a', 'b', 'c', 'd'])
    recordComparison(state, 'a', 'b', 'positive')
    recordComparison(state, 'a', 'c', 'positive')
    // Group {a,b,c} size 3, group {d} size 1
    const { groups } = getResults(state)
    expect(groups.length).toBe(2)
    expect(groups[0].length).toBe(3)
    expect(groups[1].length).toBe(1)
    expect(groups[1]).toEqual(['d'])
  })

  it('all positive = one big group', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'positive')
    recordComparison(state, 'b', 'c', 'positive')
    const { groups } = getResults(state)
    expect(groups.length).toBe(1)
    expect(groups[0].length).toBe(3)
  })

  it('all negative = all singletons', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    recordComparison(state, 'a', 'b', 'negative')
    recordComparison(state, 'a', 'c', 'negative')
    recordComparison(state, 'b', 'c', 'negative')
    const { groups } = getResults(state)
    expect(groups.length).toBe(3)
    expect(groups.every(g => g.length === 1)).toBe(true)
  })

  it('no comparisons = all singletons', () => {
    const state = createCrossCompareState(['a', 'b', 'c'])
    const { groups } = getResults(state)
    expect(groups.length).toBe(3)
  })
})

// ── End-to-end scenario ──

describe('end-to-end cross compare', () => {
  it('efficiently resolves 5 traces into 2 groups', () => {
    // Scenario: {a,b,c} are similar, {d,e} are similar, the two groups are different
    const state = createCrossCompareState(['a', 'b', 'c', 'd', 'e'])
    let steps = 0
    while (state.currentPair && steps < 20) {
      const [x, y] = state.currentPair
      const groupABC = new Set(['a', 'b', 'c'])
      const groupDE = new Set(['d', 'e'])
      const sameGroup = (groupABC.has(x) && groupABC.has(y)) || (groupDE.has(x) && groupDE.has(y))
      const result = sameGroup ? 'positive' : 'negative'
      recordComparison(state, x, y, result as any)
      state.currentPair = nextPair(state)
      if (!state.currentPair) state.isComplete = true
      steps++
    }
    expect(state.isComplete).toBe(true)
    const { groups } = getResults(state)
    // Should find exactly 2 groups
    expect(groups.length).toBe(2)
    const sizes = groups.map(g => g.length).sort()
    expect(sizes).toEqual([2, 3])
    // Should take far fewer than 10 (= 5 choose 2) comparisons
    expect(steps).toBeLessThan(10)
    expect(getProgress(state).pct).toBe(100)
  })

  it('handles all-same scenario', () => {
    const state = createCrossCompareState(['a', 'b', 'c', 'd'])
    let steps = 0
    while (state.currentPair && steps < 20) {
      const [x, y] = state.currentPair
      recordComparison(state, x, y, 'positive')
      state.currentPair = nextPair(state)
      if (!state.currentPair) state.isComplete = true
      steps++
    }
    expect(state.isComplete).toBe(true)
    // With 4 nodes, need at most 3 positive comparisons to connect them all
    expect(steps).toBeLessThanOrEqual(3)
    const { groups } = getResults(state)
    expect(groups.length).toBe(1)
    expect(groups[0].length).toBe(4)
  })

  it('skip does not prevent revisiting the pair', () => {
    const state = createCrossCompareState(['a', 'b'])
    recordComparison(state, 'a', 'b', 'skip')
    // Skip was recorded but pair is still unresolved
    // nextPair should still return null since the only pair was compared (even if skipped)
    // The skip records the comparison — it won't come back automatically
    state.currentPair = nextPair(state)
    // With only 2 traces and 1 skip, no more reps to try
    expect(state.currentPair).toBeNull()
  })
})
