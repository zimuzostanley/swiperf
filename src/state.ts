import m from 'mithril'
import type { TraceEntry, Slice, MergedSlice, Verdict, OverviewFilter, SortState } from './models/types'
import { build_merge_cache, get_compressed } from './models/compression'

export interface TraceState {
  trace: TraceEntry
  cache: Map<number, MergedSlice[]> | null
  totalDur: number
  origN: number
  sliderValue: number
  currentSeq: MergedSlice[]
}

interface VerdictCounts { positive: number; negative: number; pending: number }

export interface Cluster {
  id: string
  name: string
  traces: TraceState[]
  verdicts: Map<string, Verdict>
  overviewFilter: OverviewFilter
  counts: VerdictCounts
  tableSortState: Record<string, SortState>
  splitView: boolean
  splitFilters: [OverviewFilter, OverviewFilter]
  splitRatio: number
}

function makeCluster(name: string, traces: TraceState[]): Cluster {
  return {
    id: crypto.randomUUID(), name, traces,
    verdicts: new Map(), overviewFilter: 'all',
    counts: { positive: 0, negative: 0, pending: traces.length },
    tableSortState: {},
    splitView: false,
    splitFilters: ['pending', 'positive'],
    splitRatio: 0.5,
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

export function recomputeCounts(cl: Cluster) {
  let positive = 0, negative = 0
  for (const v of cl.verdicts.values()) {
    if (v === 'like') positive++; else if (v === 'dislike') negative++
  }
  cl.counts = { positive, negative, pending: cl.traces.length - positive - negative }
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
  m.redraw()
}

export function renameCluster(id: string, name: string) {
  const cl = S.clusters.find(c => c.id === id)
  if (cl && name.trim()) cl.name = name.trim()
  m.redraw()
}

export function setVerdict(cl: Cluster, uuid: string, verdict: Verdict) {
  if (cl.verdicts.get(uuid) === verdict) { cl.verdicts.delete(uuid) }
  else { cl.verdicts.set(uuid, verdict) }
  recomputeCounts(cl)
  m.redraw()
}

export function filterTraces(cl: Cluster, filter: OverviewFilter): TraceState[] {
  switch (filter) {
    case 'positive': return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'like')
    case 'negative': return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'dislike')
    case 'pending': return cl.traces.filter(ts => !cl.verdicts.has(ts.trace.trace_uuid))
    default: return cl.traces
  }
}

export function filteredTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return filterTraces(cl, cl.overviewFilter)
}

export function getPositiveTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'like')
}

export function getNegativeTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return cl.traces.filter(ts => cl.verdicts.get(ts.trace.trace_uuid) === 'dislike')
}
