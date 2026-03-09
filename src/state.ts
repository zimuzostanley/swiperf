import m from 'mithril'
import type { TraceEntry, Slice, MergedSlice, Verdict, OverviewFilter, SortState } from './models/types'
import { build_merge_cache, get_compressed } from './models/compression'

export interface TraceState {
  trace: TraceEntry
  _key: string
  cache: Map<number, MergedSlice[]> | null
  totalDur: number
  origN: number
  sliderValue: number
  currentSeq: MergedSlice[]
}

export function traceKey(t: TraceEntry): string {
  const startupId = t.extra?.startup_id ?? ''
  return `${t.trace_uuid}|${t.package_name}|${startupId}|${t.startup_dur}`
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
  sortField: 'index' | 'startup_dur'
  sortDir: 1 | -1
  propFilters: Map<string, Set<string>>
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
    sortField: 'index',
    sortDir: 1,
    propFilters: new Map(),
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
  return { trace, _key: traceKey(trace), cache: null, totalDur, origN: slices.length,
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
  const allStates = entries.map(initTraceLazy)
  // Deduplicate by composite key
  const seen = new Set<string>()
  const states = allStates.filter(ts => {
    if (seen.has(ts._key)) return false
    seen.add(ts._key)
    return true
  })
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

function applyPropFilters(cl: Cluster, traces: TraceState[]): TraceState[] {
  if (cl.propFilters.size === 0) return traces
  return traces.filter(ts => {
    for (const [field, allowed] of cl.propFilters) {
      const val = String(ts.trace.extra?.[field] ?? '')
      if (!allowed.has(val)) return false
    }
    return true
  })
}

function applySorting(cl: Cluster, traces: TraceState[]): TraceState[] {
  if (cl.sortField === 'index') return traces
  const sorted = [...traces]
  sorted.sort((a, b) => (a.trace.startup_dur - b.trace.startup_dur) * cl.sortDir)
  return sorted
}

export function filterTraces(cl: Cluster, filter: OverviewFilter): TraceState[] {
  let result: TraceState[]
  switch (filter) {
    case 'positive': result = cl.traces.filter(ts => cl.verdicts.get(ts._key) === 'like'); break
    case 'negative': result = cl.traces.filter(ts => cl.verdicts.get(ts._key) === 'dislike'); break
    case 'pending': result = cl.traces.filter(ts => !cl.verdicts.has(ts._key)); break
    default: result = cl.traces
  }
  result = applyPropFilters(cl, result)
  return applySorting(cl, result)
}

export function filteredTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return filterTraces(cl, cl.overviewFilter)
}

export function getPositiveTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return cl.traces.filter(ts => cl.verdicts.get(ts._key) === 'like')
}

export function getNegativeTraces(): TraceState[] {
  const cl = activeCluster()
  if (!cl) return []
  return cl.traces.filter(ts => cl.verdicts.get(ts._key) === 'dislike')
}

// Collect unique values for a given extra field across all traces
export function getFieldValues(cl: Cluster, field: string): string[] {
  const vals = new Set<string>()
  for (const ts of cl.traces) {
    vals.add(String(ts.trace.extra?.[field] ?? ''))
  }
  return [...vals].sort()
}

// Only these fields appear in the filter dropdown
const FILTERABLE_FIELDS = ['device_name', 'unique_session_name', 'startup_type']

// Get list of filterable extra fields that have multiple distinct values
export function getFilterableFields(cl: Cluster): string[] {
  return FILTERABLE_FIELDS.filter(field => {
    const vals = new Set<string>()
    for (const ts of cl.traces) {
      vals.add(String(ts.trace.extra?.[field] ?? ''))
      if (vals.size >= 2) return true
    }
    return false
  })
}

export function togglePropFilter(cl: Cluster, field: string, value: string) {
  let allowed = cl.propFilters.get(field)
  if (!allowed) {
    // First click: select only this value (deselect all others)
    const all = getFieldValues(cl, field)
    allowed = new Set([value])
    cl.propFilters.set(field, allowed)
  } else if (allowed.has(value)) {
    allowed.delete(value)
    if (allowed.size === 0) cl.propFilters.delete(field)
  } else {
    allowed.add(value)
    // If all values selected, remove filter entirely
    const all = getFieldValues(cl, field)
    if (allowed.size === all.length) cl.propFilters.delete(field)
  }
  m.redraw()
}

export function clearPropFilter(cl: Cluster, field: string) {
  cl.propFilters.delete(field)
  m.redraw()
}

// ── Session save / restore ──

interface SessionData {
  version: 1
  activeClusterId: string | null
  clusters: {
    id: string
    name: string
    traces: TraceEntry[]
    verdicts: [string, Verdict][]
    overviewFilter: OverviewFilter
    splitView: boolean
    splitFilters: [OverviewFilter, OverviewFilter]
    splitRatio: number
    sortField?: 'index' | 'startup_dur'
    sortDir?: 1 | -1
    propFilters?: [string, string[]][]
  }[]
}

export function exportSession(): string {
  const data: SessionData = {
    version: 1,
    activeClusterId: S.activeClusterId,
    clusters: S.clusters.map(cl => ({
      id: cl.id,
      name: cl.name,
      traces: cl.traces.map(ts => ts.trace),
      verdicts: [...cl.verdicts.entries()],
      overviewFilter: cl.overviewFilter,
      splitView: cl.splitView,
      splitFilters: cl.splitFilters,
      splitRatio: cl.splitRatio,
      sortField: cl.sortField,
      sortDir: cl.sortDir,
      propFilters: [...cl.propFilters.entries()].map(([k, v]) => [k, [...v]]),
    })),
  }
  return JSON.stringify(data)
}

export function importSession(json: string) {
  const data: SessionData = JSON.parse(json)
  if (data.version !== 1) throw new Error('Unknown session version')
  S.clusters = data.clusters.map(sc => {
    const traces = sc.traces.map(initTraceLazy)
    const cl: Cluster = {
      id: sc.id,
      name: sc.name,
      traces,
      verdicts: new Map(sc.verdicts),
      overviewFilter: sc.overviewFilter,
      counts: { positive: 0, negative: 0, pending: 0 },
      tableSortState: {},
      splitView: sc.splitView,
      splitFilters: sc.splitFilters,
      splitRatio: sc.splitRatio,
      sortField: sc.sortField || 'index',
      sortDir: sc.sortDir || 1,
      propFilters: new Map((sc.propFilters || []).map(([k, v]) => [k, new Set(v)])),
    }
    recomputeCounts(cl)
    if (traces.length > 0) ensureCache(traces[0])
    return cl
  })
  S.activeClusterId = data.activeClusterId
  m.redraw()
}
