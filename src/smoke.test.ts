/**
 * Smoke test: mounts the full App with Mithril and dummy data,
 * exercises every view mode, and catches vnode key errors.
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import m from 'mithril'
import { S, addCluster, activeCluster, navigate, setVerdict, switchCluster, updateSlider, currentTrace, ensureCache } from './state'
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
    // Ensure #app exists
    if (!document.getElementById('app')) {
      const div = document.createElement('div')
      div.id = 'app'
      document.body.appendChild(div)
    }
    // Ensure #tip exists (Timeline needs it)
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
    // Should show timeline section
    expect(document.querySelector('.single-view')).toBeTruthy()
    // Cluster tab should appear
    expect(document.querySelector('.cluster-tab')).toBeTruthy()
  })

  it('renders multiple traces in single view without errors', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    expect(() => mount()).not.toThrow()
    // Nav bar should show for multi-trace
    expect(document.querySelector('.nav-bar')).toBeTruthy()
    // Navigate forward
    navigate(1)
    expect(() => m.redraw.sync()).not.toThrow()
    navigate(1)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('switches to overview mode without errors', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    cl.viewMode = 'overview'
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.overview-scroll')).toBeTruthy()
  })

  it('switches to report mode without errors', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    cl.viewMode = 'report'
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.report-header')).toBeTruthy()
  })

  it('report with liked traces renders without errors', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    setVerdict('like') // likes t1
    const cl = activeCluster()!
    cl.viewMode = 'report'
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.report-content')).toBeTruthy()
  })

  it('verdict toggle and navigation cycle works', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    // Like first trace (auto-advances)
    setVerdict('like')
    expect(() => m.redraw.sync()).not.toThrow()
    // Dislike second trace
    setVerdict('dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    // Switch to overview
    const cl = activeCluster()!
    cl.viewMode = 'overview'
    expect(() => m.redraw.sync()).not.toThrow()
    // Filter to liked
    cl.overviewFilter = 'liked'
    expect(() => m.redraw.sync()).not.toThrow()
    // Filter to disliked
    cl.overviewFilter = 'disliked'
    expect(() => m.redraw.sync()).not.toThrow()
    // Filter to pending
    cl.overviewFilter = 'pending'
    expect(() => m.redraw.sync()).not.toThrow()
    // Back to all
    cl.overviewFilter = 'all'
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('multiple clusters switch without errors', () => {
    addCluster('Cluster A', [makeTrace('a1'), makeTrace('a2')])
    const idA = S.clusters[0].id
    addCluster('Cluster B', [makeTrace('b1'), makeTrace('b2'), makeTrace('b3')])
    const idB = S.clusters[1].id
    mount()
    // Should be on cluster B
    expect(activeCluster()!.name).toBe('Cluster B')
    expect(document.querySelectorAll('.cluster-tab').length).toBe(2)

    // Switch to A
    switchCluster(idA)
    expect(() => m.redraw.sync()).not.toThrow()
    expect(activeCluster()!.name).toBe('Cluster A')

    // Switch back to B
    switchCluster(idB)
    expect(() => m.redraw.sync()).not.toThrow()

    // Set B to overview
    activeCluster()!.viewMode = 'overview'
    expect(() => m.redraw.sync()).not.toThrow()

    // Switch to A while B is in overview — A should still be single
    switchCluster(idA)
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.single-view')).toBeTruthy()

    // Switch back to B — should still be in overview
    switchCluster(idB)
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.overview-scroll')).toBeTruthy()
  })

  it('all view mode transitions work without key errors', () => {
    addCluster('Test', [makeTrace('t1'), makeTrace('t2')])
    mount()
    const cl = activeCluster()!

    // single -> overview -> report -> single -> report -> overview -> single
    const transitions: Array<'single' | 'overview' | 'report'> = [
      'overview', 'report', 'single', 'report', 'overview', 'single'
    ]
    for (const mode of transitions) {
      cl.viewMode = mode
      expect(() => m.redraw.sync()).not.toThrow()
    }
  })

  it('slider update changes currentSeq and re-renders without errors', () => {
    addCluster('Slider', [makeTrace('s1', 10)])
    mount()
    const ts = currentTrace()!
    ensureCache(ts)
    const origLen = ts.currentSeq.length

    // Move slider to compress
    updateSlider(ts, 3)
    expect(ts.currentSeq.length).toBeLessThanOrEqual(3)
    expect(ts.currentSeq.length).toBeLessThan(origLen)
    expect(() => m.redraw.sync()).not.toThrow()

    // Move slider back to full
    updateSlider(ts, 10)
    expect(ts.currentSeq.length).toBe(origLen)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('theme toggle re-renders without errors', () => {
    addCluster('Theme', [makeTrace('th1', 8)])
    mount()
    // Start in light
    document.documentElement.setAttribute('data-theme', 'light')
    expect(() => m.redraw.sync()).not.toThrow()
    // Toggle to dark
    document.documentElement.setAttribute('data-theme', 'dark')
    expect(() => m.redraw.sync()).not.toThrow()
    // Toggle back
    document.documentElement.setAttribute('data-theme', 'light')
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('single-trace cluster shows verdict buttons', () => {
    addCluster('Single', [makeTrace('solo-1')])
    mount()
    // Verdict buttons should be present even for single trace
    const verdictBtns = document.querySelectorAll('.verdict-btn')
    expect(verdictBtns.length).toBeGreaterThanOrEqual(2)
    // Nav bar should not show navigation arrows for single trace
    expect(document.querySelector('.nav-group')).toBeFalsy()
  })
})
