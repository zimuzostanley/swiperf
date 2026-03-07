import { describe, it, expect } from 'vitest'
import { fmt_dur, fmt_pct } from './format'

describe('fmt_dur', () => {
  it('formats nanoseconds as microseconds', () => {
    expect(fmt_dur(500)).toBe('1 \u00b5s')
    expect(fmt_dur(1000)).toBe('1 \u00b5s')
    expect(fmt_dur(999999)).toBe('1000 \u00b5s')
  })

  it('formats as milliseconds', () => {
    expect(fmt_dur(1e6)).toBe('1.0 ms')
    expect(fmt_dur(1.5e6)).toBe('1.5 ms')
    expect(fmt_dur(999.9e6)).toBe('999.9 ms')
  })

  it('formats as seconds', () => {
    expect(fmt_dur(1e9)).toBe('1.000 s')
    expect(fmt_dur(2.5e9)).toBe('2.500 s')
  })

  it('handles zero', () => {
    expect(fmt_dur(0)).toBe('0 \u00b5s')
  })
})

describe('fmt_pct', () => {
  it('formats percentage', () => {
    expect(fmt_pct(50, 100)).toBe('50.0%')
    expect(fmt_pct(1, 3)).toBe('33.3%')
  })

  it('handles zero total without NaN', () => {
    expect(fmt_pct(0, 0)).toBe('0.0%')
    expect(fmt_pct(100, 0)).toBe('0.0%')
  })

  it('handles 100%', () => {
    expect(fmt_pct(100, 100)).toBe('100.0%')
  })
})
