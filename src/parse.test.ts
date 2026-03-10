// src/parse.test.ts — Tests for pure parsing functions
//
// These test the core parsing logic independently of mithril/state.
// Browser-level integration tests are in tests/browser.test.ts.

import { describe, it, expect } from 'vitest'
import {
  resolveField, normalizeSlice, normalizeTrace,
  resolvePackageName, arrayOfArraysToObjects,
  repairJson, parseDelimitedRows,
  parseText, parseJsonToTraces, parseDelimitedToTraces,
} from './parse'
import type { TraceEntry } from './models/types'

// ── Test data ──

const SLICES = [
  { ts: 100, dur: 50, name: null, state: 'Running', depth: null, io_wait: null, blocked_function: null },
  { ts: 150, dur: 30, name: 'doWork', state: 'Sleeping', depth: 1, io_wait: null, blocked_function: null },
  { ts: 180, dur: 20, name: null, state: 'Runnable (Preempted)', depth: null, io_wait: null, blocked_function: null },
]

function makeTraceObj(uuid: string, pkg: string, slices: any[] = SLICES): Record<string, any> {
  return {
    trace_uuid: uuid,
    process_name: pkg,
    quantized_sequence: JSON.stringify(slices),
    device_name: 'pixel',
    startup_type: 'cold',
  }
}

// ── parseText (unified entry point) ──

