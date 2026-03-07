import m from 'mithril'
import { S, activeCluster, currentTrace, navigate, setVerdict, ensureCache, switchCluster, removeCluster, renameCluster } from '../state'
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

let editingClusterId: string | null = null
let editingName = ''

export const App: m.Component = {
  oncreate() {
    const saved = localStorage.getItem('trace-theme') || 'light'
    applyTheme(saved)

    document.addEventListener('keydown', (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      const cl = activeCluster()

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
          if (cl && cl.traces.length > 1) { cl.viewMode = 'single'; m.redraw() }
          break
        case '2':
          if (cl && cl.traces.length > 1) { cl.viewMode = 'overview'; m.redraw() }
          break
        case '3':
          if (cl && cl.traces.length > 1) { cl.viewMode = 'report'; m.redraw() }
          break
      }
    })
  },

  view() {
    const dark = isDark()
    const cl = activeCluster()
    const ts = currentTrace()
    if (ts) ensureCache(ts)

    return m('.shell', [
      // Header
      m('.header', [
        m('.header-left', [
          m('h1', 'SwiPerf'),
          m('p', ts
            ? `${fmt_dur(ts.totalDur)}  \u00b7  ${ts.origN} slices` +
              (cl && cl.traces.length > 1 ? `  \u00b7  trace ${cl.currentIndex + 1}/${cl.traces.length}` : '')
            : '\u2014'),
        ]),
        m('.header-right', [
          cl && cl.traces.length > 1 ? m('.view-tabs', [
            m('button.view-tab' + (cl.viewMode === 'single' ? '.active' : ''), {
              onclick: () => { cl.viewMode = 'single' },
              title: 'Single trace view (1)',
            }, ['Single ', m('kbd', '1')]),
            m('button.view-tab' + (cl.viewMode === 'overview' ? '.active' : ''), {
              onclick: () => { cl.viewMode = 'overview' },
              title: 'Overview grid (2)',
            }, ['Overview ', m('kbd', '2')]),
            m('button.view-tab' + (cl.viewMode === 'report' ? '.active' : ''), {
              onclick: () => { cl.viewMode = 'report' },
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

      // Cluster tabs
      S.clusters.length > 0 ? m('.cluster-tabs', S.clusters.map(c =>
        m('.cluster-tab' + (c.id === S.activeClusterId ? '.active' : ''), {
          key: c.id,
          onclick: () => switchCluster(c.id),
        }, [
          editingClusterId === c.id
            ? m('input.cluster-rename', {
                value: editingName,
                oninput: (e: Event) => { editingName = (e.target as HTMLInputElement).value },
                onblur: () => { renameCluster(c.id, editingName); editingClusterId = null },
                onkeydown: (e: KeyboardEvent) => {
                  if (e.key === 'Enter') { renameCluster(c.id, editingName); editingClusterId = null }
                  if (e.key === 'Escape') { editingClusterId = null }
                },
                oncreate: (vnode: m.VnodeDOM) => (vnode.dom as HTMLInputElement).focus(),
                onclick: (e: Event) => e.stopPropagation(),
              })
            : m('span.cluster-name', {
                ondblclick: (e: Event) => {
                  e.stopPropagation()
                  editingClusterId = c.id
                  editingName = c.name
                },
              }, [
                c.name,
                c.traces.length > 1 ? m('span.cluster-count', ` (${c.traces.length})`) : null,
              ]),
          m('button.cluster-close', {
            onclick: (e: Event) => { e.stopPropagation(); removeCluster(c.id) },
            title: 'Close cluster',
          }, '\u00d7'),
        ])
      )) : null,

      // Main content
      cl ? (
        cl.viewMode === 'overview' ? m(Overview)
          : cl.viewMode === 'report' ? m(Report)
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
          : null
      ) : null,
    ])
  },
}
