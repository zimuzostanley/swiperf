import m from 'mithril'
import type { Cluster, TraceState } from '../state'
import {
  getCrossCompareState, closeCrossCompare, recordCrossComparison,
  applyCrossCompareResults, resetCrossCompare, ensureCache, updateSlider,
  undoCrossComparison, discardCrossCompareTrace, skipCrossComparison,
} from '../state'
import { getProgress, getResults } from '../models/crossCompare'
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'
import { buildTraceLink } from '../utils/export'

let _keyHandler: ((e: KeyboardEvent) => void) | null = null
let _ccSliderPct = 100
let _lastPairKey: string | null = null

// Review screen state — index into the list of all [positive, negative] pairings
let _reviewPairIdx = 0

function updateBothSliders(cl: Cluster, pct: number) {
  _ccSliderPct = pct
  const state = getCrossCompareState()
  if (!state?.currentPair) return
  const frac = pct / 100
  for (const key of state.currentPair) {
    const ts = findTrace(cl, key)
    if (!ts) continue
    ensureCache(ts)
    const target = Math.max(2, Math.round(2 + (ts.origN - 2) * frac))
    updateSlider(ts, target)
  }
}

// Build lookup maps once per render cycle instead of O(n) scans per panel
let _traceMap: Map<string, TraceState> | null = null
let _indexMap: Map<string, number> | null = null
let _mapClusterId: string | null = null

function ensureMaps(cl: Cluster) {
  if (_mapClusterId === cl.id && _traceMap) return
  _traceMap = new Map()
  _indexMap = new Map()
  cl.traces.forEach((ts, i) => { _traceMap!.set(ts._key, ts); _indexMap!.set(ts._key, i) })
  _mapClusterId = cl.id
}

function findTrace(cl: Cluster, key: string): TraceState | undefined {
  ensureMaps(cl)
  return _traceMap!.get(key)
}

function traceIndex(cl: Cluster, key: string): number {
  ensureMaps(cl)
  return _indexMap!.get(key) ?? -1
}

function renderPanel(cl: Cluster, key: string, side: 'left' | 'right') {
  const ts = findTrace(cl, key)
  if (!ts) return m('.cc-panel', 'Trace not found')
  ensureCache(ts)
  const state = getCrossCompareState()
  const selected = state?.selectedSide === side

  return m('.cc-panel' + (selected ? '.selected' : ''), [
    m('.cc-panel-header', [
      m('span.cc-panel-idx', `#${traceIndex(cl, key) + 1}`),
      m('span.cc-panel-pkg', ts.trace.package_name),
      ts.trace.startup_dur
        ? m('span.cc-panel-dur', fmt_dur(ts.trace.startup_dur))
        : null,
      (() => {
        const href = buildTraceLink(ts.trace.trace_uuid, ts.trace.package_name)
        return href ? m('a.trace-link', {
          href, target: '_blank', rel: 'noopener',
          onclick: (e: Event) => e.stopPropagation(),
          title: 'Open in trace viewer',
        }, '\u2197') : null
      })(),
    ]),
    m(MiniTimeline, { ts }),
    m('.cc-panel-detail', m(Summary, { ts })),
  ])
}

function renderReviewTraceRow(cl: Cluster, key: string) {
  const ts = findTrace(cl, key)
  if (!ts) return null
  ensureCache(ts)
  const href = buildTraceLink(ts.trace.trace_uuid, ts.trace.package_name)

  return m('.cc-review-row', [
    m('.cc-review-row-header', [
      m('span.cc-panel-idx', `#${traceIndex(cl, key) + 1}`),
      m('span.cc-panel-pkg', ts.trace.package_name),
      ts.trace.startup_dur
        ? m('span.cc-panel-dur', fmt_dur(ts.trace.startup_dur))
        : null,
      href ? m('a.trace-link', {
        href, target: '_blank', rel: 'noopener',
        title: 'Open in trace viewer',
      }, '\u2197') : null,
    ]),
    m(MiniTimeline, { ts }),
  ])
}

