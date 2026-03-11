// Cross Compare — Union-Find based pairwise comparison algorithm
//
// Traces are nodes in a graph. Positive comparisons create edges that merge
// nodes into clusters (union-find). Negative comparisons mark inter-component
// separation. Transitivity: if A+B and B+C then A+C is implied — no need to
// compare A vs C. The pair selector prioritises comparing representatives of
// the two largest unresolved components for maximum information gain.

export function edgeKey(a: string, b: string): string {
  return a < b ? `${a}|${b}` : `${b}|${a}`
}

// ── Union-Find ──

export class UnionFind {
  private parent = new Map<string, string>()
  private _rank = new Map<string, number>()

  constructor(keys: string[]) {
    for (const k of keys) {
      this.parent.set(k, k)
      this._rank.set(k, 0)
    }
  }

  find(x: string): string {
    let root = x
    while (this.parent.get(root) !== root) root = this.parent.get(root)!
    // Path compression
    let cur = x
    while (cur !== root) {
      const next = this.parent.get(cur)!
      this.parent.set(cur, root)
      cur = next
    }
    return root
  }

  union(a: string, b: string): void {
    const ra = this.find(a), rb = this.find(b)
    if (ra === rb) return
    const rankA = this._rank.get(ra)!, rankB = this._rank.get(rb)!
    if (rankA < rankB) this.parent.set(ra, rb)
    else if (rankA > rankB) this.parent.set(rb, ra)
    else { this.parent.set(rb, ra); this._rank.set(ra, rankA + 1) }
  }

  connected(a: string, b: string): boolean {
    return this.find(a) === this.find(b)
  }

  /** Returns map of root -> member keys (sorted by insertion order). */
  components(): Map<string, string[]> {
    const map = new Map<string, string[]>()
    for (const k of this.parent.keys()) {
      const root = this.find(k)
      let arr = map.get(root)
      if (!arr) { arr = []; map.set(root, arr) }
      arr.push(k)
    }
    return map
  }
}

// ── Cross Compare State ──

export type ComparisonResult = 'positive' | 'negative' | 'skip'

export interface CrossCompareState {
  uf: UnionFind
  /** Negative edges between component roots (canonical edgeKey of roots). */
  negativeEdges: Set<string>
  /** All recorded comparisons (canonical edgeKey of trace keys). */
  comparisons: Map<string, ComparisonResult>
  traceKeys: string[]
  currentPair: [string, string] | null
  selectedSide: 'left' | 'right' | null
  isComplete: boolean
}

export function createCrossCompareState(traceKeys: string[]): CrossCompareState {
  const state: CrossCompareState = {
    uf: new UnionFind(traceKeys),
    negativeEdges: new Set(),
    comparisons: new Map(),
    traceKeys,
    currentPair: null,
    selectedSide: null,
    isComplete: false,
  }
  state.currentPair = nextPair(state)
  if (!state.currentPair) state.isComplete = true
  return state
}

// ── Core Operations ──

export function recordComparison(
  state: CrossCompareState,
  keyA: string,
  keyB: string,
  result: ComparisonResult,
): void {
  const ek = edgeKey(keyA, keyB)
  state.comparisons.set(ek, result)

  if (result === 'positive') {
    // Before merging, transfer any negative edges to the new root
    const rootA = state.uf.find(keyA), rootB = state.uf.find(keyB)
    // Collect negative edges involving either root
    const toRekey: [string, string][] = []
    for (const neg of state.negativeEdges) {
      const [x, y] = neg.split('|')
      if (x === rootA || x === rootB || y === rootA || y === rootB) {
        toRekey.push([x, y])
      }
    }
    state.uf.union(keyA, keyB)
    // Re-key negative edges to use new roots
    for (const [x, y] of toRekey) {
      state.negativeEdges.delete(edgeKey(x, y))
      const rx = state.uf.find(x), ry = state.uf.find(y)
      if (rx !== ry) state.negativeEdges.add(edgeKey(rx, ry))
    }
  } else if (result === 'negative') {
    const rootA = state.uf.find(keyA), rootB = state.uf.find(keyB)
    if (rootA !== rootB) {
      state.negativeEdges.add(edgeKey(rootA, rootB))
    }
  }
  // 'skip' — recorded but no structural change
}

/** Find the next most informative pair to compare. */
export function nextPair(state: CrossCompareState): [string, string] | null {
  const comps = state.uf.components()
  // Sort components by size descending for maximum information gain
  const sorted = [...comps.entries()].sort((a, b) => b[1].length - a[1].length)

  for (let i = 0; i < sorted.length; i++) {
    for (let j = i + 1; j < sorted.length; j++) {
      const rootI = sorted[i][0], rootJ = sorted[j][0]
      // Already separated by a negative edge?
      if (state.negativeEdges.has(edgeKey(rootI, rootJ))) continue
      // Find any uncompared pair between these two components
      const pair = findUncomparedPair(state, sorted[i][1], sorted[j][1])
      if (pair) return pair
      // All individual pairs compared (some may have been skipped) — treat as unresolvable
    }
  }
  return null // All resolved or all remaining pairs were skipped
}

/** Find any uncompared pair between two component member lists. */
function findUncomparedPair(
  state: CrossCompareState,
  membersA: string[],
  membersB: string[],
): [string, string] | null {
  for (const a of membersA) {
    for (const b of membersB) {
      if (!state.comparisons.has(edgeKey(a, b))) return [a, b]
    }
  }
  return null
}

// ── Progress ──

export function getProgress(state: CrossCompareState): { completed: number; total: number; pct: number } {
  const comps = state.uf.components()
  const roots = [...comps.keys()]
  let total = 0, resolved = 0
  for (let i = 0; i < roots.length; i++) {
    for (let j = i + 1; j < roots.length; j++) {
      total++
      if (state.negativeEdges.has(edgeKey(roots[i], roots[j]))) {
        resolved++
      } else if (!findUncomparedPair(state, comps.get(roots[i])!, comps.get(roots[j])!)) {
        // All individual pairs between these components were compared (skipped) — count as resolved
        resolved++
      }
    }
  }
  // Completed = resolved component pairs + merged pairs (no longer distinct)
  const n = state.traceKeys.length
  const maxPairs = n * (n - 1) / 2
  // Pairs resolved by merging = maxPairs - remaining distinct pairs
  const mergedPairs = maxPairs - total
  const totalWork = maxPairs
  const completedWork = mergedPairs + resolved
  return {
    completed: completedWork,
    total: totalWork,
    pct: totalWork > 0 ? Math.round((completedWork / totalWork) * 100) : 100,
  }
}

// ── Results ──

export interface CrossCompareResults {
  /** Groups sorted by size descending. Each group is an array of trace _keys. */
  groups: string[][]
}

export function getResults(state: CrossCompareState): CrossCompareResults {
  const comps = state.uf.components()
  const groups = [...comps.values()].sort((a, b) => b.length - a.length)
  return { groups }
}
