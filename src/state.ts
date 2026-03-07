import m from 'mithril'
import type { TraceEntry, Slice, MergedSlice, Verdict, ViewMode, OverviewFilter, SortState } from './models/types'
import { build_merge_cache, get_compressed } from './models/compression'

export interface TraceState {
  trace: TraceEntry
  cache: Map<number, MergedSlice[]> | null
  totalDur: number
  origN: number
  sliderValue: number
  currentSeq: MergedSlice[]
}

interface VerdictCounts { liked: number; disliked: number; pending: number }

export interface Cluster {
  id: string
  name: string
  traces: TraceState[]
  currentIndex: number
  verdicts: Map<string, Verdict>
  viewMode: ViewMode
  overviewFilter: OverviewFilter
  counts: VerdictCounts
  autoAdvance: boolean
  tableSortState: Record<string, SortState>
}

function makeCluster(name: string, traces: TraceState[]): Cluster {
  return {
    id: crypto.randomUUID(), name, traces, currentIndex: 0,
    verdicts: new Map(), viewMode: 'single', overviewFilter: 'all',
    counts: { liked: 0, disliked: 0, pending: traces.length },
    autoAdvance: true, tableSortState: {},
  }
}

interface AppState {
  clusters: Cluster[]
  activeClusterId: string | null
  importMsg: { text: string; ok: boolean } | null
}

export const S: AppState = { clusters: [], activeClusterId: null, importMsg: null }

export function activeCluster(): Cluster | null {
  return S.clusters.find(c => c.id === S.activeClusterId) ?? null
}

export function currentTrace(): TraceState | null {
  const cl = activeCluster()
  return cl ? cl.traces[cl.currentIndex] ?? null : null
}

export function recomputeCounts(cl: Cluster) {
  let liked = 0, disliked = 0
  for (const v of cl.verdicts.values()) {
    if (v === 'like') liked++; else if (v === 'dislike') disliked++
  }
  cl.counts = { liked, disliked, pending: cl.traces.length - liked - disliked }
}

export function initTraceLazy(trace: TraceEntry): TraceState {
  const slices = trace.slices
  const totalDur = slices.length > 0
    ? slices.reduce((mx, d) => Math.max(mx, (d.ts - slices[0].ts) + d.dur), 0) : 0
  return { trace, cache: null, totalDur, origN: slices.length,
    sliderValue: slices.length, currentSeq: [] }
}

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

export function addCluster(name: string, entries: TraceEntry[]) {
  const states = entries.map(initTraceLazy)
  const cl = makeCluster(name, states)
  if (states.length > 0) ensureCache(states[0])
  S.clusters.push(cl)
  S.activeClusterId = cl.id
  m.redraw()
}

export function loadSingleJson(data: Slice[], uuid?: string, pkg?: string, dur?: number) {
  addCluster('Import', [{ trace_uuid: uuid || crypto.randomUUID(),
    package_name: pkg || 'unknown', startup_dur: dur || 0, slices: data }])
}

export function loadMultipleTraces(name: string, traces: TraceEntry[]) {
  addCluster(name, traces)
}

export function removeCluster(id: string) {
  S.clusters = S.clusters.filter(c => c.id !== id)
  if (S.activeClusterId === id)
    S.activeClusterId = S.clusters.length > 0 ? S.clusters[0].id : null
  m.redraw()
}

export function switchCluster(id: string) {
  S.activeClusterId = id
  const cl = activeCluster()
  if (cl && cl.traces.length > 0) ensureCache(cl.traces[cl.currentIndex])
  m.redraw()
}

export function renameCluster(id: string, name: string) {
  const cl = S.clusters.find(c => c.id === id)
  if (cl && name.trim()) cl.name = name.trim()
  m.redraw()
}

export function navigate(delta: number) {
  const cl = activeCluster()
  if (!cl || cl.traces.length === 0) return
  cl.currentIndex = Math.max(0, Math.min(cl.traces.length - 1, cl.currentIndex + delta))
  ensureCache(cl.traces[cl.currentIndex])
  m.redraw()
}

export function jumpTo(index: number) {
  const cl = activeCluster()
  if (!cl || index < 0 || index >= cl.traces.length) return
  cl.currentIndex = index
  cl.viewMode = 'single'
  ensureCache(cl.traces[cl.currentIndex])
  m.redraw()
}

function findNextPending(cl: Cluster, from: number): number {
  for (let i = from + 1; i < cl.traces.length; i++)
    if (!cl.verdicts.has(cl.traces[i].trace.trace_uuid)) return i
  for (let i = 0; i <= from; i++)
    if (!cl.verdicts.has(cl.traces[i].trace.trace_uuid)) return i
  return -1
}

export function setVerdict(verdict: Verdict) {
  const cl = activeCluster()
  if (!cl) return
  const t = cl.traces[cl.currentIndex]
  if (!t) return
  const uuid = t.trace.trace_uuid
  if (cl.verdicts.get(uuid) === verdict) { cl.verdicts.delete(uuid) }
  else {
    cl.verdicts.set(uuid, verdict)
    if (cl.autoAdvance && cl.traces.length > 1) {
      const next = findNextPending(cl, cl.currentIndex)
      if (next >= 0 && next !== cl.currentIndex) {
        cl.currentIndex = next
        ensureCache(cl.traces[cl.currentIndex])
      }
    }
  }
  recomputeCounts(cl)
  m.redraw()
}

export function filteredTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  switch (cl.overviewFilter) {
    case 'liked': return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'like')
    case 'disliked': return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'dislike')
    case 'pending': return cl.traces.filter(ts => !cl.verdicts.has(ts.trace.trace_uuid))
    default: return cl.traces
  }
}

export function getLikedTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'like')
}
