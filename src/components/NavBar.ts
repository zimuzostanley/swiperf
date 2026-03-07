import m from 'mithril'
import { S, currentTrace, navigate, setVerdict } from '../state'
import { fmt_dur } from '../utils/format'

export const NavBar: m.Component = {
  view() {
    if (S.traces.length <= 1) return null
    const ts = currentTrace()
    if (!ts) return null

    const verdict = S.verdicts.get(ts.trace.trace_uuid) ?? null
    const { liked, disliked, pending } = S.counts
    const total = S.traces.length
    const triaged = liked + disliked
    const pct = total > 0 ? (triaged / total * 100) : 0

    return m('.card', { style: { marginBottom: '14px' } }, [
      // Progress bar
      m('.progress-bar-wrap', [
        m('.progress-bar-fill', { style: { width: pct.toFixed(1) + '%' } }),
        m('.progress-bar-text', `${triaged}/${total} triaged (${pct.toFixed(0)}%)`),
      ]),

      m('.nav-bar', [
        // Navigation
        m('.nav-group', [
          m('button.btn', {
            onclick: () => navigate(-1),
            disabled: S.currentIndex === 0,
            title: 'Previous (A)',
          }, [m('kbd', 'A'), ' Prev']),
          m('span.nav-counter', `${S.currentIndex + 1} / ${total}`),
          m('button.btn', {
            onclick: () => navigate(1),
            disabled: S.currentIndex === total - 1,
            title: 'Next (D)',
          }, ['Next ', m('kbd', 'D')]),
        ]),

        m('.vdivider'),

        // Trace info
        m('.nav-info', [
          m('span.nav-pkg', ts.trace.package_name),
          m('span.nav-dur', fmt_dur(ts.totalDur)),
          ts.trace.startup_dur
            ? m('span.nav-startup', `startup ${fmt_dur(ts.trace.startup_dur)}`)
            : null,
        ]),

        m('.vdivider'),

        // Verdict buttons
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

        // Stats
        m('.nav-stats', [
          m('span.stat-pill.stat-liked', `${liked}`),
          m('span.stat-pill.stat-disliked', `${disliked}`),
          m('span.stat-pill.stat-pending', `${pending}`),
        ]),

        // Auto-advance toggle
        m('label.auto-advance', [
          m('input[type=checkbox]', {
            checked: S.autoAdvance,
            onchange: (e: Event) => { S.autoAdvance = (e.target as HTMLInputElement).checked },
          }),
          'Auto-advance',
        ]),
      ]),
    ])
  },
}
