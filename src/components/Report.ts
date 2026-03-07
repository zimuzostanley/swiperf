import m from 'mithril'
import { activeCluster, getLikedTraces, ensureCache, jumpTo } from '../state'
import type { TraceState } from '../state'
import { MiniTimeline } from './MiniTimeline'
import { fmt_dur } from '../utils/format'
import { state_color, state_label } from '../utils/colors'

function downloadFile(content: string, filename: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function buildReportData(traces: TraceState[]) {
  const cl = activeCluster()
  return traces.map(ts => {
    ensureCache(ts)
    const stateBreakdown: Record<string, number> = {}
    ts.currentSeq.forEach(d => {
      const sk = state_label(d)
      stateBreakdown[sk] = (stateBreakdown[sk] || 0) + d.dur
    })

    return {
      trace_uuid: ts.trace.trace_uuid,
      package_name: ts.trace.package_name,
      startup_dur: ts.trace.startup_dur,
      total_dur_ns: ts.totalDur,
      total_dur_ms: +(ts.totalDur / 1e6).toFixed(2),
      slice_count: ts.origN,
      state_breakdown: stateBreakdown,
      verdict: cl?.verdicts.get(ts.trace.trace_uuid) || 'pending',
      ...(ts.trace.extra || {}),
    }
  })
}

function exportJson() {
  const liked = getLikedTraces()
  const data = buildReportData(liked)
  const date = new Date().toISOString().slice(0, 10)
  downloadFile(JSON.stringify(data, null, 2), `swiperf-report-${date}.json`, 'application/json')
}

function exportCsv() {
  const liked = getLikedTraces()
  const data = buildReportData(liked)
  if (data.length === 0) return

  const baseKeys = ['trace_uuid', 'package_name', 'startup_dur', 'total_dur_ms', 'slice_count', 'verdict']
  const stateKeys = new Set<string>()
  const extraKeys = new Set<string>()
  data.forEach(d => {
    Object.keys(d.state_breakdown).forEach(k => stateKeys.add(k))
    if (d) {
      Object.keys(d).forEach(k => {
        if (!baseKeys.includes(k) && k !== 'state_breakdown' && k !== 'total_dur_ns') {
          extraKeys.add(k)
        }
      })
    }
  })
  const allStateKeys = Array.from(stateKeys).sort()
  const allExtraKeys = Array.from(extraKeys).sort()
  const headers = [...baseKeys, ...allStateKeys.map(k => `state_${k}_ns`), ...allExtraKeys]

  const rows = data.map(d => {
    const row = [
      d.trace_uuid,
      d.package_name,
      String(d.startup_dur),
      String(d.total_dur_ms),
      String(d.slice_count),
      d.verdict,
      ...allStateKeys.map(k => String(d.state_breakdown[k] || 0)),
      ...allExtraKeys.map(k => String((d as any)[k] || '')),
    ]
    return row.map(v => v.includes(',') || v.includes('"') ? `"${v.replace(/"/g, '""')}"` : v).join(',')
  })

  const csv = [headers.join(','), ...rows].join('\n')
  const date = new Date().toISOString().slice(0, 10)
  downloadFile(csv, `swiperf-report-${date}.csv`, 'text/csv')
}

function exportAllVerdicts() {
  const cl = activeCluster()
  if (!cl) return
  const data = buildReportData(cl.traces)
  const date = new Date().toISOString().slice(0, 10)
  downloadFile(JSON.stringify(data, null, 2), `swiperf-all-verdicts-${date}.json`, 'application/json')
}

function buildAggStats(traces: TraceState[]) {
  const stateAgg: Record<string, { dur: number; count: number; color: string }> = {}
  let totalDur = 0
  traces.forEach(ts => {
    ensureCache(ts)
    ts.currentSeq.forEach(d => {
      const sk = state_label(d)
      if (!stateAgg[sk]) stateAgg[sk] = { dur: 0, count: 0, color: state_color(d) }
      stateAgg[sk].dur += d.dur
      stateAgg[sk].count += 1
    })
    totalDur += ts.totalDur
  })
  return { stateAgg, totalDur }
}

export const Report: m.Component = {
  view() {
    const cl = activeCluster()
    if (!cl) return null
    const liked = getLikedTraces()
    const { liked: likedCount, disliked, pending } = cl.counts

    return m('.section', [
      m('.section-head', 'Report'),

      m('.report-header', [
        m('.report-stats', [
          m('.report-stat', [m('.val', String(likedCount)), m('.lbl', 'Liked')]),
          m('.report-stat', [m('.val', String(disliked)), m('.lbl', 'Disliked')]),
          m('.report-stat', [m('.val', String(pending)), m('.lbl', 'Pending')]),
        ]),
        m('.report-actions', [
          m('button.btn.primary', { onclick: exportJson, disabled: liked.length === 0 }, 'Export Liked (JSON)'),
          m('button.btn.primary', { onclick: exportCsv, disabled: liked.length === 0 }, 'Export Liked (CSV)'),
          m('button.btn', { onclick: exportAllVerdicts }, 'Export All Verdicts'),
        ]),
      ]),

      liked.length === 0
        ? m('.card', { style: { padding: '24px', textAlign: 'center' } },
            m('p', { style: { color: 'var(--dim)', fontSize: '11px' } },
              'No liked traces yet. Use W to like, S to dislike. Auto-advance moves to next pending.'
            )
          )
        : m('.report-content', [
            (() => {
              const { stateAgg, totalDur } = buildAggStats(liked)
              return m('.card', { style: { marginBottom: '14px', padding: '14px' } }, [
                m('.section-head', { style: { marginBottom: '6px' } }, `Aggregate (${liked.length} liked traces)`),
                m('div', { style: { display: 'flex', gap: '12px', flexWrap: 'wrap' } },
                  Object.entries(stateAgg).map(([label, v]) =>
                    m('.li', { style: { fontSize: '11px' } }, [
                      m('.ls', { style: { background: v.color } }),
                      `${label}: ${fmt_dur(v.dur)} (${(v.dur / totalDur * 100).toFixed(1)}%)`,
                    ])
                  )
                ),
              ])
            })(),

            m('.overview-grid', liked.map(ts => {
              const globalIdx = cl.traces.indexOf(ts)
              return m('.card.overview-card.verdict-liked', [
                m('.overview-card-head', [
                  m('.pkg', ts.trace.package_name),
                  m('.dur', fmt_dur(ts.totalDur)),
                  ts.trace.startup_dur ? m('.dur', `startup ${fmt_dur(ts.trace.startup_dur)}`) : null,
                  m('button.btn', {
                    onclick: () => jumpTo(globalIdx),
                    style: { padding: '2px 8px', fontSize: '10px', marginLeft: '8px' },
                  }, '\u2192'),
                ]),
                m(MiniTimeline, { ts }),
                m('div', { style: { padding: '4px 14px 8px', fontFamily: 'var(--mono)', fontSize: '10px', color: 'var(--dim)' } },
                  ts.trace.trace_uuid
                ),
              ])
            })),
          ]),
    ])
  },
}
