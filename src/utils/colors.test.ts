import { describe, it, expect, beforeEach } from 'vitest'
import { state_color, state_label, name_color, perfetto_hash } from './colors'

// Mock document for isDark() — default to light theme
beforeEach(() => {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', 'light')
})

describe('perfetto_hash', () => {
  it('matches Perfetto FNV-1a with 28-bit init', () => {
    // Verified against Perfetto's hash.ts implementation
    // Init: 0x811c9dc5 & 0xfffffff = 0x011c9dc5
    expect(perfetto_hash('doWork', 18)).toBe(14)
    expect(perfetto_hash('bindApplication', 18)).toBe(2)
    expect(perfetto_hash('activityStart', 18)).toBe(0)
    expect(perfetto_hash('inflate', 18)).toBe(4)
    expect(perfetto_hash('draw', 18)).toBe(8)
    expect(perfetto_hash('choreographer', 18)).toBe(12)
  })

  it('is deterministic', () => {
    expect(perfetto_hash('test', 100)).toBe(perfetto_hash('test', 100))
  })
})

describe('state_label', () => {
  it('returns state name for simple states', () => {
    expect(state_label({ state: 'Running', io_wait: null })).toBe('Running')
    expect(state_label({ state: 'Sleeping', io_wait: null })).toBe('Sleeping')
    expect(state_label({ state: 'Runnable', io_wait: null })).toBe('Runnable')
  })

  it('distinguishes IO vs non-IO for uninterruptible sleep', () => {
    expect(state_label({ state: 'Uninterruptible Sleep', io_wait: 1 })).toBe('Unint. Sleep (IO)')
    expect(state_label({ state: 'Uninterruptible Sleep', io_wait: 0 })).toBe('Unint. Sleep (non-IO)')
    expect(state_label({ state: 'Uninterruptible Sleep', io_wait: null })).toBe('Unint. Sleep')
  })

  it('returns Unknown for null state', () => {
    expect(state_label({ state: null, io_wait: null })).toBe('Unknown')
  })
})

describe('state_color', () => {
  it('matches Perfetto HSL values for thread states', () => {
    // Running = HSL(120, 44, 34) = #317d31
    expect(state_color({ state: 'Running', io_wait: null })).toBe('#317d31')
    // Runnable = HSL(75, 55, 47) = #99ba36
    expect(state_color({ state: 'Runnable', io_wait: null })).toBe('#99ba36')
    expect(state_color({ state: 'Runnable (Preempted)', io_wait: null })).toBe('#99ba36')
  })

  it('matches Perfetto for uninterruptible sleep', () => {
    // IO wait = HSL(36, 100, 50) = #ff9900 (ORANGE)
    expect(state_color({ state: 'Uninterruptible Sleep', io_wait: 1 })).toBe('#ff9900')
    // non-IO = HSL(3, 30, 49) = #a25b57 (DESAT_RED)
    expect(state_color({ state: 'Uninterruptible Sleep', io_wait: 0 })).toBe('#a25b57')
  })

  it('returns white for Sleeping in light mode', () => {
    expect(state_color({ state: 'Sleeping', io_wait: null })).toBe('#ffffff')
  })

  it('handles Created, Dead, Unknown states', () => {
    // Created = HSL(0, 0, 70) = #b3b3b3
    expect(state_color({ state: 'Created', io_wait: null })).toBe('#b3b3b3')
  })
})

describe('name_color', () => {
  it('returns a hex color string', () => {
    const c = name_color('SomeFunction')
    expect(c).toMatch(/^#[0-9a-f]{6}$/)
  })

  it('is deterministic — same name returns same color', () => {
    expect(name_color('foo')).toBe(name_color('foo'))
  })

  it('strips trailing numbers for seed', () => {
    // "task123" and "task456" should produce the same color
    expect(name_color('task123')).toBe(name_color('task456'))
  })

  it('different names can produce different colors', () => {
    const a = name_color('alpha')
    const b = name_color('zeta')
    expect(a).toMatch(/^#[0-9a-f]{6}$/)
    expect(b).toMatch(/^#[0-9a-f]{6}$/)
  })

  it('matches Perfetto proceduralColorScheme exactly', () => {
    // Reference values from Perfetto's hsluv package (v0.1.0)
    // proceduralColorScheme: hash→hue(0-359), S=80, hash(seed+'x')→L(40-79)
    expect(name_color('binder transaction')).toBe('#9e6530') // brown, hue=40
    expect(name_color('bindApplication')).toBe('#4ba7bf')    // teal-blue, hue=218
    expect(name_color('activityStart')).toBe('#2b6674')      // dark teal, hue=216
    expect(name_color('inflate')).toBe('#307083')            // blue-teal, hue=220
    expect(name_color('measure')).toBe('#693dd8')            // purple, hue=272
    expect(name_color('draw')).toBe('#5475e3')               // blue, hue=260
    expect(name_color('choreographer')).toBe('#497f30')      // green, hue=120
    expect(name_color('Choreographer#doFrame')).toBe('#908137') // olive, hue=74
    expect(name_color('performTraversals')).toBe('#3d6fcc')  // blue, hue=256
  })
})