describe('parseText', () => {
  it('parses JSON array of trace objects', () => {
    const json = JSON.stringify([makeTraceObj('u1', 'com.a'), makeTraceObj('u2', 'com.b')])
    const traces = parseText(json)
    expect(traces).toHaveLength(2)
    expect(traces[0].trace_uuid).toBe('u1')
    expect(traces[1].trace_uuid).toBe('u2')
    expect(traces[0].slices).toHaveLength(3)
  })

  it('parses JSON single trace object', () => {
    const json = JSON.stringify(makeTraceObj('u1', 'com.a'))
    const traces = parseText(json)
    expect(traces).toHaveLength(1)
    expect(traces[0].package_name).toBe('com.a')
  })

  it('parses JSON array of raw slices', () => {
    const json = JSON.stringify(SLICES)
    const traces = parseText(json)
    expect(traces).toHaveLength(1)
    expect(traces[0].slices).toHaveLength(3)
    expect(traces[0].package_name).toBe('unknown')
  })

  it('parses array-of-arrays JSON', () => {
    const data = [
      ['trace_uuid', 'process_name', 'device_name', 'quantized_sequence'],
      ['u1', 'com.a', 'pixel', JSON.stringify(SLICES)],
      ['u2', 'com.b', 'pixel', JSON.stringify(SLICES)],
    ]
    const traces = parseText(JSON.stringify(data))
    expect(traces).toHaveLength(2)
    expect(traces[0].trace_uuid).toBe('u1')
    expect(traces[1].trace_uuid).toBe('u2')
    expect(traces[0].extra?.device_name).toBe('pixel')
  })

  it('parses TSV with tab delimiter', () => {
    const tsv = `trace_uuid\tprocess_name\tquantized_sequence\nu1\tcom.a\t${JSON.stringify(SLICES)}`
    const traces = parseText(tsv)
    expect(traces).toHaveLength(1)
    expect(traces[0].trace_uuid).toBe('u1')
    expect(traces[0].package_name).toBe('com.a')
  })

  it('parses CSV with comma delimiter', () => {
    const slicesQuoted = '"' + JSON.stringify(SLICES).replace(/"/g, '""') + '"'
    const csv = `trace_uuid,process_name,quantized_sequence\nu1,com.a,${slicesQuoted}`
    const traces = parseText(csv)
    expect(traces).toHaveLength(1)
    expect(traces[0].trace_uuid).toBe('u1')
  })

  it('returns empty array for empty input', () => {
    expect(parseText('')).toEqual([])
    expect(parseText('  ')).toEqual([])
  })

  it('repairs truncated JSON', () => {
    const json = JSON.stringify([makeTraceObj('u1', 'com.a')])
    const truncated = json.slice(0, -1) // remove trailing ]
    const traces = parseText(truncated)
    expect(traces).toHaveLength(1)
    expect(traces[0].trace_uuid).toBe('u1')
  })

  it('handles startup_dur_ms (ms → ns conversion)', () => {
    const obj = {
      trace_uuid: 'u1',
      process_name: 'com.a',
      startup_dur_ms: '1500',
      quantized_sequence: JSON.stringify(SLICES),
    }
    const traces = parseText(JSON.stringify([obj]))
    expect(traces[0].startup_dur).toBe(1500 * 1e6)
  })

  it('handles JSON-encoded package column', () => {
    const obj = {
      trace_uuid: 'u1',
      package: '{"package_name":"com.real.name","debug":false}',
      quantized_sequence: JSON.stringify(SLICES),
    }
    const traces = parseText(JSON.stringify([obj]))
    expect(traces[0].package_name).toBe('com.real.name')
  })

  it('prefers process_name over package alias', () => {
    const obj = {
      trace_uuid: 'u1',
      process_name: 'com.process',
      package: '{"package_name":"com.pkg"}',
      quantized_sequence: JSON.stringify(SLICES),
    }
    const traces = parseText(JSON.stringify([obj]))
    expect(traces[0].package_name).toBe('com.process')
  })

  it('calls progress callback during large parse', () => {
    const items = Array.from({ length: 25 }, (_, i) =>
      makeTraceObj(`u${i}`, `com.app${i}`),
    )
    const progress: string[] = []
    parseText(JSON.stringify(items), p => progress.push(p.message))
    expect(progress.length).toBeGreaterThan(0)
    expect(progress.some(m => m.includes('Processing trace'))).toBe(true)
  })
})

// ── parseDelimitedToTraces ──

describe('parseDelimitedToTraces', () => {
  function tsvQuote(val: string): string {
    if (val.includes('"') || val.includes('\t') || val.includes('\n')) {
      return '"' + val.replace(/"/g, '""') + '"'
    }
    return val
  }

  it('handles quoted JSON with escaped double quotes', () => {
    const slicesJson = JSON.stringify(SLICES)
    const tsv = `trace_uuid\tquantized_sequence\nu1\t${tsvQuote(slicesJson)}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(1)
    expect(traces[0].slices).toHaveLength(3)
  })

  it('handles multiple rows', () => {
    const rows = Array.from({ length: 5 }, (_, i) =>
      `u${i}\tcom.app${i}\t${JSON.stringify(SLICES)}`,
    ).join('\n')
    const tsv = `trace_uuid\tprocess_name\tquantized_sequence\n${rows}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(5)
  })

  it('skips rows with empty slices', () => {
    const tsv = `trace_uuid\tquantized_sequence\nu1\t${JSON.stringify(SLICES)}\nu2\t\nu3\t${JSON.stringify(SLICES)}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(2)
  })

  it('handles base64-encoded slices', () => {
    const slicesJson = JSON.stringify(SLICES)
    const b64 = btoa(slicesJson)
    const tsv = `trace_uuid\tquantized_sequence\nu1\t${b64}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(1)
    expect(traces[0].slices).toHaveLength(3)
  })

  it('throws if no slices column found', () => {
    const tsv = 'name\tvalue\na\t1'
    expect(() => parseDelimitedToTraces(tsv, '\t')).toThrow('Need a column matching')
  })

  it('throws if only header row', () => {
    const tsv = 'trace_uuid\tquantized_sequence'
    expect(() => parseDelimitedToTraces(tsv, '\t')).toThrow('Need header + data')
  })

  it('handles CRLF line endings', () => {
    const tsv = `trace_uuid\tquantized_sequence\r\nu1\t${JSON.stringify(SLICES)}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(1)
  })

  it('matches space-separated display-name headers to underscore aliases', () => {
    const tsv = `Trace Uuid\tPackage\tStartup Dur Ms\tQuantized Sequence\nu1\tcom.app\t1500\t${JSON.stringify(SLICES)}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(1)
    expect(traces[0].trace_uuid).toBe('u1')
    expect(traces[0].package_name).toBe('com.app')
    expect(traces[0].startup_dur).toBe(1500 * 1e6) // ms → ns
  })

  it('parses real APC console TSV with display-name headers', () => {
    const slicesJson = JSON.stringify([
      { ts: '2002315967077879', dur: 2000000, io_wait: null, name: null, blocked_function: null, state: 'Running', token: 'state:Running' },
      { ts: '2002315969077879', dur: 2000000, io_wait: null, name: null, blocked_function: null, state: 'Runnable (Preempted)', token: 'state:Runnable (Preempted)' },
    ])
    const headers = [
      'Neighbor Apc Link Markdown', 'Cosine Similarity', 'Package',
      'Startup Dur Ms', 'Startup Type', 'Device Name', 'Build Id',
      'Upload Date', 'Trace Uuid', 'Quantized Sequence',
    ].join('\t')
    const row = [
      '[link](https://example.com)', '0.982984888112673', 'com.zhiliaoapp.musically',
      '4792.679462', 'cold', 'V2207', 'UP1A.231005.007_IN',
      '2026-03-08', '231cc60e-2892-a685-3b8f-ac6f40bde663', slicesJson,
    ].join('\t')
    const tsv = headers + '\n' + row
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces).toHaveLength(1)
    expect(traces[0].trace_uuid).toBe('231cc60e-2892-a685-3b8f-ac6f40bde663')
    expect(traces[0].package_name).toBe('com.zhiliaoapp.musically')
    expect(traces[0].startup_dur).toBe(4792.679462 * 1e6) // ms → ns
    expect(traces[0].slices).toHaveLength(2)
    expect(traces[0].slices[0].state).toBe('Running')
    expect(traces[0].slices[1].state).toBe('Runnable (Preempted)')
    // Extra fields preserved with normalized keys
    expect(traces[0].extra?.['startup_type']).toBe('cold')
    expect(traces[0].extra?.['device_name']).toBe('V2207')
    expect(traces[0].extra?.['upload_date']).toBe('2026-03-08')
  })

  it('reports progress', () => {
    const rows = Array.from({ length: 15 }, (_, i) =>
      `u${i}\t${JSON.stringify(SLICES)}`,
    ).join('\n')
    const tsv = `trace_uuid\tquantized_sequence\n${rows}`
    const progress: string[] = []
    parseDelimitedToTraces(tsv, '\t', p => progress.push(p.message))
    expect(progress.length).toBeGreaterThan(0)
  })
})

// ── repairJson edge cases ──

describe('repairJson edge cases', () => {
  it('repairs object with missing closing brace', () => {
    const broken = '{"a": 1, "b": 2'
    expect(JSON.parse(repairJson(broken))).toEqual({ a: 1, b: 2 })
  })

  it('repairs nested arrays', () => {
    const broken = '[[1, 2], [3'
    const fixed = JSON.parse(repairJson(broken))
    expect(fixed[0]).toEqual([1, 2])
    expect(fixed[1]).toEqual([3])
  })

  it('handles escaped quotes in strings', () => {
    const broken = '{"a": "he said \\"hi"'
    const fixed = repairJson(broken)
    // Should close the string and object
    expect(() => JSON.parse(fixed)).not.toThrow()
  })

  it('is a no-op for valid JSON', () => {
    const valid = '{"a": [1, 2, 3]}'
    expect(repairJson(valid)).toBe(valid)
  })
})

// ── parseDelimitedRows edge cases ──

describe('parseDelimitedRows edge cases', () => {
  it('handles field with only quotes', () => {
    const rows = parseDelimitedRows('a\t""\tb', '\t')
    expect(rows).toEqual([['a', '', 'b']])
  })

  it('handles mixed quoted and unquoted', () => {
    const rows = parseDelimitedRows('a\t"b\tc"\td', '\t')
    expect(rows).toEqual([['a', 'b\tc', 'd']])
  })

  it('handles trailing newline', () => {
    const rows = parseDelimitedRows('a\tb\n', '\t')
    expect(rows).toEqual([['a', 'b']])
  })

  it('handles multiple empty lines', () => {
    const rows = parseDelimitedRows('a\tb\n\n\nc\td', '\t')
    expect(rows).toEqual([['a', 'b'], ['c', 'd']])
  })
})

// ── trace_address / UUID extraction ──

describe('trace_address UUID extraction', () => {
  it('extracts UUID from a file path', () => {
    const result = normalizeTrace({
      trace_address: '/some/path/a1b2c3d4-e5f6-7890-abcd-ef1234567890.pftrace.gz',
      slices: [{ ts: 0, dur: 1 }],
    })
    expect(result!.trace_uuid).toBe('a1b2c3d4-e5f6-7890-abcd-ef1234567890')
  })

  it('uses trace_uuid directly when no path separator', () => {
    const result = normalizeTrace({
      trace_uuid: 'my-simple-id',
      slices: [{ ts: 0, dur: 1 }],
    })
    expect(result!.trace_uuid).toBe('my-simple-id')
  })

  it('prefers trace_uuid over trace_address (alias order)', () => {
    const result = normalizeTrace({
      trace_uuid: 'direct-uuid',
      trace_address: '/path/to/a1b2c3d4-e5f6-7890-abcd-ef1234567890.pftrace',
      slices: [{ ts: 0, dur: 1 }],
    })
    expect(result!.trace_uuid).toBe('direct-uuid')
  })

  it('extracts UUID from trace_address in TSV', () => {
    const addr = '/path/to/a1b2c3d4-e5f6-7890-abcd-ef1234567890.pftrace.gz'
    const tsv = `trace_address\tquantized_sequence\n${addr}\t${JSON.stringify(SLICES)}`
    const traces = parseDelimitedToTraces(tsv, '\t')
    expect(traces[0].trace_uuid).toBe('a1b2c3d4-e5f6-7890-abcd-ef1234567890')
  })

  it('falls back to basename without extension if no UUID in path', () => {
    const result = normalizeTrace({
      trace_address: '/some/path/my-trace-file.pftrace.gz',
      slices: [{ ts: 0, dur: 1 }],
    })
    expect(result!.trace_uuid).toBe('my-trace-file')
  })
})

// ── normalizeTrace edge cases ──

describe('normalizeTrace edge cases', () => {
  it('returns null for object with no slices', () => {
    expect(normalizeTrace({ trace_uuid: 'u1' })).toBeNull()
  })

  it('returns null for empty slices array', () => {
    expect(normalizeTrace({ trace_uuid: 'u1', slices: [] })).toBeNull()
  })

  it('returns null for invalid base64', () => {
    expect(normalizeTrace({ trace_uuid: 'u1', slices: '!!!invalid!!!' })).toBeNull()
  })

  it('preserves extra fields', () => {
    const result = normalizeTrace({
      trace_uuid: 'u1',
      slices: [{ ts: 0, dur: 1 }],
      custom_field: 'hello',
      another: 42,
    })
    expect(result).not.toBeNull()
    expect(result!.extra?.custom_field).toBe('hello')
    expect(result!.extra?.another).toBe(42)
  })

  it('handles slices as JSON string', () => {
    const result = normalizeTrace({
      trace_uuid: 'u1',
      quantized_sequence: JSON.stringify([{ ts: 0, dur: 100, state: 'Running' }]),
    })
    expect(result).not.toBeNull()
    expect(result!.slices[0].state).toBe('Running')
  })
})
