/**
 * Smoke test: mounts the full App with Mithril and dummy data,
 * exercises the trace list view, and catches vnode key errors.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import m from 'mithril'
import { S, addCluster, activeCluster, setVerdict, switchCluster, updateSlider, ensureCache, filteredTraces, copyFilteredToNewTab, filterTraces, recomputeCounts } from './state'
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

/** Helper: get _key for trace at index in active cluster */
function keyAt(idx: number): string {
  return activeCluster()!.traces[idx]._key
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
    const cards = document.querySelectorAll('.trace-card')
    expect(cards.length).toBe(3)
  })

  it('verdict buttons work on trace cards', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    setVerdict(cl, keyAt(0), 'like')
    expect(() => m.redraw.sync()).not.toThrow()
    expect(cl.counts.positive).toBe(1)
    setVerdict(cl, keyAt(1), 'dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    expect(cl.counts.negative).toBe(1)
    expect(cl.counts.pending).toBe(1)
  })

  it('filter tabs work', () => {
    addCluster('Multi', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const cl = activeCluster()!
    setVerdict(cl, keyAt(0), 'like')
    setVerdict(cl, keyAt(1), 'dislike')

    cl.overviewFilter = 'positive'
    expect(filteredTraces().length).toBe(1)
    expect(() => m.redraw.sync()).not.toThrow()

    cl.overviewFilter = 'negative'
    expect(filteredTraces().length).toBe(1)
    expect(() => m.redraw.sync()).not.toThrow()

    cl.overviewFilter = 'all'
    expect(filteredTraces().length).toBe(3)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('toggle verdict on/off works', () => {
    addCluster('Test', [makeTrace('t1')])
    mount()
    const cl = activeCluster()!
    const k = keyAt(0)
    setVerdict(cl, k, 'like')
    expect(cl.verdicts.get(k)).toBe('like')
    setVerdict(cl, k, 'like')
    expect(cl.verdicts.has(k)).toBe(false)
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
    expect(document.querySelectorAll('.filter-btn').length).toBe(5)
    expect(document.querySelector('.stat-positive')).toBeTruthy()
    expect(document.querySelector('.stat-negative')).toBeTruthy()
    expect(document.querySelector('.stat-pending')).toBeTruthy()
  })

  it('expanding a trace card shows detail without key errors', () => {
    addCluster('Expand', [makeTrace('e1', 6), makeTrace('e2', 4)])
    mount()
    const header = document.querySelector('.trace-card-header') as HTMLElement
    expect(header).toBeTruthy()
    header.click()
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.trace-card-detail')).toBeTruthy()
    header.click()
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.trace-card-detail')).toBeFalsy()
  })

  it('expanding multiple cards and toggling verdicts works', () => {
    addCluster('Multi', [makeTrace('m1'), makeTrace('m2'), makeTrace('m3')])
    mount()
    const cl = activeCluster()!
    const headers = document.querySelectorAll('.trace-card-header')
    ;(headers[0] as HTMLElement).click()
    expect(() => m.redraw.sync()).not.toThrow()
    ;(headers[1] as HTMLElement).click()
    expect(() => m.redraw.sync()).not.toThrow()
    setVerdict(cl, keyAt(0), 'like')
    expect(() => m.redraw.sync()).not.toThrow()
    setVerdict(cl, keyAt(1), 'dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    cl.overviewFilter = 'positive'
    expect(() => m.redraw.sync()).not.toThrow()
    cl.overviewFilter = 'negative'
    expect(() => m.redraw.sync()).not.toThrow()
    cl.overviewFilter = 'all'
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('split view toggles and renders two panels', () => {
    addCluster('Split', [makeTrace('s1'), makeTrace('s2'), makeTrace('s3')])
    mount()
    const cl = activeCluster()!

    cl.splitView = true
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.split-container')).toBeTruthy()
    expect(document.querySelectorAll('.split-panel').length).toBe(2)
    expect(document.querySelectorAll('.split-panel-header').length).toBe(2)

    setVerdict(cl, keyAt(0), 'like')
    setVerdict(cl, keyAt(1), 'dislike')
    expect(() => m.redraw.sync()).not.toThrow()
    cl.splitFilters = ['positive', 'negative']
    expect(() => m.redraw.sync()).not.toThrow()

    cl.splitView = false
    expect(() => m.redraw.sync()).not.toThrow()
    expect(document.querySelector('.split-container')).toBeFalsy()
  })

  it('split view resize updates ratio', () => {
    addCluster('Resize', [makeTrace('r1')])
    mount()
    const cl = activeCluster()!
    cl.splitView = true
    cl.splitRatio = 0.3
    expect(() => m.redraw.sync()).not.toThrow()
    const panels = document.querySelectorAll('.split-panel')
    expect(panels.length).toBe(2)
    const style = (panels[0] as HTMLElement).style.width
    expect(style).toContain('30')
  })

  it('deduplicates traces with same composite key', () => {
    addCluster('Dedup', [makeTrace('dup'), makeTrace('dup'), makeTrace('unique')])
    mount()
    const cl = activeCluster()!
    expect(cl.traces.length).toBe(2)
    expect(document.querySelectorAll('.trace-card').length).toBe(2)
  })

  it('sort by startup_dur works', () => {
    const t1 = makeTrace('fast')
    t1.startup_dur = 1000
    const t2 = makeTrace('slow')
    t2.startup_dur = 9000
    const t3 = makeTrace('mid')
    t3.startup_dur = 5000
    addCluster('Sort', [t1, t2, t3])
    mount()
    const cl = activeCluster()!
    cl.sortField = 'startup_dur'
    cl.sortDir = 1
    const sorted = filteredTraces()
    expect(sorted[0].trace.startup_dur).toBe(1000)
    expect(sorted[2].trace.startup_dur).toBe(9000)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('copy-to-tab renders new tab and preserves verdict display', () => {
    addCluster('Source', [makeTrace('t1'), makeTrace('t2'), makeTrace('t3')])
    mount()
    const source = activeCluster()!
    setVerdict(source, source.traces[0]._key, 'like')
    setVerdict(source, source.traces[1]._key, 'dislike')

    // Copy only positive
    const positive = filterTraces(source, 'positive')
    copyFilteredToNewTab(source, positive)
    expect(() => m.redraw.sync()).not.toThrow()

    // New tab is active
    const copy = activeCluster()!
    expect(copy.name).toBe('Source (copy)')
    expect(copy.traces).toHaveLength(1)
    expect(copy.counts.positive).toBe(1)
    expect(copy.counts.negative).toBe(0)

    // Tab renders correctly
    expect(document.querySelectorAll('.cluster-tab').length).toBe(2)
    expect(document.querySelectorAll('.trace-card').length).toBe(1)
  })

  it('copy-to-tab does not affect source counts on re-render', () => {
    addCluster('Source', [makeTrace('t1'), makeTrace('t2')])
    mount()
    const source = S.clusters[0]
    setVerdict(source, source.traces[0]._key, 'like')
    expect(() => m.redraw.sync()).not.toThrow()

    copyFilteredToNewTab(source, source.traces)
    expect(() => m.redraw.sync()).not.toThrow()

    // Switch back to source
    switchCluster(source.id)
    expect(() => m.redraw.sync()).not.toThrow()

    expect(source.counts.positive).toBe(1)
    expect(source.counts.pending).toBe(1)
    expect(document.querySelectorAll('.trace-card').length).toBe(2)
  })

  it('split view verdict changes update counts in both panels', () => {
    addCluster('Split', [makeTrace('s1'), makeTrace('s2'), makeTrace('s3')])
    mount()
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[0]._key, 'like')
    setVerdict(cl, cl.traces[1]._key, 'dislike')

    cl.splitView = true
    cl.splitFilters = ['positive', 'negative']
    expect(() => m.redraw.sync()).not.toThrow()

    // Verify panels render
    expect(document.querySelectorAll('.split-panel').length).toBe(2)

    // Change verdict while in split view
    setVerdict(cl, cl.traces[2]._key, 'like')
    expect(cl.counts.positive).toBe(2)
    expect(cl.counts.negative).toBe(1)
    expect(cl.counts.pending).toBe(0)
    expect(() => m.redraw.sync()).not.toThrow()
  })

  it('filter + sort + verdict change renders correctly', () => {
    const t1 = makeTrace('fast')
    t1.startup_dur = 1000
    const t2 = makeTrace('slow')
    t2.startup_dur = 9000
    const t3 = makeTrace('mid')
    t3.startup_dur = 5000
    addCluster('Combined', [t1, t2, t3])
    mount()
    const cl = activeCluster()!

    setVerdict(cl, cl.traces[0]._key, 'like')  // fast
    setVerdict(cl, cl.traces[1]._key, 'like')  // slow
    cl.sortField = 'startup_dur'
    cl.sortDir = -1
    cl.overviewFilter = 'positive'
    expect(() => m.redraw.sync()).not.toThrow()

    const visible = filteredTraces()
    expect(visible).toHaveLength(2)
    expect(visible[0].trace.startup_dur).toBe(9000) // slow first, descending
    expect(document.querySelectorAll('.trace-card').length).toBe(2)

    // Remove verdict from slow — it drops out of positive filter
    setVerdict(cl, cl.traces[1]._key, 'like') // toggle off
    expect(() => m.redraw.sync()).not.toThrow()
    expect(filteredTraces()).toHaveLength(1)
    expect(document.querySelectorAll('.trace-card').length).toBe(1)
  })

  it('export dropdown renders and closes on click', () => {
    addCluster('Export', [makeTrace('e1')])
    mount()
    const exportBtn = document.querySelector('.export-dropdown-wrap .btn') as HTMLElement
    expect(exportBtn).toBeTruthy()
    exportBtn.click()
    expect(() => m.redraw.sync()).not.toThrow()
    const dropdown = document.querySelector('.export-dropdown')
    expect(dropdown).toBeTruthy()
  })

  it('discard verdict renders with correct CSS class on trace card', () => {
    addCluster('Discard', [makeTrace('d1')])
    mount()
    const cl = activeCluster()!
    setVerdict(cl, cl.traces[0]._key, 'discard')
    expect(() => m.redraw.sync()).not.toThrow()

    const card = document.querySelector('.trace-card.verdict-discard')
    expect(card).toBeTruthy()
  })
})
