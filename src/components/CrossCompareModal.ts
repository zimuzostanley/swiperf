import m from 'mithril'
import type { Cluster, TraceState } from '../state'
import {
  getCrossCompareState, closeCrossCompare, recordCrossComparison,
  applyCrossCompareResults, resetCrossCompare, ensureCache, updateSlider,
  undoCrossComparison,
} from '../state'
import { getProgress, getResults } from '../models/crossCompare'
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'
import { buildTraceLink } from '../utils/export'

let _keyHandler: ((e: KeyboardEvent) => void) | null = null
let _ccSliderPct = 100
let _lastPairKey: string | null = null

// Review screen state
let _reviewPositiveIdx = 0
let _reviewNegativeIdx = 1

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

function swapReviewAssignment(groupCount: number) {
  if (groupCount < 2) return
  const tmp = _reviewPositiveIdx
  _reviewPositiveIdx = _reviewNegativeIdx
  _reviewNegativeIdx = tmp
  m.redraw()
}

function renderReview(cl: Cluster) {
  const state = getCrossCompareState()
  if (!state) return null
  const { groups } = getResults(state)

  // Reset indices if out of range
  if (_reviewPositiveIdx >= groups.length) _reviewPositiveIdx = 0
  if (_reviewNegativeIdx >= groups.length || _reviewNegativeIdx === _reviewPositiveIdx) {
    _reviewNegativeIdx = groups.length > 1 ? (_reviewPositiveIdx === 0 ? 1 : 0) : -1
  }

  const positiveGroup = groups[_reviewPositiveIdx] || []
  const negativeGroup = _reviewNegativeIdx >= 0 ? (groups[_reviewNegativeIdx] || []) : []

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
    groups.length > 2
      ? m('.cc-hint', `${groups.length - 2} other group${groups.length - 2 !== 1 ? 's' : ''} will be left unchanged`)
      : null,
    m('.cc-hint', '\u2190 \u2192 to swap assignment'),
    m('.cc-footer', [
      m('button.cc-action-btn.positive', {
        onclick: () => applyCrossCompareResults(cl, _reviewPositiveIdx, _reviewNegativeIdx),
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
        // Review screen: arrow keys swap assignment
        const { groups } = getResults(state)
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          swapReviewAssignment(groups.length)
          return
        }
        return
      }
      if (e.key === 'p' || e.key === 'P') { recordCrossComparison('positive'); return }
      if (e.key === 'n' || e.key === 'N') { recordCrossComparison('negative'); return }
      if ((e.key === 's' || e.key === 'S') && state.selectedSide) { recordCrossComparison('skip'); return }
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
                    onclick: () => { if (state.selectedSide) recordCrossComparison('skip') },
                    disabled: !state.selectedSide,
                    title: state.selectedSide ? 'Skip this pair' : 'Select a side first (arrow keys)',
                  }, ['Skip ', m('kbd', 'S')]),
                  m('button.cc-action-btn', {
                    onclick: () => undoCrossComparison(),
                    disabled: state.history.length === 0,
                    title: 'Undo last comparison (Ctrl+Z)',
                  }, ['Undo ', m('kbd', '\u2318Z')]),
                ]),
                m('.cc-hint', '\u2190 \u2192 arrow keys to highlight a side \u00b7 Ctrl+Z to undo \u00b7 Esc to close'),
                m('.cc-footer', [
                  m('button.cc-action-btn', {
                    onclick: () => applyCrossCompareResults(cl, _reviewPositiveIdx, _reviewNegativeIdx),
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
