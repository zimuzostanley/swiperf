import { describe, it, expect } from 'vitest'

// We test the pure functions from Import.ts indirectly by testing normalizeSlice/normalizeTrace
// and the delimited parser. Since these are not exported, we test the config and resolver logic.
import { DEFAULT_SLICE_FIELD_CONFIG, DEFAULT_COLUMN_CONFIG } from '../models/types'

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

describe('delimited line parsing', () => {
  // Replicates parseDelimitedLine from Import.ts
  function parseDelimitedLine(line: string, delimiter: string): string[] {
    const fields: string[] = []; let current = ''; let inQ = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (ch === '"') { inQ = !inQ; continue }
      if (ch === delimiter && !inQ) { fields.push(current); current = ''; continue }
      current += ch
    }
    fields.push(current); return fields
  }

  it('parses simple TSV', () => {
    expect(parseDelimitedLine('a\tb\tc', '\t')).toEqual(['a', 'b', 'c'])
  })

  it('parses simple CSV', () => {
    expect(parseDelimitedLine('a,b,c', ',')).toEqual(['a', 'b', 'c'])
  })

  it('handles quoted fields with delimiter inside', () => {
    expect(parseDelimitedLine('"a,b",c', ',')).toEqual(['a,b', 'c'])
  })

  it('handles quoted fields with tab inside', () => {
    expect(parseDelimitedLine('"a\tb"\tc', '\t')).toEqual(['a\tb', 'c'])
  })

  it('handles empty fields', () => {
    expect(parseDelimitedLine('a,,c', ',')).toEqual(['a', '', 'c'])
  })

  it('handles single field', () => {
    expect(parseDelimitedLine('abc', ',')).toEqual(['abc'])
  })
})
