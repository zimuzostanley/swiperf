import m from 'mithril'
import { activeCluster, currentTrace, navigate, setVerdict } from '../state'
import { fmt_dur } from '../utils/format'

export const NavBar: m.Component = {
  view() {
    const cl = activeCluster()
    if (!cl || cl.traces.length <= 1) return null
    const ts = currentTrace()
    if (!ts) return null

    const verdict = cl.verdicts.get(ts.trace.trace_uuid) ?? null
    const { liked, disliked, pending } = cl.counts
    const total = cl.traces.length
    const triaged = liked + disliked
    const pct = total > 0 ? (triaged / total * 100) : 0

    return m('.card', { style: { marginBottom: '14px' } }, [
      m('.progress-bar-wrap', [
        m('.progress-bar-fill', { style: { width: pct.toFixed(1) + '%' } }),
        m('.progress-bar-text', `${triaged}/${total} triaged (${pct.toFixed(0)}%)`),
      ]),

      m('.nav-bar', [
        m('.nav-group', [
          m('button.btn', {
            onclick: () => navigate(-1),
            disabled: cl.currentIndex === 0,
            title: 'Previous (A)',
          }, [m('kbd', 'A'), ' Prev']),
          m('span.nav-counter', `${cl.currentIndex + 1} / ${total}`),
          m('button.btn', {
            onclick: () => navigate(1),
            disabled: cl.currentIndex === total - 1,
            title: 'Next (D)',
          }, ['Next ', m('kbd', 'D')]),
        ]),

        m('.vdivider'),

        m('.nav-info', [
          m('span.nav-pkg', ts.trace.package_name),
          m('span.nav-dur', fmt_dur(ts.totalDur)),
          ts.trace.startup_dur
            ? m('span.nav-startup', `startup ${fmt_dur(ts.trace.startup_dur)}`)
            : null,
        ]),

        m('.vdivider'),

        m('.nav-verdict', [
          m('button.verdict-btn' + (verdict === 'like' ? '.active-like' : ''), {
            onclick: () => setVerdict('like'),
            title: 'Like (W)',
          }, [m('kbd', 'W'), ' Like']),
          m('button.verdict-btn' + (verdict === 'dislike' ? '.active-dislike' : ''), {
            onclick: () => setVerdict('dislike'),
            title: 'Dislike (S)',
          }, [m('kbd', 'S'), ' Nope']),
        ]),

        m('.vdivider'),

        m('.nav-stats', [
          m('span.stat-pill.stat-liked', `${liked}`),
          m('span.stat-pill.stat-disliked', `${disliked}`),
          m('span.stat-pill.stat-pending', `${pending}`),
        ]),

        m('label.auto-advance', [
          m('input[type=checkbox]', {
            checked: cl.autoAdvance,
            onchange: (e: Event) => { cl.autoAdvance = (e.target as HTMLInputElement).checked },
          }),
          'Auto-advance',
        ]),
      ]),
    ])
  },
}
