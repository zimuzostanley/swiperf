import { describe, it, expect, beforeEach } from 'vitest'

import { DEFAULT_SLICE_FIELD_CONFIG, DEFAULT_COLUMN_CONFIG } from '../models/types'
import type { Slice, TraceEntry } from '../models/types'
import { S, activeCluster } from '../state'

describe('field alias resolution', () => {
  // Simulates the resolveField logic from Import.ts
  function resolveField<T>(obj: Record<string, any>, aliases: string[], fallback: T | (() => T)): T {
    for (const alias of aliases) { if (obj[alias] !== undefined) return obj[alias] as T }
    return typeof fallback === 'function' ? (fallback as () => T)() : fallback
  }

  it('resolves ts from "timestamp" alias', () => {
    const raw = { timestamp: 12345 }
    const result = resolveField(raw, DEFAULT_SLICE_FIELD_CONFIG.ts.aliases, DEFAULT_SLICE_FIELD_CONFIG.ts.fallback)
    expect(result).toBe(12345)
  })

  it('resolves ts from "start" alias', () => {
    const raw = { start: 999 }
    const result = resolveField(raw, DEFAULT_SLICE_FIELD_CONFIG.ts.aliases, DEFAULT_SLICE_FIELD_CONFIG.ts.fallback)
    expect(result).toBe(999)
  })

  it('falls back to default when no alias matches', () => {
    const raw = { unknown_field: 42 }
    const result = resolveField(raw, DEFAULT_SLICE_FIELD_CONFIG.ts.aliases, DEFAULT_SLICE_FIELD_CONFIG.ts.fallback)
    expect(result).toBe(0)
  })

  it('resolves slices from "json" alias', () => {
    const data = [{ ts: 0, dur: 100 }]
    const raw = { json: data }
    const result = resolveField(raw, DEFAULT_COLUMN_CONFIG.slices.aliases, DEFAULT_COLUMN_CONFIG.slices.fallback)
    expect(result).toEqual(data)
  })

  it('resolves package_name from "pkg" alias', () => {
    const raw = { pkg: 'com.example' }
    const result = resolveField(raw, DEFAULT_COLUMN_CONFIG.package_name.aliases, DEFAULT_COLUMN_CONFIG.package_name.fallback)
    expect(result).toBe('com.example')
  })

  it('priority: first matching alias wins', () => {
    const raw = { ts: 100, timestamp: 200, start: 300 }
    const result = resolveField(raw, DEFAULT_SLICE_FIELD_CONFIG.ts.aliases, DEFAULT_SLICE_FIELD_CONFIG.ts.fallback)
    expect(result).toBe(100) // 'ts' is first in aliases
  })
})

