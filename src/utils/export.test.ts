import { describe, it, expect } from 'vitest'
import { traceExportRow, rowsToTsv, rowsToJson, buildTraceLink } from './export'
import type { ExportRow, ExportableTrace } from './export'
import type { Verdict } from '../models/types'

function makeTrace(uuid: string, pkg = 'com.test', dur = 0, extra?: Record<string, unknown>): ExportableTrace {
  return { trace_uuid: uuid, package_name: pkg, startup_dur: dur, extra }
}

describe('buildTraceLink', () => {
  it('builds URL with just uuid', () => {
    const url = buildTraceLink('abc-123')
    expect(url).toBe('https://apconsole.corp.google.com/link/perfetto/field_traces?uuid=abc-123')
  })

  it('includes packageName query param when provided', () => {
    const url = buildTraceLink('abc-123', 'com.google.android.app')
    expect(url).toContain('uuid=abc-123')
    expect(url).toContain('query=')
    expect(url).toContain('packageName%3Dcom.google.android.app')
  })

  it('returns empty string for empty uuid', () => {
    expect(buildTraceLink('')).toBe('')
  })
})

describe('traceExportRow', () => {
  it('includes core fields', () => {
    const trace = makeTrace('u1', 'com.app', 500)
    const verdicts = new Map<string, Verdict>()
    const row = traceExportRow(trace, 'key1', 'Tab A', verdicts)
    expect(row.trace_uuid).toBe('u1')
    expect(row.package_name).toBe('com.app')
    expect(row.startup_dur).toBe(500)
    expect(row.tab_name).toBe('Tab A')
    expect(row.verdict).toBe('pending')
    expect(row.link).toContain('uuid=u1')
  })

  it('maps verdict labels correctly', () => {
    const trace = makeTrace('u1')
    const verdicts = new Map<string, Verdict>([['k', 'like']])
    expect(traceExportRow(trace, 'k', 'T', verdicts).verdict).toBe('positive')

    verdicts.set('k', 'dislike')
    expect(traceExportRow(trace, 'k', 'T', verdicts).verdict).toBe('negative')

    verdicts.set('k', 'discard')
    expect(traceExportRow(trace, 'k', 'T', verdicts).verdict).toBe('discarded')
  })

  it('includes extra fields except excluded ones', () => {
    const trace = makeTrace('u1', 'com.app', 0, {
      device_name: 'pixel',
      startup_type: 'cold',
      quantized_sequence: '[...]',
      slices: 'should_be_excluded',
      startup_id: '7',
    })
    const row = traceExportRow(trace, 'k', 'T', new Map())
    expect(row.device_name).toBe('pixel')
    expect(row.startup_type).toBe('cold')
    expect(row.startup_id).toBe('7')
    expect(row).not.toHaveProperty('quantized_sequence')
    expect(row).not.toHaveProperty('slices')
  })

  it('includes link with packageName query param', () => {
    const trace = makeTrace('u1', 'com.google.app', 0)
    const row = traceExportRow(trace, 'k', 'T', new Map())
    expect(row.link).toContain('uuid=u1')
    expect(row.link).toContain('packageName%3Dcom.google.app')
  })
})

