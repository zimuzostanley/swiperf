import m from 'mithril'
import { S, currentTrace, navigate, setVerdict, ensureCache } from '../state'
import { Import } from './Import'
import { Controls } from './Controls'
import { Timeline } from './Timeline'
import { Summary } from './Summary'
import { NavBar } from './NavBar'
import { Overview } from './Overview'
import { Report } from './Report'
import { fmt_dur } from '../utils/format'
import { isDark } from '../utils/colors'

function applyTheme(theme: string) {
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('trace-theme', theme)
}

function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme')
  applyTheme(current === 'dark' ? 'light' : 'dark')
}

export const App: m.Component = {
  oncreate() {
    const saved = localStorage.getItem('trace-theme') || 'light'
    applyTheme(saved)

    document.addEventListener('keydown', (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return

      switch (e.key.toLowerCase()) {
        case 'a':
          e.preventDefault()
          navigate(-1)
          break
        case 'd':
          e.preventDefault()
          navigate(1)
          break
        case 'w':
          e.preventDefault()
          setVerdict('like')
          break
        case 's':
          e.preventDefault()
          setVerdict('dislike')
          break
        case '1':
          if (S.traces.length > 1) S.viewMode = 'single'
          m.redraw()
          break
        case '2':
          if (S.traces.length > 1) S.viewMode = 'overview'
          m.redraw()
          break
        case '3':
          if (S.traces.length > 1) S.viewMode = 'report'
          m.redraw()
          break
      }
    })
  },

  view() {
    const dark = isDark()
    const ts = currentTrace()
    if (ts) ensureCache(ts)

    return m('.shell', [
      // Header
      m('.header', [
        m('.header-left', [
          m('h1', 'SwiPerf'),
          m('p', ts
            ? `${fmt_dur(ts.totalDur)}  \u00b7  ${ts.origN} slices` +
              (S.traces.length > 1 ? `  \u00b7  trace ${S.currentIndex + 1}/${S.traces.length}` : '')
            : '\u2014'),
        ]),
        m('.header-right', [
          S.traces.length > 1 ? m('.view-tabs', [
            m('button.view-tab' + (S.viewMode === 'single' ? '.active' : ''), {
              onclick: () => { S.viewMode = 'single' },
              title: 'Single trace view (1)',
            }, ['Single ', m('kbd', '1')]),
            m('button.view-tab' + (S.viewMode === 'overview' ? '.active' : ''), {
              onclick: () => { S.viewMode = 'overview' },
              title: 'Overview grid (2)',
            }, ['Overview ', m('kbd', '2')]),
            m('button.view-tab' + (S.viewMode === 'report' ? '.active' : ''), {
              onclick: () => { S.viewMode = 'report' },
              title: 'Report (3)',
            }, ['Report ', m('kbd', '3')]),
          ]) : null,
          m('button.theme-btn', { onclick: toggleTheme }, [
            m('span.theme-icon', dark ? '\u25cf' : '\u25cb'),
            m('span', dark ? 'Dark' : 'Light'),
          ]),
        ]),
      ]),

      // Import
      m(Import),

      // Main content
      S.viewMode === 'overview' ? m(Overview)
        : S.viewMode === 'report' ? m(Report)
        : ts ? [
            m(NavBar),
            m('.section', [
              m('.section-head', 'Compression'),
              m(Controls, { ts }),
            ]),
            m('.section', { key: ts.trace.trace_uuid + '-tl' }, [
              m('.section-head', 'Timeline'),
              m(Timeline, { ts, key: ts.trace.trace_uuid }),
            ]),
            m('.section', { key: ts.trace.trace_uuid + '-sm' }, [
              m('.section-head', 'Breakdown'),
              m(Summary, { ts }),
            ]),
          ]
        : null,
    ])
  },
}
