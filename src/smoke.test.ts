/**
 * Smoke test: mounts the full App with Mithril and dummy data,
 * exercises the trace list view, and catches vnode key errors.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import m from 'mithril'
import { S, addCluster, activeCluster, setVerdict, switchCluster, updateSlider, ensureCache, filteredTraces } from './state'
import { App } from './components/App'
import type { TraceEntry } from './models/types'

function makeTrace(uuid: string, sliceCount = 5): TraceEntry {
  const slices = Array.from({ length: sliceCount }, (_, i) => ({
    ts: i * 1000000, dur: 1000000, name: i % 2 === 0 ? 'func_' + i : null,
    state: i % 3 === 0 ? 'Running' : i % 3 === 1 ? 'Sleeping' : 'Runnable',
    depth: 0, io_wait: null, blocked_function: null,
  }))
  return { trace_uuid: uuid, package_name: 'com.test.app', startup_dur: 5000000, slices }
}

function resetState() {
  S.clusters = []
  S.activeClusterId = null
  S.importMsg = null
}

function mount() {
  const root = document.getElementById('app')!
  m.mount(root, App)
  m.redraw.sync()
}

function unmount() {
  const root = document.getElementById('app')!
  m.mount(root, null)
}

describe('smoke test — full app mount with dummy data', () => {
  beforeEach(() => {
    resetState()
    document.documentElement.setAttribute('data-theme', 'light')
    if (!document.getElementById('app')) {
      const div = document.createElement('div')
      div.id = 'app'
      document.body.appendChild(div)
    }
    if (!document.getElementById('tip')) {
      const tip = document.createElement('div')
      tip.id = 'tip'
      tip.className = 'tooltip'
      document.body.appendChild(tip)
    }
  })

  afterEach(() => {
    unmount()
  })

  it('renders empty state without errors', () => {
    expect(() => mount()).not.toThrow()
    expect(document.querySelector('.shell')).toBeTruthy()
    expect(document.querySelector('h1')!.textContent).toBe('SwiPerf')
  })

  it('renders single trace without errors', () => {
    addCluster('Test', [makeTrace('single-1')])
    expect(() => mount()).not.toThrow()
    expect(document.querySelector('.trace-list')).toBeTruthy()
    expect(document.querySelector('.cluster-tab')).toBeTruthy()
  })

  it('renders multiple traces in trace list without errors', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    expect(() => mount()).not.toThrow()
    // Should show trace cards
    const cards = document.querySelectorAll('.trace-card')
    expect(cards.length).toBe(3)
  })

  it('verdict buttons work on trace cards', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    // Mark t1 as positive
    setVerdict(cl, 't1', 'like')
    expect(() => m.redraw.sync()).not.toThrow()
    expect(cl.counts.positive).toBe(1)
    // Mark t2 as negative
    setVerdict(cl, 't2', 'dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    expect(cl.counts.negative).toBe(1)
    expect(cl.counts.pending).toBe(1)
  })

  it('filter tabs work', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    setVerdict(cl, 't1', 'like')
    setVerdict(cl, 't2', 'dislike')

    // Filter to positive
    cl.overviewFilter = 'positive'
    expect(filteredTraces().length).toBe(1)
    expect(() => m.redraw.sync()).not.toThrow()

    // Filter to negative
    cl.overviewFilter = 'negative'
    expect(filteredTraces().length).toBe(1)
    expect(() => m.redraw.sync()).not.toThrow()

    // Back to all
    cl.overviewFilter = 'all'
    expect(filteredTraces().length).toBe(3)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('toggle verdict on/off works', () => {
    addCluster('Test', [makeTrace('t1')])
    mount()
    const cl = activeCluster()!
    // Set positive
    setVerdict(cl, 't1', 'like')
    expect(cl.verdicts.get('t1')).toBe('like')
    // Toggle off (same verdict again)
    setVerdict(cl, 't1', 'like')
    expect(cl.verdicts.has('t1')).toBe(false)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('multiple clusters switch without errors', () => {
    addCluster('Cluster A', [makeTrace('a1'), makeTrace('a2')])
    const idA = S.clusters[0].id
    addCluster('Cluster B', [makeTrace('b1'), makeTrace('b2'), makeTrace('b3')])
    const idB = S.clusters[1].id
    mount()
    expect(activeCluster()!.name).toBe('Cluster B')
    expect(document.querySelectorAll('.cluster-tab').length).toBe(2)

    switchCluster(idA)
    expect(() => m.redraw.sync()).not.toThrow()
    expect(activeCluster()!.name).toBe('Cluster A')

    switchCluster(idB)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('slider update changes currentSeq and re-renders', () => {
    addCluster('Slider', [makeTrace('s1', 10)])
    mount()
    const ts = activeCluster()!.traces[0]
    ensureCache(ts)
    const origLen = ts.currentSeq.length

    updateSlider(ts, 3)
    expect(ts.currentSeq.length).toBeLessThanOrEqual(3)
    expect(ts.currentSeq.length).toBeLessThan(origLen)
    expect(() => m.redraw.sync()).not.toThrow()

    updateSlider(ts, 10)
    expect(ts.currentSeq.length).toBe(origLen)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('theme toggle re-renders without errors', () => {
    addCluster('Theme', [makeTrace('th1', 8)])
    mount()
    document.documentElement.setAttribute('data-theme', 'dark')
    expect(() => m.redraw.sync()).not.toThrow()
    document.documentElement.setAttribute('data-theme', 'light')
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('single-trace cluster shows verdict buttons', () => {
    addCluster('Single', [makeTrace('solo-1')])
    mount()
    const verdictBtns = document.querySelectorAll('.verdict-btn-sm')
    expect(verdictBtns.length).toBeGreaterThanOrEqual(2)
  })

  it('trace list toolbar shows filter buttons and stats', () => {
    addCluster('Test', [makeTrace('t1'), makeTrace('t2')])
    mount()
    expect(document.querySelectorAll('.filter-btn').length).toBe(3)
    expect(document.querySelector('.stat-positive')).toBeTruthy()
    expect(document.querySelector('.stat-negative')).toBeTruthy()
    expect(document.querySelector('.stat-pending')).toBeTruthy()
  })

  it('expanding a trace card shows detail without key errors', () => {
    addCluster('Expand', [makeTrace('e1', 6), makeTrace('e2', 4)])
    mount()
    // Click the first card header to expand
    const header = document.querySelector('.trace-card-header') as HTMLElement
    expect(header).toBeTruthy()
    header.click()
    expect(() => m.redraw.sync()).not.toThrow()
    // Detail should now be visible
    expect(document.querySelector('.trace-card-detail')).toBeTruthy()
    // Collapse it
    header.click()
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.trace-card-detail')).toBeFalsy()
  })

  it('expanding multiple cards and toggling verdicts works', () => {
    addCluster('Multi', [makeTrace('m1'), makeTrace('m2'), makeTrace('m3')])
    mount()
    const cl = activeCluster()!
    // Expand first two cards
    const headers = document.querySelectorAll('.trace-card-header')
    ;(headers[0] as HTMLElement).click()
    expect(() => m.redraw.sync()).not.toThrow()
    ;(headers[1] as HTMLElement).click()
    expect(() => m.redraw.sync()).not.toThrow()
    // Set verdicts while expanded
    setVerdict(cl, 'm1', 'like')
    expect(() => m.redraw.sync()).not.toThrow()
    setVerdict(cl, 'm2', 'dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    // Switch filter to positive
    cl.overviewFilter = 'positive'
    expect(() => m.redraw.sync()).not.toThrow()
    // Switch to negative
    cl.overviewFilter = 'negative'
    expect(() => m.redraw.sync()).not.toThrow()
    // Back to all
    cl.overviewFilter = 'all'
    expect(() => m.redraw.sync()).not.toThrow()
  })
})
