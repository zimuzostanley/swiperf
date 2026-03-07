import m from 'mithril'
import type { Slice, MergedSlice, SortState, SummaryRow } from '../models/types'
import type { TraceState } from '../state'
import { S } from '../state'
import { state_color, state_label, name_color } from '../utils/colors'
import { fmt_dur, fmt_pct } from '../utils/format'

function build_summary_data(data: MergedSlice[]) {
  const stateMap: Record<string, { dur: number; count: number; color: string }> = {}
  const nameMap: Record<string, { dur: number; count: number }> = {}
  const bfMap: Record<string, { dur: number; count: number }> = {}

  data.forEach(d => {
    const sk = state_label(d)
    if (!stateMap[sk]) stateMap[sk] = { dur: 0, count: 0, color: state_color(d) }
    stateMap[sk].dur += d.dur
    stateMap[sk].count += 1

    if (d.name) {
      if (!nameMap[d.name]) nameMap[d.name] = { dur: 0, count: 0 }
      nameMap[d.name].dur += d.dur
      nameMap[d.name].count += 1
    }

    if (d.blocked_function) {
      if (!bfMap[d.blocked_function]) bfMap[d.blocked_function] = { dur: 0, count: 0 }
      bfMap[d.blocked_function].dur += d.dur
      bfMap[d.blocked_function].count += 1
    }
  })
  return { stateMap, nameMap, bfMap }
}

function sortRows(rows: SummaryRow[], sort: SortState): SummaryRow[] {
  return [...rows].sort((a, b) => {
    let va: any = (a as any)[sort.col]
    let vb: any = (b as any)[sort.col]
    if (sort.col === 'label') { va = va.toLowerCase(); vb = vb.toLowerCase() }
    if (va < vb) return -1 * sort.dir
    if (va > vb) return 1 * sort.dir
    return 0
  })
}

interface TableCardAttrs {
  id: string
  title: string
  rows: SummaryRow[]
  maxDur: number
  totalDur: number
}

const TableCard: m.Component<TableCardAttrs> = {
  view(vnode) {
    const { id, title, rows, maxDur, totalDur } = vnode.attrs
    if (!S.tableSortState[id]) S.tableSortState[id] = { col: 'dur', dir: -1 }
    const sort = S.tableSortState[id]
    const sorted = sortRows(rows, sort)

    const cols = [
      { key: 'label', label: 'Name' },
      { key: 'dur', label: 'Duration' },
      { key: 'pct', label: '%' },
      { key: 'count', label: 'Count' },
    ]

    function onHeaderClick(col: string) {
      if (sort.col === col) { sort.dir = (sort.dir === -1 ? 1 : -1) as 1 | -1 }
      else { sort.col = col; sort.dir = col === 'label' ? 1 : -1 }
    }

    return m('.card.table-card', [
      m('.table-card-head', m('span', title)),
      m('.table-scroll',
        m('table.summary', [
          m('thead',
            m('tr', cols.map(c =>
              m('th', {
                class: sort.col === c.key ? 'sorted' : '',
                onclick: () => onHeaderClick(c.key),
              }, [
                c.label, ' ',
                m('span.sort-arrow', sort.col === c.key ? (sort.dir === -1 ? '\u2193' : '\u2191') : '\u2195'),
              ])
            ))
          ),
          m('tbody', sorted.map(row =>
            m('tr', [
              m('td',
                m('.cell-label', [
                  row.color ? m('span.swatch', { style: { background: row.color } }) : null,
                  m('div', [
                    m('span.name-text', { title: row.label }, row.short || row.label),
                    m('.bar-wrap',
                      m('.bar-fill', {
                        style: {
                          width: (row.dur / maxDur * 100).toFixed(1) + '%',
                          background: row.color || 'var(--accent)',
                        },
                      })
                    ),
                  ]),
                ])
              ),
              m('td', fmt_dur(row.dur)),
              m('td', fmt_pct(row.dur, totalDur)),
              m('td', String(row.count)),
            ])
          )),
        ])
      ),
    ])
  },
}

interface SummaryAttrs {
  ts: TraceState
}

export const Summary: m.Component<SummaryAttrs> = {
  view(vnode) {
    const ts = vnode.attrs.ts
    // Use raw slices (with tsRel and _merged) for full breakdown
    const data = ts.currentSeq
    const totalDur = ts.totalDur
    const { stateMap, nameMap, bfMap } = build_summary_data(data)

    const tables: m.Vnode<any>[] = []

    // States
    const maxState = Math.max(...Object.values(stateMap).map(v => v.dur))
    const stateRows: SummaryRow[] = Object.entries(stateMap).map(([k, v]) =>
      ({ label: k, short: k, dur: v.dur, count: v.count, color: v.color, pct: v.dur }))
    tables.push(m(TableCard, { id: 'state', title: 'States', rows: stateRows, maxDur: maxState, totalDur }))

    // Names
    if (Object.keys(nameMap).length) {
      const maxName = Math.max(...Object.values(nameMap).map(v => v.dur), 1)
      const nameRows: SummaryRow[] = Object.entries(nameMap).map(([k, v]) => ({
        label: k,
        short: k.replace('com.redfin.android.core.activity.launch.deeplink.', ''),
        dur: v.dur, count: v.count, color: name_color(k), pct: v.dur,
      }))
      tables.push(m(TableCard, { id: 'name', title: 'Names', rows: nameRows, maxDur: maxName, totalDur }))
    }

    // Blocked functions
    if (Object.keys(bfMap).length) {
      const maxBf = Math.max(...Object.values(bfMap).map(v => v.dur), 1)
      const bfRows: SummaryRow[] = Object.entries(bfMap).map(([k, v]) =>
        ({ label: k, short: k, dur: v.dur, count: v.count, color: '#c62828', pct: v.dur }))
      tables.push(m(TableCard, { id: 'bf', title: 'Blocked functions', rows: bfRows, maxDur: maxBf, totalDur }))
    }

    return m('.summary-grid', tables)
  },
}
