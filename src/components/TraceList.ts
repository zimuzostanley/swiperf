import m from 'mithril'
import { S, activeCluster, filteredTraces, filterTraces, ensureCache, setVerdict, updateSlider, updateGlobalSlider, getFilterableFields, getFieldValues, togglePropFilter, clearPropFilter, copyFilteredToNewTab } from '../state'
import type { TraceState, Cluster } from '../state'
import type { OverviewFilter } from '../models/types'
import { traceExportRow, rowsToTsv, rowsToJson, buildTraceLink } from '../utils/export'
import type { ExportRow } from '../utils/export'

let openFilterDropdown: string | null = null
let openExportMenu = false
import { MiniTimeline } from './MiniTimeline'
import { Summary } from './Summary'
import { fmt_dur } from '../utils/format'

const expanded = new Set<string>()

function toggleExpand(uuid: string) {
  if (expanded.has(uuid)) expanded.delete(uuid)
  else expanded.add(uuid)
}

const ALL_FILTERS: { id: OverviewFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'positive', label: 'Positive' },
  { id: 'negative', label: 'Negative' },
  { id: 'pending', label: 'Pending' },
  { id: 'discarded', label: 'Discarded' },
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

function buildRows(clusters: Cluster[]): ExportRow[] {
  const rows: ExportRow[] = []
  for (const cl of clusters) {
    for (const ts of cl.traces) {
      rows.push(traceExportRow(ts.trace, ts._key, cl.name, cl.verdicts))
    }
  }
  return rows
}

function doExport(scope: 'tab' | 'all', format: 'json' | 'tsv') {
  const cl = activeCluster()
  if (!cl) return
  const clusters = scope === 'tab' ? [cl] : S.clusters
  const rows = buildRows(clusters)
  const date = new Date().toISOString().slice(0, 10)
  const stem = scope === 'tab' ? `swiperf-${cl.name}` : 'swiperf-all'
  const safeStem = stem.replace(/[^a-zA-Z0-9_-]/g, '_')
  if (format === 'json') {
    downloadFile(rowsToJson(rows), `${safeStem}-${date}.json`, 'application/json')
  } else {
    downloadFile(rowsToTsv(rows), `${safeStem}-${date}.tsv`, 'text/tab-separated-values')
  }
  openExportMenu = false
}

