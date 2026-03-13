import m from 'mithril'
import type { Cluster, TraceState } from '../state'
import {
  getCrossCompareState, closeCrossCompare, recordCrossComparison,
  applyCrossCompareResults, resetCrossCompare, ensureCache, updateSlider,
  undoCrossComparison, discardCrossCompareTrace, skipCrossComparison,
} from '../state'
import { getProgress, getResults, CrossCompareState } from '../models/crossCompare'
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'
import { buildTraceLink } from '../utils/export'

let _keyHandler: ((e: KeyboardEvent) => void) | null = null
let _ccSliderPct = 100
let _lastPairKey: string | null = null

// Anchor mode: when set, this trace stays on screen and others rotate against it
let _anchorKey: string | null = null
let _anchorSide: 'left' | 'right' | null = null

// Review screen state
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

// ── Anchor helpers ──

function toggleAnchor(key: string, side: 'left' | 'right') {
  if (_anchorKey === key && _anchorSide === side) {
    // Deselect
    _anchorKey = null
    _anchorSide = null
  } else {
    _anchorKey = key
    _anchorSide = side
  }
  const state = getCrossCompareState()
  if (state) state.selectedSide = null
  m.redraw()
}

function clearAnchor() {
  _anchorKey = null
  _anchorSide = null
}

/** After advancing, ensure anchor stays on the correct side of currentPair. */
function ensureAnchorSide() {
  const state = getCrossCompareState()
  if (!_anchorKey || !state?.currentPair) return
  // Clear anchor if anchor trace was discarded
  if (state.discardedKeys.has(_anchorKey)) { clearAnchor(); return }
  const [a, b] = state.currentPair
  // Only swap if anchor is actually in this pair
  if (_anchorSide === 'left' && b === _anchorKey && a !== _anchorKey) {
    state.currentPair = [b, a]
  } else if (_anchorSide === 'right' && a === _anchorKey && b !== _anchorKey) {
    state.currentPair = [b, a]
  }
  // If anchor isn't in pair (tournament fallback), leave anchor set — it's a user preference
}

/** Whether the anchor trace is in the current pair. */
function anchorActive(state: CrossCompareState | null): boolean {
  return !!_anchorKey && !!state?.currentPair && state.currentPair.includes(_anchorKey)
}

function discardSideForAnchor(): 'left' | 'right' | null {
  if (_anchorSide === 'left') return 'right'
  if (_anchorSide === 'right') return 'left'
  return null
}

// ── Panel rendering ──

function renderPanel(cl: Cluster, key: string, side: 'left' | 'right') {
  const ts = findTrace(cl, key)
  if (!ts) return m('.cc-panel', 'Trace not found')
  ensureCache(ts)
  const isAnchor = _anchorKey === key
  const state = getCrossCompareState()
  const aa = anchorActive(state)
  const isSelected = !aa && state?.selectedSide === side

  return m('.cc-panel' + (isAnchor ? '.anchored' : isSelected ? '.selected' : ''), {
    onclick: (e: Event) => {
      e.stopPropagation()
      toggleAnchor(key, side)
    },
  }, [
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
      isAnchor ? m('span.cc-anchor-badge', 'anchor') : null,
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

// ── Review screen ──

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

  // Pure anchor: anchor's group = positive, all others combined = negative
  let positiveGroup: string[]
  let negativeGroup: string[]
  let posIdx: number
  let negIdx: number
  let pairings: [number, number][]
  const pureAnchor = _anchorKey && groups.some(g => g.includes(_anchorKey!))

  if (pureAnchor) {
    const anchorGroupIdx = groups.findIndex(g => g.includes(_anchorKey!))
    positiveGroup = groups[anchorGroupIdx]
    negativeGroup = []
    posIdx = anchorGroupIdx
    negIdx = -1  // no negative group — others stay pending
    pairings = [[posIdx, negIdx]]
  } else {
    pairings = buildPairings(groups.length)
    if (_reviewPairIdx >= pairings.length) _reviewPairIdx = 0
    ;[posIdx, negIdx] = pairings[_reviewPairIdx]
    positiveGroup = groups[posIdx] || []
    negativeGroup = negIdx >= 0 ? (groups[negIdx] || []) : []
  }

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
        ? m('span.cc-hint', `Pairing ${_reviewPairIdx + 1} / ${pairings.length}`)
        : null,
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
        onclick: () => { undoCrossComparison(_anchorKey ?? undefined); ensureAnchorSide() },
        disabled: state.history.length === 0,
      }, 'Undo'),
      m('button.cc-action-btn', {
        onclick: () => { resetCrossCompare(cl); clearAnchor() },
      }, 'Reset'),
      m('button.cc-action-btn', {
        onclick: closeCrossCompare,
      }, 'Close'),
    ]),
  ])
}

// ── Main component ──

