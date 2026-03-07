import m from 'mithril'
import type { TraceEntry, Slice, MergedSlice, Verdict, ViewMode, OverviewFilter, SortState } from './models/types'
import { build_merge_cache, get_compressed } from './models/compression'

// Lazy trace state — cache is built on first view, not at load time.
// This is critical for 10k+ traces: we only pay O(N^2) for traces
// the user actually looks at.
export interface TraceState {
  trace: TraceEntry
  cache: Map<number, MergedSlice[]> | null  // null = not yet computed
  totalDur: number
  origN: number
  sliderValue: number
  currentSeq: MergedSlice[]
}

interface VerdictCounts {
  liked: number
  disliked: number
  pending: number
}

interface AppState {
  traces: TraceState[]
  currentIndex: number
  verdicts: Map<string, Verdict>
  viewMode: ViewMode
  overviewFilter: OverviewFilter
  tableSortState: Record<string, SortState>
  importMsg: { text: string; ok: boolean } | null
  autoAdvance: boolean
  // Cached counts — updated on every verdict change, not recomputed per render
  counts: VerdictCounts
}

export const S: AppState = {
  traces: [],
  currentIndex: 0,
  verdicts: new Map(),
  viewMode: 'single',
  overviewFilter: 'all',
  tableSortState: {},
  importMsg: null,
  autoAdvance: true,
  counts: { liked: 0, disliked: 0, pending: 0 },
}

function recomputeCounts() {
  let liked = 0, disliked = 0
  for (const v of S.verdicts.values()) {
    if (v === 'like') liked++
    else if (v === 'dislike') disliked++
  }
  S.counts = { liked, disliked, pending: S.traces.length - liked - disliked }
}

export function currentTrace(): TraceState | null {
  return S.traces[S.currentIndex] ?? null
}

// Cheap init — only computes totalDur and origN (O(N)), skips merge cache.
export function initTraceLazy(trace: TraceEntry): TraceState {
  const slices = trace.slices
  const totalDur = slices.length > 0
    ? slices.reduce((mx, d) => Math.max(mx, (d.ts - slices[0].ts) + d.dur), 0)
    : 0
  const origN = slices.length
  const sliderValue = Math.min(10, origN)
  return {
    trace,
    cache: null,
    totalDur,
    origN,
    sliderValue,
    currentSeq: [],  // will be filled on ensureCache
  }
}

// Build cache on demand — only called when user actually views the trace.
export function ensureCache(ts: TraceState) {
  if (ts.cache !== null) return
  ts.cache = build_merge_cache(ts.trace.slices)
  ts.currentSeq = get_compressed(ts.cache, ts.origN, ts.sliderValue)
}

export function updateSlider(ts: TraceState, value: number) {
  ensureCache(ts)
  ts.sliderValue = value
  ts.currentSeq = get_compressed(ts.cache!, ts.origN, value)
}

export function loadSingleJson(data: Slice[], uuid?: string, pkg?: string, dur?: number) {
  const trace: TraceEntry = {
    trace_uuid: uuid || crypto.randomUUID(),
    package_name: pkg || 'unknown',
    startup_dur: dur || 0,
    slices: data,
  }
  S.traces = [initTraceLazy(trace)]
  S.currentIndex = 0
  S.viewMode = 'single'
  S.verdicts.clear()
  recomputeCounts()
  // Eagerly build cache for single trace
  ensureCache(S.traces[0])
  m.redraw()
}

export function loadMultipleTraces(traces: TraceEntry[]) {
  S.traces = traces.map(initTraceLazy)
  S.currentIndex = 0
  S.viewMode = S.traces.length > 1 ? 'single' : 'single'
  S.verdicts.clear()
  recomputeCounts()
  // Only build cache for first trace
  if (S.traces.length > 0) ensureCache(S.traces[0])
  m.redraw()
}

export function navigate(delta: number) {
  if (S.traces.length === 0) return
  S.currentIndex = Math.max(0, Math.min(S.traces.length - 1, S.currentIndex + delta))
  ensureCache(S.traces[S.currentIndex])
  m.redraw()
}

export function jumpTo(index: number) {
  if (index < 0 || index >= S.traces.length) return
  S.currentIndex = index
  S.viewMode = 'single'
  ensureCache(S.traces[S.currentIndex])
  m.redraw()
}

// Find next trace without a verdict, starting from current position.
function findNextPending(fromIndex: number): number {
  for (let i = fromIndex + 1; i < S.traces.length; i++) {
    if (!S.verdicts.has(S.traces[i].trace.trace_uuid)) return i
  }
  // Wrap around
  for (let i = 0; i <= fromIndex; i++) {
    if (!S.verdicts.has(S.traces[i].trace.trace_uuid)) return i
  }
  return -1 // all triaged
}

export function setVerdict(verdict: Verdict) {
  const t = currentTrace()
  if (!t) return
  const uuid = t.trace.trace_uuid
  const current = S.verdicts.get(uuid)

  if (current === verdict) {
    S.verdicts.delete(uuid)
  } else {
    S.verdicts.set(uuid, verdict)
    // Auto-advance to next pending trace
    if (S.autoAdvance && S.traces.length > 1) {
      const next = findNextPending(S.currentIndex)
      if (next >= 0 && next !== S.currentIndex) {
        S.currentIndex = next
        ensureCache(S.traces[S.currentIndex])
      }
    }
  }

  recomputeCounts()
  m.redraw()
}

export function filteredTraces(): TraceState[] {
  switch (S.overviewFilter) {
    case 'liked': return S.traces.filter(ts => S.verdicts.get(ts.trace.trace_uuid) === 'like')
    case 'disliked': return S.traces.filter(ts => S.verdicts.get(ts.trace.trace_uuid) === 'dislike')
    case 'pending': return S.traces.filter(ts => !S.verdicts.has(ts.trace.trace_uuid))
    default: return S.traces
  }
}

export function getLikedTraces(): TraceState[] {
  return S.traces.filter(ts => S.verdicts.get(ts.trace.trace_uuid) === 'like')
}