function renderGlobalSlider(cl: Cluster) {
  return m('.trace-slider.global-slider', [
    m('span.slider-label', 'All'),
    m('span.slider-num', String(cl.globalSlider) + '%'),
    m('input[type=range]', {
      min: 1,
      max: 100,
      value: cl.globalSlider,
      step: 1,
      oninput: (e: Event) => {
        updateGlobalSlider(cl, +(e.target as HTMLInputElement).value)
      },
    }),
  ])
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
  const key = ts._key
  const isExpanded = expanded.has(key)
  const verdict = cl.verdicts.get(key)
  ensureCache(ts)

  return m('.card.trace-card', {
    class: verdict === 'like' ? 'verdict-positive' : verdict === 'dislike' ? 'verdict-negative' : verdict === 'discard' ? 'verdict-discard' : '',
  }, [
    m('.trace-card-header', {
      onclick: () => toggleExpand(key),
    }, [
      m('span.collapse-arrow' + (isExpanded ? '.open' : ''), '\u25b6'),
      m('span.trace-idx', `#${idx + 1}`),
      m('span.trace-pkg', ts.trace.package_name),
      ts.trace.startup_dur
        ? m('span.trace-startup-dur', fmt_dur(ts.trace.startup_dur))
        : null,
      (() => {
        const href = buildTraceLink(ts.trace.trace_uuid, ts.trace.extra?.startup_id)
        return href ? m('a.trace-link', {
          href, target: '_blank', rel: 'noopener',
          onclick: (e: Event) => e.stopPropagation(),
          title: 'Open in trace viewer',
        }, '\u2197') : null
      })(),
      m('span.trace-actions', [
        m('button.verdict-btn-sm' + (verdict === 'like' ? '.active-positive' : ''), {
          onclick: (e: Event) => { e.stopPropagation(); setVerdict(cl, key, 'like') },
          title: 'Positive',
        }, '+'),
        m('button.verdict-btn-sm' + (verdict === 'dislike' ? '.active-negative' : ''), {
          onclick: (e: Event) => { e.stopPropagation(); setVerdict(cl, key, 'dislike') },
          title: 'Negative',
        }, '\u2212'),
        m('button.verdict-btn-sm' + (verdict === 'discard' ? '.active-discard' : ''), {
          onclick: (e: Event) => { e.stopPropagation(); setVerdict(cl, key, 'discard') },
          title: 'Discard',
        }, '\u00d7'),
      ]),
    ]),

    m('.trace-card-body', [
      m(MiniTimeline, { ts }),
      renderSlider(ts),
    ]),

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

function filterCount(cl: Cluster, filter: OverviewFilter): number {
  switch (filter) {
    case 'positive': return cl.counts.positive
    case 'negative': return cl.counts.negative
    case 'pending': return cl.counts.pending
    case 'discarded': return cl.counts.discarded
    default: return cl.traces.length
  }
}

function renderFilterBar(cl: Cluster, activeFilter: OverviewFilter, onSelect: (f: OverviewFilter) => void) {
  return m('.list-filters', ALL_FILTERS.map(f =>
    m('button.filter-btn' + (activeFilter === f.id ? '.active' : ''), {
      onclick: () => onSelect(f.id),
    }, [
      f.label,
      m('span.filter-count', String(filterCount(cl, f.id))),
    ])
  ))
}

function renderSortBtn(cl: Cluster) {
  const active = cl.sortField === 'startup_dur'
  return m('button.btn' + (active ? '.active-split' : ''), {
    onclick: () => {
      if (cl.sortField === 'startup_dur') {
        cl.sortDir = cl.sortDir === 1 ? -1 : 1
      } else {
        cl.sortField = 'startup_dur'
        cl.sortDir = 1
      }
    },
    title: active ? 'Click to reverse, double-click index to reset' : 'Sort by startup duration',
  }, active ? `Startup ${cl.sortDir === 1 ? '\u2191' : '\u2193'}` : 'Sort')
}

function renderFilterDropdown(cl: Cluster) {
  const fields = getFilterableFields(cl)
  if (fields.length === 0) return null
  const hasActive = cl.propFilters.size > 0
  return m('.filter-dropdown-wrap', [
    m('button.btn' + (hasActive ? '.active-split' : ''), {
      onclick: (e: Event) => {
        e.stopPropagation()
        openFilterDropdown = openFilterDropdown ? null : cl.id
      },
    }, hasActive ? `Filter (${cl.propFilters.size})` : 'Filter'),
    openFilterDropdown === cl.id ? m('.filter-dropdown', {
      onclick: (e: Event) => e.stopPropagation(),
    }, [
      ...fields.map(field => {
        const values = getFieldValues(cl, field)
        const active = cl.propFilters.get(field)
        return m('.filter-field', [
          m('.filter-field-header', [
            m('span.filter-field-name', field.replace(/_/g, ' ')),
            active ? m('button.filter-clear', {
              onclick: () => clearPropFilter(cl, field),
            }, 'clear') : null,
          ]),
          m('.filter-field-values', values.map(val =>
            m('label.filter-value-label', [
              m('input[type=checkbox]', {
                checked: !active || active.has(val),
                onchange: () => togglePropFilter(cl, field, val),
              }),
              m('span', val || '(empty)'),
            ])
          )),
        ])
      }),
    ]) : null,
  ])
}

function renderExportDropdown(cl: Cluster) {
  return m('.export-dropdown-wrap', [
    m('button.btn.primary', {
      onclick: (e: Event) => {
        e.stopPropagation()
        openExportMenu = !openExportMenu
      },
    }, ['Export ', m('span.export-caret', '\u25be')]),
    openExportMenu ? m('.export-dropdown', {
      onclick: (e: Event) => e.stopPropagation(),
    }, [
      m('.export-section-label', 'This tab'),
      m('button.export-item', {
        onclick: () => doExport('tab', 'json'),
      }, 'JSON'),
      m('button.export-item', {
        onclick: () => doExport('tab', 'tsv'),
      }, 'TSV'),
      S.clusters.length > 1 ? [
        m('.export-section-label', 'All tabs'),
        m('button.export-item', {
          onclick: () => doExport('all', 'json'),
        }, 'JSON'),
        m('button.export-item', {
          onclick: () => doExport('all', 'tsv'),
        }, 'TSV'),
      ] : null,
    ]) : null,
  ])
}

function renderCardList(cl: Cluster, traces: TraceState[]) {
  // Build index map once instead of O(n) indexOf per card
  const idxMap = new Map<TraceState, number>()
  cl.traces.forEach((ts, i) => idxMap.set(ts, i))
  return m('.trace-list', traces.map(ts =>
    renderTraceCard(cl, ts, idxMap.get(ts) ?? 0)
  ))
}

// Split view divider drag state
let _dragging = false

function onDividerDown(e: MouseEvent, cl: Cluster) {
  e.preventDefault()
  _dragging = true

  const onMove = (ev: MouseEvent) => {
    if (!_dragging) return
    const container = (e.target as HTMLElement).parentElement as HTMLElement
    if (!container) return
    const rect = container.getBoundingClientRect()
    const ratio = Math.max(0.15, Math.min(0.85, (ev.clientX - rect.left) / rect.width))
    cl.splitRatio = ratio
    m.redraw()
  }

  const onUp = () => {
    _dragging = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

let _docClickHandler: (() => void) | null = null

export const TraceList: m.Component = {
  oncreate() {
    _docClickHandler = () => {
      let changed = false
      if (openFilterDropdown) { openFilterDropdown = null; changed = true }
      if (openExportMenu) { openExportMenu = false; changed = true }
      if (changed) m.redraw()
    }
    document.addEventListener('click', _docClickHandler)
  },
  onremove() {
    if (_docClickHandler) {
      document.removeEventListener('click', _docClickHandler)
      _docClickHandler = null
    }
  },
  view() {
    const cl = activeCluster()
    if (!cl || cl.traces.length === 0) {
      return m('.section', [
        m('.section-head', 'Traces'),
        m('p', { style: { color: 'var(--dim)', fontSize: '11px' } }, 'No traces loaded.'),
      ])
    }

    const { positive, negative, pending, discarded } = cl.counts
    const filtered = cl.splitView ? null : filteredTraces()

    // Toolbar
    const toolbar = m('.card.list-toolbar', [
      cl.splitView
        ? m('.list-filters-label', 'Split View')
        : renderFilterBar(cl, cl.overviewFilter, f => { cl.overviewFilter = f }),
      m('.list-stats', [
        m('span.stat-pill.stat-positive', `${positive} +`),
        m('span.stat-pill.stat-negative', `${negative} \u2212`),
        m('span.stat-pill.stat-pending', `${pending} ?`),
        discarded > 0 ? m('span.stat-pill.stat-discard', `${discarded} \u00d7`) : null,
      ]),
      renderGlobalSlider(cl),
      m('.list-actions', [
        renderSortBtn(cl),
        renderFilterDropdown(cl),
        m('button.btn' + (cl.splitView ? '.active-split' : ''), {
          onclick: () => { cl.splitView = !cl.splitView },
          title: 'Toggle split view',
        }, cl.splitView ? 'Single' : 'Split'),
        m('button.btn', {
          onclick: () => { if (filtered) copyFilteredToNewTab(cl, filtered) },
          disabled: cl.splitView || !filtered || filtered.length === 0,
          title: cl.splitView ? 'Switch to single view first' : 'Copy visible traces to a new tab',
        }, 'Copy to tab'),
        renderExportDropdown(cl),
      ]),
    ])

    if (filtered) {
      // Normal single-panel view
      return m('.section', [
        m('.section-head', `Traces (${filtered.length}${filtered.length !== cl.traces.length ? '/' + cl.traces.length : ''})`),
        toolbar,
        renderCardList(cl, filtered),
      ])
    }

    // Split view
    const leftTraces = filterTraces(cl, cl.splitFilters[0])
    const rightTraces = filterTraces(cl, cl.splitFilters[1])
    const ratio = cl.splitRatio

    return m('.section', [
      m('.section-head', 'Traces (split view)'),
      toolbar,
      m('.split-container', {
        style: { cursor: _dragging ? 'col-resize' : '' },
      }, [
        m('.split-panel', {
          style: { width: (ratio * 100).toFixed(1) + '%' },
        }, [
          m('.split-panel-header', [
            renderFilterBar(cl, cl.splitFilters[0], f => { cl.splitFilters[0] = f }),
            m('span.split-count', `${leftTraces.length}`),
          ]),
          m('.split-panel-body', renderCardList(cl, leftTraces)),
        ]),
        m('.split-divider', {
          onmousedown: (e: MouseEvent) => onDividerDown(e, cl),
        }),
        m('.split-panel', {
          style: { width: ((1 - ratio) * 100).toFixed(1) + '%' },
        }, [
          m('.split-panel-header', [
            renderFilterBar(cl, cl.splitFilters[1], f => { cl.splitFilters[1] = f }),
            m('span.split-count', `${rightTraces.length}`),
          ]),
          m('.split-panel-body', renderCardList(cl, rightTraces)),
        ]),
      ]),
    ])
  },
}