describe('rowsToTsv', () => {
  it('produces header + data lines', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'com.a', startup_dur: 100, tab_name: 'T', verdict: 'positive', link: 'http://x' },
      { trace_uuid: 'u2', package_name: 'com.b', startup_dur: 200, tab_name: 'T', verdict: 'negative', link: 'http://y' },
    ]
    const tsv = rowsToTsv(rows)
    const lines = tsv.split('\n')
    expect(lines.length).toBe(3) // header + 2 rows
    expect(lines[0]).toBe('trace_uuid\tpackage_name\tstartup_dur\ttab_name\tverdict\tlink')
    expect(lines[1]).toContain('u1')
    expect(lines[1]).toContain('positive')
  })

  it('includes extra columns sorted after fixed columns', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'a', startup_dur: 0, tab_name: 'T', verdict: 'p', link: '', device_name: 'pixel', zzz_field: 'last' },
    ]
    const tsv = rowsToTsv(rows)
    const header = tsv.split('\n')[0]
    const cols = header.split('\t')
    // Fixed cols first, then extras sorted
    expect(cols.indexOf('device_name')).toBeGreaterThan(cols.indexOf('link'))
    expect(cols.indexOf('zzz_field')).toBeGreaterThan(cols.indexOf('device_name'))
  })

  it('handles varying extra fields across rows', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'a', startup_dur: 0, tab_name: 'T', verdict: 'p', link: '', field_a: 'yes' },
      { trace_uuid: 'u2', package_name: 'b', startup_dur: 0, tab_name: 'T', verdict: 'p', link: '', field_b: 'yes' },
    ]
    const tsv = rowsToTsv(rows)
    const header = tsv.split('\n')[0]
    expect(header).toContain('field_a')
    expect(header).toContain('field_b')
    // Row 1 has field_a but not field_b (should be empty)
    const row1Cols = tsv.split('\n')[1].split('\t')
    const headerCols = header.split('\t')
    const fbIdx = headerCols.indexOf('field_b')
    expect(row1Cols[fbIdx]).toBe('')
  })

  it('escapes tabs and newlines in values', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'a', startup_dur: 0, tab_name: 'has\ttab', verdict: 'p', link: 'has\nnewline' },
    ]
    const tsv = rowsToTsv(rows)
    // Should not have literal tabs within a field value
    const dataLine = tsv.split('\n')[1]
    const fields = dataLine.split('\t')
    expect(fields[3]).toBe('has tab') // tab replaced with space
    expect(fields[5]).toBe('has newline') // newline replaced with space
  })

  it('stringifies object values', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'a', startup_dur: 0, tab_name: 'T', verdict: 'p', link: '', obj_field: { nested: true } as any },
    ]
    const tsv = rowsToTsv(rows)
    expect(tsv).toContain('{"nested":true}')
  })

  it('returns empty string for empty rows', () => {
    expect(rowsToTsv([])).toBe('')
  })
})

describe('rowsToJson', () => {
  it('produces pretty-printed JSON array', () => {
    const rows: ExportRow[] = [
      { trace_uuid: 'u1', package_name: 'a', startup_dur: 0, tab_name: 'T', verdict: 'pending', link: '' },
    ]
    const json = rowsToJson(rows)
    const parsed = JSON.parse(json)
    expect(parsed).toHaveLength(1)
    expect(parsed[0].trace_uuid).toBe('u1')
    expect(json).toContain('\n') // pretty-printed
  })
})

describe('multi-tab export', () => {
  it('rows from different tabs have distinct tab_name', () => {
    const verdA = new Map<string, Verdict>([['k1', 'like']])
    const verdB = new Map<string, Verdict>([['k2', 'dislike']])
    const rowA = traceExportRow(makeTrace('u1'), 'k1', 'Tab A', verdA)
    const rowB = traceExportRow(makeTrace('u2'), 'k2', 'Tab B', verdB)

    expect(rowA.tab_name).toBe('Tab A')
    expect(rowA.verdict).toBe('positive')
    expect(rowB.tab_name).toBe('Tab B')
    expect(rowB.verdict).toBe('negative')

    const tsv = rowsToTsv([rowA, rowB])
    const lines = tsv.split('\n')
    expect(lines[1]).toContain('Tab A')
    expect(lines[2]).toContain('Tab B')
  })

  it('TSV column union works across tabs with different extra fields', () => {
    const traceA = makeTrace('u1', 'com.a', 100, { device_name: 'pixel' })
    const traceB = makeTrace('u2', 'com.b', 200, { build_type: 'debug' })
    const rowA = traceExportRow(traceA, 'k1', 'A', new Map())
    const rowB = traceExportRow(traceB, 'k2', 'B', new Map())

    const tsv = rowsToTsv([rowA, rowB])
    const header = tsv.split('\n')[0]
    expect(header).toContain('device_name')
    expect(header).toContain('build_type')

    // Row A should have empty build_type
    const headerCols = header.split('\t')
    const row1Cols = tsv.split('\n')[1].split('\t')
    const btIdx = headerCols.indexOf('build_type')
    expect(row1Cols[btIdx]).toBe('')
  })

  it('JSON export preserves all verdict types', () => {
    const verdicts = new Map<string, Verdict>([
      ['k1', 'like'], ['k2', 'dislike'], ['k3', 'discard'],
    ])
    const rows = [
      traceExportRow(makeTrace('u1'), 'k1', 'T', verdicts),
      traceExportRow(makeTrace('u2'), 'k2', 'T', verdicts),
      traceExportRow(makeTrace('u3'), 'k3', 'T', verdicts),
      traceExportRow(makeTrace('u4'), 'k4', 'T', verdicts), // pending
    ]
    const parsed = JSON.parse(rowsToJson(rows))
    expect(parsed[0].verdict).toBe('positive')
    expect(parsed[1].verdict).toBe('negative')
    expect(parsed[2].verdict).toBe('discarded')
    expect(parsed[3].verdict).toBe('pending')
  })
})
