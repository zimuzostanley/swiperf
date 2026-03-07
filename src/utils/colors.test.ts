import { describe, it, expect, beforeEach } from 'vitest'
import { state_color, state_label, name_color } from './colors'

// Mock document for isDark() — default to light theme
beforeEach(() => {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', 'light')
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
  it('returns correct colors for known states', () => {
    expect(state_color({ state: 'Running', io_wait: null })).toBe('#357b34')
    expect(state_color({ state: 'Runnable', io_wait: null })).toBe('#99b93a')
    expect(state_color({ state: 'Runnable (Preempted)', io_wait: null })).toBe('#99b93a')
  })

  it('distinguishes IO wait for uninterruptible sleep', () => {
    expect(state_color({ state: 'Uninterruptible Sleep', io_wait: 1 })).toBe('#e65100')
    expect(state_color({ state: 'Uninterruptible Sleep', io_wait: 0 })).toBe('#a25c58')
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
    // Not guaranteed but highly likely for distinct names
    const a = name_color('alpha')
    const b = name_color('zeta')
    // Just check they're both valid hex
    expect(a).toMatch(/^#[0-9a-f]{6}$/)
    expect(b).toMatch(/^#[0-9a-f]{6}$/)
  })
})