/** Build all [positiveIdx, negativeIdx] pairings for N groups. */
function buildPairings(n: number): [number, number][] {
  if (n < 2) return [[0, -1]]
  const pairs: [number, number][] = []
  for (let p = 0; p < n; p++) {
    for (let neg = 0; neg < n; neg++) {
      if (neg !== p) pairs.push([p, neg])
    }
  }
  return pairs
}

function cycleReview(delta: number, groupCount: number) {
  const pairings = buildPairings(groupCount)
  if (pairings.length <= 1) return
  _reviewPairIdx = ((_reviewPairIdx + delta) % pairings.length + pairings.length) % pairings.length
  m.redraw()
}

function renderReview(cl: Cluster) {
  const state = getCrossCompareState()
  if (!state) return null
  const { groups, discarded } = getResults(state)

  const pairings = buildPairings(groups.length)
  if (_reviewPairIdx >= pairings.length) _reviewPairIdx = 0
  const [posIdx, negIdx] = pairings[_reviewPairIdx]

  const positiveGroup = groups[posIdx] || []
  const negativeGroup = negIdx >= 0 ? (groups[negIdx] || []) : []

  return m('.cc-review', [
    m('.cc-review-split', [
      m('.cc-review-panel', [
        m('.cc-review-panel-header.negative', [
          m('span', 'Negative'),
          m('span.cc-review-count', `${negativeGroup.length}`),
        ]),
        m('.cc-review-panel-body', negativeGroup.map(key =>
          renderReviewTraceRow(cl, key)
        )),
      ]),
      m('.cc-review-panel', [
        m('.cc-review-panel-header.positive', [
          m('span', 'Positive'),
          m('span.cc-review-count', `${positiveGroup.length}`),
        ]),
        m('.cc-review-panel-body', positiveGroup.map(key =>
          renderReviewTraceRow(cl, key)
        )),
      ]),
    ]),
    m('.cc-review-nav', [
      pairings.length > 1
        ? m('span.cc-hint', `Pairing ${_reviewPairIdx + 1} / ${pairings.length} \u00b7 Group ${posIdx + 1} vs ${negIdx >= 0 ? negIdx + 1 : '\u2014'} of ${groups.length}`)
        : m('span.cc-hint', `${groups.length} group${groups.length !== 1 ? 's' : ''}`),
      discarded.length > 0
        ? m('span.cc-hint', `${discarded.length} discarded`)
        : null,
    ]),
    pairings.length > 1 ? m('.cc-hint', '\u2190 \u2192 to cycle pairings') : null,
    m('.cc-footer', [
      m('button.cc-action-btn.positive', {
        onclick: () => applyCrossCompareResults(cl, posIdx, negIdx),
      }, 'Apply'),
      m('button.cc-action-btn', {
        onclick: () => undoCrossComparison(),
        disabled: state.history.length === 0,
      }, 'Undo'),
      m('button.cc-action-btn', {
        onclick: () => resetCrossCompare(cl),
      }, 'Reset'),
      m('button.cc-action-btn', {
        onclick: closeCrossCompare,
      }, 'Close'),
    ]),
  ])
}

