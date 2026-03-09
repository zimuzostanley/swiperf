export interface Slice {
  ts: number
  dur: number
  name: string | null
  state: string | null
  depth: number | null
  io_wait: number | null
  blocked_function: string | null
}

export interface MergedSlice extends Slice {
  tsRel: number
  _merged: number
}

export interface TraceEntry {
  trace_uuid: string
  package_name: string
  startup_dur: number
  slices: Slice[]
  // Extra fields from import, preserved for export
  extra?: Record<string, unknown>
}

export type Verdict = 'like' | 'dislike'

export type OverviewFilter = 'all' | 'positive' | 'negative' | 'pending'

export interface SortState {
  col: string
  dir: 1 | -1
}

export interface SummaryRow {
  label: string
  short: string
  dur: number
  count: number
  color: string
  pct: number
}

// Configurable column mapping for import fallbacks.
// Each key is our internal field name; the value is an ordered list of
// column names to try when resolving from TSV/CSV/JSON, plus an optional
// default when nothing matches.
export interface ColumnConfig {
  trace_uuid: { aliases: string[]; fallback: () => string }
  package_name: { aliases: string[]; fallback: () => string }
  startup_dur: { aliases: string[]; fallback: () => number }
  slices: { aliases: string[]; fallback: () => Slice[] }
}

export const DEFAULT_COLUMN_CONFIG: ColumnConfig = {
  trace_uuid: {
    aliases: ['trace_uuid', 'uuid', 'id', 'trace_id'],
    fallback: () => crypto.randomUUID(),
  },
  package_name: {
    aliases: ['package_name', 'process_name', 'process', 'package', 'pkg', 'app'],
    fallback: () => 'unknown',
  },
  startup_dur: {
    aliases: ['startup_dur', 'startup_duration', 'dur', 'duration', 'total_dur', 'startup_ms'],
    fallback: () => 0,
  },
  slices: {
    aliases: ['slices', 'quantized_sequence', 'json', 'data', 'trace_data', 'base64', 'thread_slices'],
    fallback: () => [],
  },
}

// Slice field fallbacks — when a slice is missing expected fields
export interface SliceFieldConfig {
  ts: { aliases: string[]; fallback: number }
  dur: { aliases: string[]; fallback: number }
  name: { aliases: string[]; fallback: string | null }
  state: { aliases: string[]; fallback: string | null }
  depth: { aliases: string[]; fallback: number | null }
  io_wait: { aliases: string[]; fallback: number | null }
  blocked_function: { aliases: string[]; fallback: string | null }
}

export const DEFAULT_SLICE_FIELD_CONFIG: SliceFieldConfig = {
  ts: { aliases: ['ts', 'timestamp', 'start', 'start_ts', 'begin'], fallback: 0 },
  dur: { aliases: ['dur', 'duration', 'length', 'end_ts'], fallback: 0 },
  name: { aliases: ['name', 'slice_name', 'label', 'event'], fallback: null },
  state: { aliases: ['state', 'thread_state', 'sched_state'], fallback: null },
  depth: { aliases: ['depth', 'level', 'stack_depth'], fallback: null },
  io_wait: { aliases: ['io_wait', 'iowait', 'io'], fallback: null },
  blocked_function: { aliases: ['blocked_function', 'blocked_fn', 'blocked', 'wchan'], fallback: null },
}
