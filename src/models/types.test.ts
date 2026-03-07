import { describe, it, expect } from 'vitest'
import { DEFAULT_COLUMN_CONFIG, DEFAULT_SLICE_FIELD_CONFIG } from './types'

describe('DEFAULT_COLUMN_CONFIG', () => {
  it('trace_uuid aliases include common names', () => {
    const a = DEFAULT_COLUMN_CONFIG.trace_uuid.aliases
    expect(a).toContain('trace_uuid')
    expect(a).toContain('uuid')
    expect(a).toContain('id')
  })

  it('slices aliases include common names', () => {
    const a = DEFAULT_COLUMN_CONFIG.slices.aliases
    expect(a).toContain('slices')
    expect(a).toContain('json')
    expect(a).toContain('data')
    expect(a).toContain('base64')
  })

  it('trace_uuid fallback generates unique UUIDs', () => {
    const a = DEFAULT_COLUMN_CONFIG.trace_uuid.fallback()
    const b = DEFAULT_COLUMN_CONFIG.trace_uuid.fallback()
    expect(a).not.toBe(b)
    expect(a).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('package_name fallback returns unknown', () => {
    expect(DEFAULT_COLUMN_CONFIG.package_name.fallback()).toBe('unknown')
  })

  it('startup_dur fallback returns 0', () => {
    expect(DEFAULT_COLUMN_CONFIG.startup_dur.fallback()).toBe(0)
  })

  it('slices fallback returns empty array', () => {
    expect(DEFAULT_COLUMN_CONFIG.slices.fallback()).toEqual([])
  })
})

describe('DEFAULT_SLICE_FIELD_CONFIG', () => {
  it('ts aliases include common names', () => {
    const a = DEFAULT_SLICE_FIELD_CONFIG.ts.aliases
    expect(a).toContain('ts')
    expect(a).toContain('timestamp')
    expect(a).toContain('start')
  })

  it('dur aliases include common names', () => {
    const a = DEFAULT_SLICE_FIELD_CONFIG.dur.aliases
    expect(a).toContain('dur')
    expect(a).toContain('duration')
  })

  it('state fallback is null', () => {
    expect(DEFAULT_SLICE_FIELD_CONFIG.state.fallback).toBeNull()
  })
})
