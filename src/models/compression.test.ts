import { describe, it, expect } from 'vitest'
import { build_merge_cache, get_compressed } from './compression'
import type { Slice } from './types'

function makeSlice(ts: number, dur: number, state: string, name: string | null = null): Slice {
  return { ts, dur, name, state, depth: null, io_wait: null, blocked_function: null }
}

describe('build_merge_cache', () => {
  it('returns empty map for empty input', () => {
    const cache = build_merge_cache([])
    expect(cache.size).toBe(0)
  })

  it('caches the original length', () => {
    const slices = [makeSlice(0, 100, 'Running'), makeSlice(100, 200, 'Sleeping')]
    const cache = build_merge_cache(slices)
    expect(cache.has(2)).toBe(true)
    expect(cache.get(2)!.length).toBe(2)
  })

  it('produces levels down to 2 for multi-slice input', () => {
    const slices = [
      makeSlice(0, 100, 'Running'),
      makeSlice(100, 200, 'Sleeping'),
      makeSlice(300, 150, 'Running'),
      makeSlice(450, 50, 'Runnable'),
    ]
    const cache = build_merge_cache(slices)
    expect(cache.has(4)).toBe(true)
    expect(cache.has(2)).toBe(true)
  })

  it('preserves tsRel relative to first slice', () => {
    const slices = [makeSlice(1000, 100, 'Running'), makeSlice(1100, 200, 'Sleeping')]
    const cache = build_merge_cache(slices)
    const full = cache.get(2)!
    expect(full[0].tsRel).toBe(0)
    expect(full[1].tsRel).toBe(100)
  })

  it('merging identical states costs less than different states', () => {
    const same = [
      makeSlice(0, 100, 'Running'),
      makeSlice(100, 100, 'Running'),
      makeSlice(200, 100, 'Sleeping'),
    ]
    const cache = build_merge_cache(same)
    // At level 2, the two Running slices should merge first (lower cost)
    const merged = cache.get(2)!
    expect(merged.length).toBe(2)
    expect(merged[0].state).toBe('Running')
    expect(merged[0].dur).toBe(200)
  })
})

describe('get_compressed', () => {
  const slices = [
    makeSlice(0, 100, 'Running'),
    makeSlice(100, 200, 'Sleeping'),
    makeSlice(300, 150, 'Running'),
    makeSlice(450, 50, 'Runnable'),
  ]
  const cache = build_merge_cache(slices)

  it('returns full sequence when target >= origN', () => {
    const result = get_compressed(cache, 4, 4)
    expect(result.length).toBe(4)
  })

  it('returns full sequence when target > origN', () => {
    const result = get_compressed(cache, 4, 100)
    expect(result.length).toBe(4)
  })

  it('clamps target below 2 to 2', () => {
    const result = get_compressed(cache, 4, 0)
    expect(result.length).toBe(2)
  })

  it('returns closest level >= target', () => {
    const result = get_compressed(cache, 4, 3)
    expect(result.length).toBe(3)
  })
})
