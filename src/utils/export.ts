// src/utils/export.ts — Pure export helpers (no DOM, no mithril)
//
// Builds export rows from trace/cluster data and converts to JSON or TSV.
// Excludes slices/quantized_sequence from output — exports metadata only.

import type { Verdict } from '../models/types'

export interface ExportRow {
  trace_uuid: string
  package_name: string
  startup_dur: number
  tab_name: string
  verdict: string
  link: string
  [key: string]: unknown
}

export interface ExportableTrace {
  trace_uuid: string
  package_name: string
  startup_dur: number
  extra?: Record<string, unknown>
}

/** Fields to always exclude from export (large data columns). */
const EXCLUDED_EXTRA = new Set([
  'slices', 'quantized_sequence', 'quantized_sequence_base64',
])

/** Build a perfetto trace link URL. */
export function buildTraceLink(uuid: string, startupId?: unknown): string {
  if (!uuid) return ''
  let url = `https://apconsole.corp.google.com/link/perfetto/field_traces?uuid=${uuid}`
  if (startupId != null && startupId !== '') {
    url += `&query=${encodeURIComponent(`com.android.AndroidStartup.startupId=${startupId}`)}`
  }
  return url
}

/** Human-readable verdict label. */
function verdictLabel(v: Verdict | undefined): string {
  if (v === 'like') return 'positive'
  if (v === 'dislike') return 'negative'
  if (v === 'discard') return 'discarded'
  return 'pending'
}

/** Build one export row from a trace + its cluster context. */
export function traceExportRow(
  trace: ExportableTrace,
  traceKey: string,
  tabName: string,
  verdicts: Map<string, Verdict>,
): ExportRow {
  const row: ExportRow = {
    trace_uuid: trace.trace_uuid,
    package_name: trace.package_name,
    startup_dur: trace.startup_dur,
    tab_name: tabName,
    verdict: verdictLabel(verdicts.get(traceKey)),
    link: buildTraceLink(trace.trace_uuid, trace.extra?.startup_id),
  }
  if (trace.extra) {
    for (const [k, v] of Object.entries(trace.extra)) {
      if (!EXCLUDED_EXTRA.has(k)) row[k] = v
    }
  }
  return row
}

/** Fixed columns that always appear first, in order. */
const FIXED_COLS = ['trace_uuid', 'package_name', 'startup_dur', 'tab_name', 'verdict', 'link']

/** Escape a value for TSV output: stringify non-primitives, strip tabs/newlines. */
function tsvEscape(v: unknown): string {
  if (v == null) return ''
  if (typeof v === 'object') return JSON.stringify(v).replace(/[\t\n\r]/g, ' ')
  return String(v).replace(/[\t\n\r]/g, ' ')
}

/** Convert rows to TSV string with stable column order. */
export function rowsToTsv(rows: ExportRow[]): string {
  if (rows.length === 0) return ''
  // Collect all extra columns across all rows
  const extraCols = new Set<string>()
  for (const row of rows) {
    for (const k of Object.keys(row)) {
      if (!FIXED_COLS.includes(k)) extraCols.add(k)
    }
  }
  const cols = [...FIXED_COLS, ...[...extraCols].sort()]
  const header = cols.join('\t')
  const lines = rows.map(row => cols.map(c => tsvEscape(row[c])).join('\t'))
  return header + '\n' + lines.join('\n')
}

/** Convert rows to pretty-printed JSON string. */
export function rowsToJson(rows: ExportRow[]): string {
  return JSON.stringify(rows, null, 2)
}
