import m from 'mithril'
import type { Cluster, TraceState } from '../state'
import {
  getCrossCompareState, closeCrossCompare, recordCrossComparison,
  applyCrossCompareResults, resetCrossCompare, ensureCache,
} from '../state'
import { getProgress, getResults } from '../models/crossCompare'
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'

let _keyHandler: ((e: KeyboardEvent) => void) | null = null

function findTrace(cl: Cluster, key: string): TraceState | undefined {
  return cl.traces.find(ts => ts._key === key)
}

function traceIndex(cl: Cluster, key: string): number {
  return cl.traces.findIndex(ts => ts._key === key)
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
    ]),
    m(MiniTimeline, { ts }),
    m('.cc-panel-detail', m(Summary, { ts })),
  ])
}

function renderComplete(cl: Cluster) {
  const state = getCrossCompareState()
  if (!state) return null
  const { groups } = getResults(state)

  return m('.cc-results', [
    m('.cc-results-title', 'Comparison complete'),
    m('p.cc-hint', `Found ${groups.length} group${groups.length !== 1 ? 's' : ''}`),
    ...groups.slice(0, 5).map((group, i) =>
      m('.cc-group-row', [
        m('span.cc-group-label',
          i === 0 ? 'Largest (→ Positive)' :
          i === 1 ? 'Second (→ Negative)' :
          `Group ${i + 1}`
        ),
        m('span.cc-group-count', `${group.length} trace${group.length !== 1 ? 's' : ''}`),
      ])
    ),
    m('.cc-footer', [
      m('button.cc-action-btn.positive', {
        onclick: () => applyCrossCompareResults(cl),
      }, 'Apply Results'),
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
      const state = getCrossCompareState()
      if (!state) return
      if (e.key === 'Escape') { closeCrossCompare(); return }
      if (state.isComplete) return
      if (e.key === 'p' || e.key === 'P') { recordCrossComparison('positive'); return }
      if (e.key === 'n' || e.key === 'N') { recordCrossComparison('negative'); return }
      if (e.key === 'd' || e.key === 'D') { recordCrossComparison('skip'); return }
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

    return m('.cc-overlay', { onclick: closeCrossCompare }, [
      m('.cc-modal', { onclick: (e: Event) => e.stopPropagation() }, [
        // Header
        m('.cc-header', [
          m('span.cc-title', 'Cross Compare'),
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
          ? renderComplete(cl)
          : state.currentPair
            ? m('.cc-body', [
                m('.cc-pair', [
                  renderPanel(cl, state.currentPair[0], 'left'),
                  m('.cc-pair-divider', 'vs'),
                  renderPanel(cl, state.currentPair[1], 'right'),
                ]),
                m('.cc-actions', [
                  m('button.cc-action-btn.positive', {
                    onclick: () => recordCrossComparison('positive'),
                  }, ['Similar ', m('kbd', 'P')]),
                  m('button.cc-action-btn.negative', {
                    onclick: () => recordCrossComparison('negative'),
                  }, ['Different ', m('kbd', 'N')]),
                  m('button.cc-action-btn', {
                    onclick: () => recordCrossComparison('skip'),
                  }, ['Skip ', m('kbd', 'D')]),
                ]),
                m('.cc-hint', '\u2190 \u2192 arrow keys to highlight a side \u00b7 Esc to close'),
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
