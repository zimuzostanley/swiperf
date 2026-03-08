import m from 'mithril'
import { activeCluster, filteredTraces, ensureCache, setVerdict, updateSlider, recomputeCounts, getPositiveTraces, getNegativeTraces } from '../state'
import type { TraceState, Cluster } from '../state'
import type { OverviewFilter } from '../models/types'
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'

const expanded = new Set<string>()

function toggleExpand(uuid: string) {
  if (expanded.has(uuid)) expanded.delete(uuid)
  else expanded.add(uuid)
}

const FILTERS: { id: OverviewFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'positive', label: 'Positive' },
  { id: 'negative', label: 'Negative' },
]

function downloadFile(content: string, filename: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function exportVerdicts() {
  const cl = activeCluster()
  if (!cl) return
  const pos = getPositiveTraces().map(ts => ({
    trace_uuid: ts.trace.trace_uuid,
    package_name: ts.trace.package_name,
    startup_dur: ts.trace.startup_dur,
  }))
  const neg = getNegativeTraces().map(ts => ({
    trace_uuid: ts.trace.trace_uuid,
    package_name: ts.trace.package_name,
    startup_dur: ts.trace.startup_dur,
  }))
  const data = { positive: pos, negative: neg }
  const date = new Date().toISOString().slice(0, 10)
  downloadFile(JSON.stringify(data, null, 2), `swiperf-verdicts-${date}.json`, 'application/json')
}

function renderSlider(ts: TraceState) {
  return m('.trace-slider', [
    m('span.slider-label', 'Slices'),
    m('span.slider-num', String(ts.currentSeq.length)),
    m('input[type=range]', {
      min: 2,
      max: ts.origN,
      value: ts.sliderValue,
      step: 1,
      onclick: (e: Event) => e.stopPropagation(),
      oninput: (e: Event) => {
        e.stopPropagation()
        updateSlider(ts, +(e.target as HTMLInputElement).value)
      },
    }),
    m('span.slider-of', `/ ${ts.origN}`),
  ])
}

function renderTraceCard(cl: Cluster, ts: TraceState, idx: number) {
  const uuid = ts.trace.trace_uuid
  const isExpanded = expanded.has(uuid)
  const verdict = cl.verdicts.get(uuid)
  ensureCache(ts)

  return m('.card.trace-card', {
    class: verdict === 'like' ? 'verdict-positive' : verdict === 'dislike' ? 'verdict-negative' : '',
  }, [
    // Header row — always visible
    m('.trace-card-header', {
      onclick: () => toggleExpand(uuid),
    }, [
      m('span.collapse-arrow' + (isExpanded ? '.open' : ''), '\u25b6'),
      m('span.trace-idx', `#${idx + 1}`),
      m('span.trace-pkg', ts.trace.package_name),
      m('span.trace-dur', fmt_dur(ts.totalDur)),
      ts.trace.startup_dur
        ? m('span.trace-startup', `startup ${fmt_dur(ts.trace.startup_dur)}`)
        : null,
      verdict
        ? m('span.verdict-badge', {
            class: verdict === 'like' ? 'badge-positive' : 'badge-negative',
          }, verdict === 'like' ? '+' : '\u2212')
        : null,
      m('span.trace-actions', [
        m('button.verdict-btn-sm' + (verdict === 'like' ? '.active-positive' : ''), {
          onclick: (e: Event) => { e.stopPropagation(); setVerdict(cl, uuid, 'like') },
          title: 'Positive',
        }, '+'),
        m('button.verdict-btn-sm' + (verdict === 'dislike' ? '.active-negative' : ''), {
          onclick: (e: Event) => { e.stopPropagation(); setVerdict(cl, uuid, 'dislike') },
          title: 'Negative',
        }, '\u2212'),
      ]),
    ]),

    // Mini timeline + slider — always visible
    m('.trace-card-body', [
      m(MiniTimeline, { ts }),
      renderSlider(ts),
    ]),

    // Expanded detail — full timeline + breakdown
    isExpanded ? m('.trace-card-detail', [
      m('.detail-section', [
        m('.detail-label', 'Breakdown'),
        m(Summary, { ts }),
      ]),
      m('.detail-meta', [
        m('.tt-grid', [
          m('span.tt-k', 'UUID'),
          m('span.tt-v', ts.trace.trace_uuid),
          m('span.tt-k', 'Package'),
          m('span.tt-v', ts.trace.package_name),
          m('span.tt-k', 'Startup'),
          m('span.tt-v', ts.trace.startup_dur ? fmt_dur(ts.trace.startup_dur) : '\u2014'),
          m('span.tt-k', 'Slices'),
          m('span.tt-v', String(ts.origN)),
          m('span.tt-k', 'Total dur'),
          m('span.tt-v', fmt_dur(ts.totalDur)),
          ...(ts.trace.extra
            ? Object.entries(ts.trace.extra).flatMap(([k, v]) => [
                m('span.tt-k', k),
                m('span.tt-v', String(v)),
              ])
            : []
          ),
        ]),
      ]),
    ]) : null,
  ])
}

export const TraceList: m.Component = {
  view() {
    const cl = activeCluster()
    if (!cl || cl.traces.length === 0) {
      return m('.section', [
        m('.section-head', 'Traces'),
        m('p', { style: { color: 'var(--dim)', fontSize: '11px' } }, 'No traces loaded.'),
      ])
    }

    const filtered = filteredTraces()
    const { positive, negative, pending } = cl.counts

    return m('.section', [
      m('.section-head', `Traces (${filtered.length}${filtered.length !== cl.traces.length ? '/' + cl.traces.length : ''})`),

      // Toolbar: filter tabs + stats + export
      m('.card.list-toolbar', [
        m('.list-filters', FILTERS.map(f =>
          m('button.filter-btn' + (cl.overviewFilter === f.id ? '.active' : ''), {
            onclick: () => { cl.overviewFilter = f.id },
          }, [
            f.label,
            m('span.filter-count',
              f.id === 'all' ? String(cl.traces.length)
                : f.id === 'positive' ? String(positive)
                : String(negative)
            ),
          ])
        )),
        m('.list-stats', [
          m('span.stat-pill.stat-positive', `${positive} positive`),
          m('span.stat-pill.stat-negative', `${negative} negative`),
          m('span.stat-pill.stat-pending', `${pending} pending`),
        ]),
        m('.list-actions', [
          m('button.btn.primary', {
            onclick: exportVerdicts,
            disabled: positive === 0 && negative === 0,
          }, 'Export verdicts'),
        ]),
      ]),

      // Trace cards
      m('.trace-list', filtered.map((ts, i) => {
        const globalIdx = cl.traces.indexOf(ts)
        return renderTraceCard(cl, ts, globalIdx)
      })),
    ])
  },
}
