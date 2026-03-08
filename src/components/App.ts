import m from 'mithril'
import { S, activeCluster, switchCluster, removeCluster, renameCluster } from '../state'
import { Import } from './Import'
import { TraceList } from './TraceList'
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
  },

  view() {
    const dark = isDark()
    const cl = activeCluster()

    return m('.shell', [
      // Header
      m('.header', [
        m('.header-left', [
          m('h1', 'SwiPerf'),
          m('p', cl
            ? `${cl.traces.length} trace${cl.traces.length !== 1 ? 's' : ''} \u00b7 ${cl.counts.positive} positive \u00b7 ${cl.counts.negative} negative`
            : '\u2014'),
        ]),
        m('.header-right', [
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
                title: 'Double-click to rename',
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

      // Main content — unified trace list
      cl ? m(TraceList) : null,
    ])
  },
}