describe('delimited row parsing', () => {
  it('parses simple TSV', () => {
    const rows = parseDelimitedRows('a\tb\tc', '\t')
    expect(rows).toEqual([['a', 'b', 'c']])
  })

  it('parses simple CSV', () => {
    const rows = parseDelimitedRows('a,b,c', ',')
    expect(rows).toEqual([['a', 'b', 'c']])
  })

  it('handles quoted fields with delimiter inside', () => {
    const rows = parseDelimitedRows('"a,b",c', ',')
    expect(rows).toEqual([['a,b', 'c']])
  })

  it('handles quoted fields with tab inside', () => {
    const rows = parseDelimitedRows('"a\tb"\tc', '\t')
    expect(rows).toEqual([['a\tb', 'c']])
  })

  it('handles empty fields', () => {
    const rows = parseDelimitedRows('a,,c', ',')
    expect(rows).toEqual([['a', '', 'c']])
  })

  it('handles single field', () => {
    const rows = parseDelimitedRows('abc', ',')
    expect(rows).toEqual([['abc']])
  })

  it('handles escaped quotes (doubled "")', () => {
    const rows = parseDelimitedRows('a\t"he said ""hello"""\tb', '\t')
    expect(rows).toEqual([['a', 'he said "hello"', 'b']])
  })

  it('handles newlines inside quoted fields', () => {
    const rows = parseDelimitedRows('a\t"line1\nline2"\tb\nc\td\te', '\t')
    expect(rows).toEqual([
      ['a', 'line1\nline2', 'b'],
      ['c', 'd', 'e'],
    ])
  })

  it('handles multiple rows', () => {
    const rows = parseDelimitedRows('h1\th2\nh3\th4', '\t')
    expect(rows).toEqual([['h1', 'h2'], ['h3', 'h4']])
  })

  it('handles CRLF line endings', () => {
    const rows = parseDelimitedRows('a\tb\r\nc\td', '\t')
    expect(rows).toEqual([['a', 'b'], ['c', 'd']])
  })

  it('skips empty rows', () => {
    const rows = parseDelimitedRows('a\tb\n\nc\td', '\t')
    expect(rows).toEqual([['a', 'b'], ['c', 'd']])
  })

  it('handles JSON inside quoted TSV field with escaped quotes', () => {
    // This is how spreadsheets/tools export JSON in TSV: wrap in quotes, double internal quotes
    const json = '[{"ts":100,"dur":50,"state":"Running"}]'
    const quoted = json.replace(/"/g, '""')
    const tsv = `uuid\tslices\nuuid-1\t"${quoted}"`
    const rows = parseDelimitedRows(tsv, '\t')
    expect(rows).toHaveLength(2)
    expect(rows[1][1]).toBe(json)
    expect(JSON.parse(rows[1][1])).toEqual([{ts:100,dur:50,state:"Running"}])
  })

  it('handles complex JSON with nested quotes in TSV', () => {
    const pkg = '{"package_name":"com.foo","debuggable":false}'
    const slices = '[{"ts":100,"dur":50,"name":null,"state":"Sleeping","depth":null,"io_wait":null,"blocked_function":null}]'
    const quotedPkg = pkg.replace(/"/g, '""')
    const quotedSlices = slices.replace(/"/g, '""')
    const tsv = `trace_uuid\tpackage\tquantized_sequence\nuuid-1\t"${quotedPkg}"\t"${quotedSlices}"`
    const rows = parseDelimitedRows(tsv, '\t')
    expect(rows).toHaveLength(2)
    expect(JSON.parse(rows[1][1])).toEqual(JSON.parse(pkg))
    expect(JSON.parse(rows[1][2])).toEqual(JSON.parse(slices))
  })
})

// ── Integration tests using exported functions ──

import { normalizeTrace, normalizeSlice, resolvePackageName, arrayOfArraysToObjects, handleTextInput, repairJson, parseDelimitedRows } from './Import'

const SAMPLE_SLICES = [
  {"ts":1412782513363902,"dur":37000000.0,"name":null,"state":"Sleeping","depth":null,"io_wait":null,"blocked_function":null},
  {"ts":1412782550363902,"dur":3000000.0,"name":null,"state":"Running","depth":null,"io_wait":null,"blocked_function":null},
  {"ts":1412782553363902,"dur":3000000.0,"name":null,"state":"Runnable (Preempted)","depth":null,"io_wait":null,"blocked_function":null},
]

describe('normalizeTrace', () => {
  it('handles object with quantized_sequence string field', () => {
    const raw = {
      trace_uuid: 'abc-123',
      process_name: 'com.facebook.katana',
      package: '{"package_name":"com.facebook.katana","apk_version_code":"468818445"}',
      quantized_sequence: JSON.stringify(SAMPLE_SLICES),
      device_name: 'blue',
      build_id: 'UP1A.231005.007',
    }
    const result = normalizeTrace(raw)
    expect(result).not.toBeNull()
    expect(result!.trace_uuid).toBe('abc-123')
    expect(result!.package_name).toBe('com.facebook.katana')
    expect(result!.slices).toHaveLength(3)
    expect(result!.slices[0].state).toBe('Sleeping')
    expect(result!.extra?.device_name).toBe('blue')
    expect(result!.extra?.build_id).toBe('UP1A.231005.007')
  })

  it('extracts package_name from JSON-encoded package column', () => {
    const raw = {
      package: '{"package_name":"com.example.app","debuggable":false}',
      quantized_sequence: JSON.stringify(SAMPLE_SLICES),
    }
    const result = normalizeTrace(raw)
    expect(result).not.toBeNull()
    expect(result!.package_name).toBe('com.example.app')
  })

  it('prefers process_name over package', () => {
    const raw = {
      process_name: 'com.clean.name',
      package: '{"package_name":"com.json.name"}',
      quantized_sequence: JSON.stringify(SAMPLE_SLICES),
    }
    const result = normalizeTrace(raw)
    expect(result!.package_name).toBe('com.clean.name')
  })
})

describe('arrayOfArraysToObjects', () => {
  it('converts header+rows to objects', () => {
    const arr = [
      ['name', 'age'],
      ['Alice', 30],
      ['Bob', 25],
    ]
    const result = arrayOfArraysToObjects(arr)
    expect(result).toEqual([
      { name: 'Alice', age: 30 },
      { name: 'Bob', age: 25 },
    ])
  })
})

describe('repairJson', () => {
  it('closes unclosed string', () => {
    const broken = '["hello", "world'
    const fixed = repairJson(broken)
    expect(JSON.parse(fixed)).toEqual(['hello', 'world'])
  })

  it('closes unclosed arrays', () => {
    const broken = '[[1, 2], [3, 4]'
    const fixed = repairJson(broken)
    expect(JSON.parse(fixed)).toEqual([[1, 2], [3, 4]])
  })

  it('closes unclosed string inside nested structure', () => {
    // Simulates truncated quantized_sequence: string ends mid-value
    const broken = '[["a","b"],["v1","[{\\"ts\\":100}]'
    const fixed = repairJson(broken)
    const parsed = JSON.parse(fixed)
    expect(parsed).toHaveLength(2)
    expect(parsed[1][1]).toBe('[{"ts":100}]')
  })

  it('handles already-valid JSON', () => {
    const valid = '{"a": 1}'
    expect(repairJson(valid)).toBe('{"a": 1}')
  })

  it('repairs truncated array-of-arrays with escaped JSON string', () => {
    const slices = '[{\\"ts\\":100,\\"dur\\":50,\\"state\\":\\"Running\\"}]'
    const broken = `[["trace_uuid","quantized_sequence"],["abc",${JSON.stringify(JSON.stringify([{ts:100,dur:50,state:"Running"}]))}`
    // This is: [["trace_uuid","quantized_sequence"],["abc","[{\"ts\":100,...}]"
    // but missing the closing ]]
    const fixed = repairJson(broken)
    const parsed = JSON.parse(fixed)
    expect(parsed).toHaveLength(2)
  })
})

describe('JSON array-of-arrays import', () => {
  beforeEach(() => {
    S.clusters = []
    S.activeClusterId = null
    S.importMsg = null
  })

  it('imports array-of-arrays with quantized_sequence', () => {
    const data = [
      [
        "trace_address", "device_name", "build_id", "android_id", "upload_date",
        "suspend_count", "sched_duration_ns", "trace_duration_ns",
        "unique_session_name", "trace_causal_trigger", "trace_uuid",
        "process_name", "package", "startup_id", "startup_type", "quantized_sequence"
      ],
      [
        "/some/path.pftrace.gz", "blue", "UP1A.231005.007", "0", "2026-03-04",
        "0", "33076477002", "39583586157",
        "perfetto_aot_oem_public", "com.android.listed-app-start",
        "fdc407f2-66c0-b49c-3b8f-ac6f40bde663",
        "com.facebook.katana",
        '{"package_name":"com.facebook.katana","apk_version_code":"468818445","debuggable":false}',
        "15", "hot",
        JSON.stringify(SAMPLE_SLICES)
      ]
    ]
    handleTextInput(JSON.stringify(data), 'Test')
    expect(S.clusters).toHaveLength(1)
    const cl = S.clusters[0]
    expect(cl.traces).toHaveLength(1)
    const t = cl.traces[0]
    expect(t.trace.trace_uuid).toBe('fdc407f2-66c0-b49c-3b8f-ac6f40bde663')
    expect(t.trace.package_name).toBe('com.facebook.katana')
    expect(t.trace.slices).toHaveLength(3)
    expect(t.trace.extra?.device_name).toBe('blue')
    expect(t.trace.extra?.build_id).toBe('UP1A.231005.007')
    expect(t.trace.extra?.upload_date).toBe('2026-03-04')
    expect(t.trace.extra?.startup_type).toBe('hot')
  })

  it('imports truncated array-of-arrays JSON (repairs missing closing brackets)', () => {
    // Simulates paste truncation: the JSON ends mid-string without closing ", ] ]
    const slicesJson = JSON.stringify(SAMPLE_SLICES)
    const fullJson = JSON.stringify([
      ["trace_uuid", "process_name", "device_name", "quantized_sequence"],
      ["uuid-trunc", "com.truncated.app", "pixel", slicesJson],
    ])
    // Truncate: remove last 3 chars (the closing "]])
    const truncated = fullJson.slice(0, -3)
    handleTextInput(truncated, 'Truncated')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].traces[0].trace.trace_uuid).toBe('uuid-trunc')
    expect(S.clusters[0].traces[0].trace.package_name).toBe('com.truncated.app')
  })

  it('imports multiple rows in array-of-arrays', () => {
    const data = [
      ["trace_uuid", "process_name", "quantized_sequence"],
      ["uuid-1", "com.app1", JSON.stringify(SAMPLE_SLICES)],
      ["uuid-2", "com.app2", JSON.stringify(SAMPLE_SLICES)],
    ]
    handleTextInput(JSON.stringify(data), 'Multi')
    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].traces).toHaveLength(2)
    expect(S.clusters[0].traces[0].trace.package_name).toBe('com.app1')
    expect(S.clusters[0].traces[1].trace.package_name).toBe('com.app2')
  })
})