export const CrossCompareModal: m.Component<{ cl: Cluster }> = {
  oncreate() {
    _keyHandler = (e: KeyboardEvent) => {
      // Don't intercept keys when typing in inputs
      const tag = (e.target as HTMLElement)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      const state = getCrossCompareState()
      if (!state) return
      if (e.key === 'Escape') { closeCrossCompare(); return }
      if (e.key === 'z' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); undoCrossComparison(); return }
      if (state.isComplete) {
        // Review screen: arrow keys cycle pairings
        const { groups } = getResults(state)
        if (e.key === 'ArrowLeft') { cycleReview(-1, groups.length); return }
        if (e.key === 'ArrowRight') { cycleReview(1, groups.length); return }
        return
      }
      if (e.key === 'p' || e.key === 'P') { recordCrossComparison('positive'); return }
      if (e.key === 'n' || e.key === 'N') { recordCrossComparison('negative'); return }
      if (e.key === 's' || e.key === 'S') { skipCrossComparison(); return }
      if ((e.key === 'd' || e.key === 'D') && state.selectedSide) { discardCrossCompareTrace(vnode.attrs.cl, state.selectedSide); return }
      if (e.key === 'ArrowLeft') { state.selectedSide = 'left'; m.redraw(); return }
      if (e.key === 'ArrowRight') { state.selectedSide = 'right'; m.redraw(); return }
    }
    document.addEventListener('keydown', _keyHandler)
  },
  onremove() {
    if (_keyHandler) {
      document.removeEventListener('keydown', _keyHandler)
      _keyHandler = null
    }
  },
  view(vnode) {
    const { cl } = vnode.attrs
    const state = getCrossCompareState()
    if (!state) return null

    const progress = getProgress(state)

    // Apply slider to new pairs when they load
    const pairKey = state.currentPair ? state.currentPair[0] + '|' + state.currentPair[1] : null
    if (pairKey && pairKey !== _lastPairKey) {
      _lastPairKey = pairKey
      if (_ccSliderPct < 100) updateBothSliders(cl, _ccSliderPct)
    }

    return m('.cc-overlay', { onclick: () => {
      const s = getCrossCompareState()
      if (s && s.selectedSide) { s.selectedSide = null; m.redraw() }
      else closeCrossCompare()
    } }, [
      m('.cc-modal', { onclick: (e: Event) => {
        e.stopPropagation()
        const s = getCrossCompareState()
        if (s && s.selectedSide) { s.selectedSide = null; m.redraw() }
      } }, [
        // Header
        m('.cc-header', [
          m('span.cc-title', 'Compare'),
          m('button.cc-close', { onclick: closeCrossCompare, title: 'Close (Esc)' }, '\u00d7'),
        ]),

        // Progress bar
        m('.cc-progress', [
          m('.cc-progress-text', `${progress.completed} / ${progress.total} pairs resolved (${progress.pct}%)`),
          m('.cc-progress-bar', [
            m('.cc-progress-fill', { style: { width: progress.pct + '%' } }),
          ]),
        ]),

        // Body
        state.isComplete
          ? renderReview(cl)
          : state.currentPair
            ? m('.cc-body', [
                m('.cc-pair', [
                  renderPanel(cl, state.currentPair[0], 'left'),
                  m('.cc-pair-divider', 'vs'),
                  renderPanel(cl, state.currentPair[1], 'right'),
                ]),
                m('.cc-slider', [
                  m('span.slider-label', 'Detail'),
                  m('span.slider-num', _ccSliderPct + '%'),
                  m('input[type=range]', {
                    min: 1, max: 100, value: _ccSliderPct, step: 1,
                    oninput: (e: Event) => updateBothSliders(cl, +(e.target as HTMLInputElement).value),
                    onchange: (e: Event) => (e.target as HTMLElement).blur(),
                  }),
                ]),
                m('.cc-actions', [
                  m('button.cc-action-btn.positive', {
                    onclick: () => recordCrossComparison('positive'),
                  }, ['Positive ', m('kbd', 'P')]),
                  m('button.cc-action-btn.negative', {
                    onclick: () => recordCrossComparison('negative'),
                  }, ['Negative ', m('kbd', 'N')]),
                  m('button.cc-action-btn', {
                    onclick: () => skipCrossComparison(),
                  }, ['Skip ', m('kbd', 'S')]),
                  m('button.cc-action-btn.discard', {
                    onclick: () => { if (state.selectedSide) discardCrossCompareTrace(cl, state.selectedSide) },
                    disabled: !state.selectedSide,
                    title: state.selectedSide ? 'Discard selected trace' : 'Select a side first (\u2190\u2192)',
                  }, ['Discard ', m('kbd', 'D')]),
                  m('button.cc-action-btn', {
                    onclick: () => undoCrossComparison(),
                    disabled: state.history.length === 0,
                    title: 'Undo (Ctrl+Z)',
                  }, ['Undo ', m('kbd', '\u2318Z')]),
                ]),
                m('.cc-hint', '\u2190 \u2192 select side \u00b7 Ctrl+Z undo \u00b7 Esc close'),
                m('.cc-footer', [
                  m('button.cc-action-btn', {
                    onclick: () => applyCrossCompareResults(cl),
                  }, 'Apply Current Results'),
                  m('button.cc-action-btn', {
                    onclick: () => resetCrossCompare(cl),
                  }, 'Reset'),
                ]),
              ])
            : null,
      ]),
    ])
  },
}