export const CrossCompareModal: m.Component<{ cl: Cluster }> = {
  oncreate(vnode: m.VnodeDOM<{ cl: Cluster }>) {
    const getCl = () => vnode.attrs.cl
    _keyHandler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      const state = getCrossCompareState()
      if (!state) return
      if (e.key === 'Escape') { closeCrossCompare(); return }
      if (e.key === 'z' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault()
        undoCrossComparison(_anchorKey ?? undefined)
        ensureAnchorSide()
        return
      }
      if (state.isComplete) {
        const { groups } = getResults(state)
        if (e.key === 'ArrowLeft') { cycleReview(-1, groups.length); return }
        if (e.key === 'ArrowRight') { cycleReview(1, groups.length); return }
        return
      }
      // Comparison keys — pass anchorKey so advancePair tries anchor-first
      const ak = _anchorKey ?? undefined
      const aa = anchorActive(state)
      if (e.key === 'p' || e.key === 'P') {
        recordCrossComparison('positive', ak)
        ensureAnchorSide()
        return
      }
      if (e.key === 'n' || e.key === 'N') {
        recordCrossComparison('negative', ak)
        ensureAnchorSide()
        return
      }
      if (e.key === 's' || e.key === 'S') {
        skipCrossComparison(ak)
        ensureAnchorSide()
        return
      }
      if (e.key === 'd' || e.key === 'D') {
        const side = aa ? discardSideForAnchor() : state.selectedSide
        if (side) {
          discardCrossCompareTrace(getCl(), side, ak)
          ensureAnchorSide()
        }
        return
      }
      // Arrow keys: select side when anchor not active in current pair
      if (!aa) {
        if (e.key === 'ArrowLeft') { state.selectedSide = 'left'; m.redraw(); return }
        if (e.key === 'ArrowRight') { state.selectedSide = 'right'; m.redraw(); return }
      }
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

    // Clear anchor if discarded
    if (_anchorKey && state.discardedKeys.has(_anchorKey)) clearAnchor()

    const active = anchorActive(state)
    const canDiscard = active || !!state.selectedSide

    return m('.cc-overlay', { onclick: () => {
      if (_anchorKey) { clearAnchor(); m.redraw() }
      else if (state.selectedSide) { state.selectedSide = null; m.redraw() }
      else closeCrossCompare()
    } }, [
      m('.cc-modal', { onclick: (e: Event) => {
        e.stopPropagation()
        // Don't clear anchor/selection when clicking modal background —
        // only panels and overlay handle that
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
                    onclick: () => { recordCrossComparison('positive', _anchorKey ?? undefined); ensureAnchorSide() },
                  }, ['Positive ', m('kbd', 'P')]),
                  m('button.cc-action-btn.negative', {
                    onclick: () => { recordCrossComparison('negative', _anchorKey ?? undefined); ensureAnchorSide() },
                  }, ['Negative ', m('kbd', 'N')]),
                  m('button.cc-action-btn', {
                    onclick: () => { skipCrossComparison(_anchorKey ?? undefined); ensureAnchorSide() },
                  }, ['Skip ', m('kbd', 'S')]),
                  m('button.cc-action-btn.discard', {
                    onclick: () => {
                      const side = active ? discardSideForAnchor() : state.selectedSide
                      if (side) { discardCrossCompareTrace(cl, side, _anchorKey ?? undefined); ensureAnchorSide() }
                    },
                    disabled: !canDiscard,
                    title: active
                      ? 'Discard the non-anchor trace'
                      : state.selectedSide
                        ? 'Discard selected trace'
                        : 'Click a panel to anchor, or \u2190\u2192 to select',
                  }, ['Discard ', m('kbd', 'D')]),
                  m('button.cc-action-btn', {
                    onclick: () => { undoCrossComparison(_anchorKey ?? undefined); ensureAnchorSide() },
                    disabled: state.history.length === 0,
                    title: 'Undo (Ctrl+Z)',
                  }, ['Undo ', m('kbd', '\u2318Z')]),
                ]),
                m('.cc-hint', active
                  ? `Anchored \u00b7 P/N/S/D apply to other trace \u00b7 click anchor to deselect`
                  : _anchorKey
                    ? `Anchor set (not in this pair) \u00b7 \u2190\u2192 to select \u00b7 click panel to re-anchor`
                    : 'Click a panel to anchor \u00b7 \u2190\u2192 to select for discard \u00b7 Esc close'
                ),
                m('.cc-footer', [
                  m('button.cc-action-btn', {
                    onclick: () => applyCrossCompareResults(cl),
                  }, 'Apply Current Results'),
                  m('button.cc-action-btn', {
                    onclick: () => { resetCrossCompare(cl); clearAnchor() },
                  }, 'Reset'),
                ]),
              ])
            : null,
      ]),
    ])
  },
}