describe('TSV import', () => {
  beforeEach(() => {
    S.clusters = []
    S.activeClusterId = null
    S.importMsg = null
  })

  // Helper: build a TSV string with proper quoting
  function tsvQuote(val: string): string {
    if (val.includes('"') || val.includes('\t') || val.includes('\n')) {
      return '"' + val.replace(/"/g, '""') + '"'
    }
    return val
  }
  function buildTsv(headers: string[], ...rows: string[][]): string {
    return [headers.join('\t'), ...rows.map(r => r.map(tsvQuote).join('\t'))].join('\n')
  }

  it('imports basic TSV with unquoted JSON slices', () => {
    const slicesJson = JSON.stringify(SAMPLE_SLICES)
    const tsv = `trace_uuid\tprocess_name\tquantized_sequence\nuuid-1\tcom.test.app\t${slicesJson}`
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].traces).toHaveLength(1)
    expect(S.clusters[0].traces[0].trace.trace_uuid).toBe('uuid-1')
    expect(S.clusters[0].traces[0].trace.package_name).toBe('com.test.app')
    expect(S.clusters[0].traces[0].trace.slices).toHaveLength(3)
  })

  it('imports TSV with quoted JSON slices (escaped double quotes)', () => {
    const slicesJson = JSON.stringify(SAMPLE_SLICES)
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'quantized_sequence'],
      ['uuid-1', 'com.test.app', slicesJson],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters).toHaveLength(1)
    expect(S.clusters[0].traces[0].trace.slices).toHaveLength(3)
    expect(S.clusters[0].traces[0].trace.slices[0].state).toBe('Sleeping')
  })

  it('imports TSV with JSON-encoded package column', () => {
    const pkg = '{"package_name":"com.facebook.katana","apk_version_code":"468818445"}'
    const tsv = buildTsv(
      ['trace_uuid', 'package', 'quantized_sequence'],
      ['uuid-1', pkg, JSON.stringify(SAMPLE_SLICES)],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces[0].trace.package_name).toBe('com.facebook.katana')
  })

  it('imports TSV with all metadata columns', () => {
    const tsv = buildTsv(
      ['trace_address', 'device_name', 'build_id', 'upload_date', 'trace_uuid', 'process_name', 'package', 'startup_id', 'startup_type', 'quantized_sequence'],
      ['/some/path', 'blue', 'UP1A', '2026-03-04', 'uuid-1', 'com.test',
       '{"package_name":"com.test"}', '15', 'hot', JSON.stringify(SAMPLE_SLICES)],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    const t = S.clusters[0].traces[0]
    expect(t.trace.trace_uuid).toBe('uuid-1')
    expect(t.trace.package_name).toBe('com.test')
    expect(t.trace.extra?.device_name).toBe('blue')
    expect(t.trace.extra?.startup_type).toBe('hot')
    expect(t.trace.extra?.startup_id).toBe('15')
  })

  it('imports TSV with multiple rows', () => {
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'quantized_sequence'],
      ['uuid-1', 'com.app1', JSON.stringify(SAMPLE_SLICES)],
      ['uuid-2', 'com.app2', JSON.stringify(SAMPLE_SLICES)],
      ['uuid-3', 'com.app3', JSON.stringify(SAMPLE_SLICES)],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces).toHaveLength(3)
  })

  it('imports TSV with startup_dur_ms column (converts to ns)', () => {
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'startup_dur_ms', 'quantized_sequence'],
      ['uuid-1', 'com.test', '1500', JSON.stringify(SAMPLE_SLICES)],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces[0].trace.startup_dur).toBe(1500 * 1e6)
  })

  it('handles TSV with CRLF line endings', () => {
    const slicesJson = JSON.stringify(SAMPLE_SLICES)
    const tsv = `trace_uuid\tprocess_name\tquantized_sequence\r\nuuid-1\tcom.test\t${slicesJson}`
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters).toHaveLength(1)
  })

  it('imports same data as TSV and JSON with identical results', () => {
    const slicesJson = JSON.stringify(SAMPLE_SLICES)

    // JSON import
    const jsonData = [
      ['trace_uuid', 'process_name', 'device_name', 'quantized_sequence'],
      ['uuid-1', 'com.test', 'pixel', slicesJson],
    ]
    handleTextInput(JSON.stringify(jsonData), 'JSON')
    const jsonTrace = S.clusters[0].traces[0]

    // TSV import
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'device_name', 'quantized_sequence'],
      ['uuid-1', 'com.test', 'pixel', slicesJson],
    )
    handleTextInput(tsv, 'TSV')
    const tsvTrace = S.clusters[1].traces[0]

    expect(tsvTrace.trace.trace_uuid).toBe(jsonTrace.trace.trace_uuid)
    expect(tsvTrace.trace.package_name).toBe(jsonTrace.trace.package_name)
    expect(tsvTrace.trace.slices).toEqual(jsonTrace.trace.slices)
    expect(tsvTrace.trace.extra?.device_name).toBe(jsonTrace.trace.extra?.device_name)
  })

  it('handles TSV where JSON field has newlines inside quoted value', () => {
    // Some tools may format JSON with newlines inside a quoted TSV field
    const slices = '[\n{"ts":100,"dur":50,"state":"Running"}\n]'
    const tsv = `trace_uuid\tquantized_sequence\nuuid-1\t"${slices.replace(/"/g, '""')}"`
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces[0].trace.slices).toHaveLength(1)
  })

  it('skips rows with empty slices column gracefully', () => {
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'quantized_sequence'],
      ['uuid-1', 'com.app1', JSON.stringify(SAMPLE_SLICES)],
      ['uuid-2', 'com.app2', ''],
      ['uuid-3', 'com.app3', JSON.stringify(SAMPLE_SLICES)],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces).toHaveLength(2)
  })

  it('handles TSV with truncated JSON slices (auto-repair)', () => {
    const slicesJson = JSON.stringify(SAMPLE_SLICES)
    // Remove trailing ] — repairJson should close it
    const truncated = slicesJson.slice(0, -1)
    const tsv = buildTsv(
      ['trace_uuid', 'process_name', 'quantized_sequence'],
      ['uuid-1', 'com.test', truncated],
    )
    handleTextInput(tsv, 'TSV')
    expect(S.importMsg?.ok).toBe(true)
    expect(S.clusters[0].traces).toHaveLength(1)
  })
})
