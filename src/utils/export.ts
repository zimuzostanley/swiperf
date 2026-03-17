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
  'slices', 'quantized_sequence', 'quantized_sequence_json', 'quantized_sequence_base64',
])

/** Build a perfetto trace link URL. */
export function buildTraceLink(uuid: string, packageName?: string): string {
  if (!uuid) return ''
  let url = `https://apconsole.corp.google.com/link/perfetto/field_traces?uuid=${uuid}`
  if (packageName) {
    url += `&query=${encodeURIComponent(`com.android.AndroidStartup.packageName=${packageName}`)}`
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
    link: buildTraceLink(trace.trace_uuid, trace.package_name),
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

/** Build a Sheets HYPERLINK formula that opens all trace UUIDs in Brush. */
function brushFormula(dataRows: number, uuidCol: string, linkCol: string): string {
  const lastRow = dataRows + 1 // +1 for header
  const range = `${uuidCol}2:${uuidCol}${lastRow}`
  return `=IF(COUNTA(${range})=0, "No UUIDs found",` +
    ` HYPERLINK(` +
    `"https://brush.corp.google.com/?filters=" &` +
    ` ENCODEURL(` +
    `"[{" & CHAR(34) & "column" & CHAR(34) & ":" & CHAR(34) & "trace_uuid" & CHAR(34) &` +
    ` "," & CHAR(34) & "operator" & CHAR(34) & ":" & CHAR(34) & "in" & CHAR(34) &` +
    ` "," & CHAR(34) & "value" & CHAR(34) & ":" & CHAR(34) & "[" & CHAR(92) & CHAR(34) &` +
    ` TEXTJOIN(CHAR(92) & CHAR(34) & "," & CHAR(92) & CHAR(34), TRUE, FILTER(${range}, ${range}<>"")) &` +
    ` CHAR(92) & CHAR(34) & "]" & CHAR(34) & "}]"` +
    `) &` +
    ` "&metric_id=android_startup&charts=gallery&gallerySvgColumn=svg&galleryMetricColumn=dur_ms&galleryMetricNameColumn=process_name",` +
    ` "Open in Brush"))`
}

/** Column index to spreadsheet letter (0=A, 1=B, ..., 25=Z, 26=AA). */
function colLetter(idx: number): string {
  let s = ''
  let n = idx
  while (n >= 0) {
    s = String.fromCharCode(65 + (n % 26)) + s
    n = Math.floor(n / 26) - 1
  }
  return s
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

  // Append a formula row with Brush hyperlink in the link column
  const linkIdx = cols.indexOf('link')
  const uuidIdx = cols.indexOf('trace_uuid')
  if (linkIdx >= 0 && uuidIdx >= 0) {
    const formulaCells = cols.map((_, i) =>
      i === linkIdx ? brushFormula(rows.length, colLetter(uuidIdx), colLetter(linkIdx)) : ''
    )
    return header + '\n' + lines.join('\n') + '\n' + formulaCells.join('\t')
  }

  return header + '\n' + lines.join('\n')
}

/** Convert rows to pretty-printed JSON string. */
export function rowsToJson(rows: ExportRow[]): string {
  return JSON.stringify(rows, null, 2)
}
