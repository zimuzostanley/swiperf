import m from 'mithril'
import { S, filteredTraces, ensureCache, jumpTo } from '../state'
import type { TraceState } from '../state'
import type { OverviewFilter } from '../models/types'
import { renderMiniCanvas } from './Timeline'
import { fmt_dur } from '../utils/format'
import { state_color, state_label } from '../utils/colors'

const CARD_HEIGHT = 90 // approximate height per card in px
const RENDER_BUFFER = 10 // extra cards above/below viewport

let scrollTop = 0
let containerHeight = 0
const expanded = new Set<string>()

function toggleExpand(uuid: string) {
  if (expanded.has(uuid)) expanded.delete(uuid)
  else expanded.add(uuid)
}

function buildAggregateStats(traces: TraceState[]) {
  const stateAgg: Record<string, { dur: number; count: number; color: string }> = {}
  let totalDur = 0
  traces.forEach(ts => {
    // Use raw slices for aggregate — don't require cache to be built
    if (ts.currentSeq.length > 0) {
      ts.currentSeq.forEach(d => {
        const sk = state_label(d)
        if (!stateAgg[sk]) stateAgg[sk] = { dur: 0, count: 0, color: state_color(d) }
        stateAgg[sk].dur += d.dur
        stateAgg[sk].count += 1
      })
    }
    totalDur += ts.totalDur
  })
  return { stateAgg, totalDur }
}

// Mini canvas that only renders when visible
const MiniTimeline: m.Component<{ ts: TraceState }> = {
  oncreate(vnode) {
    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    ensureCache(vnode.attrs.ts)
    if (canvas) renderMiniCanvas(canvas, vnode.attrs.ts)
  },
  onupdate(vnode) {
    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    ensureCache(vnode.attrs.ts)
    if (canvas) renderMiniCanvas(canvas, vnode.attrs.ts)
  },
  view() {
    return m('.overview-mini-canvas', m('canvas'))
  },
}

const FILTERS: { key: OverviewFilter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'pending', label: 'Pending' },
  { key: 'liked', label: 'Liked' },
  { key: 'disliked', label: 'Disliked' },
]

function renderOverviewCard(ts: TraceState, globalIdx: number) {
  const uuid = ts.trace.trace_uuid
  const isExpanded = expanded.has(uuid)
  const verdict = S.verdicts.get(uuid)

  return m('.card.overview-card', {
    key: uuid,
    class: verdict === 'like' ? 'verdict-liked' : verdict === 'dislike' ? 'verdict-disliked' : '',
  }, [
    m('.collapsible-header', {
      onclick: () => { toggleExpand(uuid) },
    }, [
      m('span.collapse-arrow' + (isExpanded ? '.open' : ''), '\u25b6'),
      m('span.ov-idx', `#${globalIdx + 1}`),
      m('span.ov-pkg', ts.trace.package_name),
      m('span.ov-dur', fmt_dur(ts.totalDur)),
      ts.trace.startup_dur
        ? m('span.ov-startup', fmt_dur(ts.trace.startup_dur))
        : null,
      verdict
        ? m('span.ov-verdict-badge', verdict === 'like' ? '\u2714' : '\u2718')
        : null,
      m('span.ov-actions', [
        m('button.btn' + (verdict === 'like' ? '.active-like' : ''), {
          onclick: (e: Event) => {
            e.stopPropagation()
            const cur = S.verdicts.get(uuid)
            if (cur === 'like') S.verdicts.delete(uuid)
            else S.verdicts.set(uuid, 'like')
          },
        }, '\u2714'),
        m('button.btn' + (verdict === 'dislike' ? '.active-dislike' : ''), {
          onclick: (e: Event) => {
            e.stopPropagation()
            const cur = S.verdicts.get(uuid)
            if (cur === 'dislike') S.verdicts.delete(uuid)
            else S.verdicts.set(uuid, 'dislike')
          },
        }, '\u2718'),
        m('button.btn', {
          onclick: (e: Event) => {
            e.stopPropagation()
            jumpTo(globalIdx)
          },
        }, '\u2192'),
      ]),
    ]),

    m(MiniTimeline, { ts }),

    isExpanded ? m('.collapsible-body', [
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
        // Extra fields
        ...(ts.trace.extra
          ? Object.entries(ts.trace.extra).flatMap(([k, v]) => [
              m('span.tt-k', k),
              m('span.tt-v', String(v)),
            ])
          : []
        ),
      ]),
    ]) : null,
  ])
}

export const Overview: m.Component = {
  view() {
    if (S.traces.length === 0) {
      return m('.section', [
        m('.section-head', 'Overview'),
        m('p', { style: { color: 'var(--dim)', fontSize: '11px' } }, 'No traces loaded.'),
      ])
    }

    const filtered = filteredTraces()
    const { liked, disliked, pending } = S.counts

    // Virtual scrolling: only render visible cards
    const startIdx = Math.max(0, Math.floor(scrollTop / CARD_HEIGHT) - RENDER_BUFFER)
    const visibleCount = Math.ceil(containerHeight / CARD_HEIGHT) + RENDER_BUFFER * 2
    const endIdx = Math.min(filtered.length, startIdx + visibleCount)
    const topPad = startIdx * CARD_HEIGHT
    const bottomPad = Math.max(0, (filtered.length - endIdx) * CARD_HEIGHT)

    return m('.section', [
      m('.section-head', `Overview (${filtered.length}${filtered.length !== S.traces.length ? '/' + S.traces.length : ''} traces)`),

      // Filter bar + stats
      m('.card.overview-toolbar', [
        m('.overview-filters', FILTERS.map(f =>
          m('button.filter-btn' + (S.overviewFilter === f.key ? '.active' : ''), {
            onclick: () => { S.overviewFilter = f.key },
          }, [
            f.label,
            m('span.filter-count',
              f.key === 'all' ? String(S.traces.length)
                : f.key === 'liked' ? String(liked)
                : f.key === 'disliked' ? String(disliked)
                : String(pending)
            ),
          ])
        )),
        m('.overview-summary', [
          m('span.stat-pill.stat-liked', `${liked} liked`),
          m('span.stat-pill.stat-disliked', `${disliked} disliked`),
          m('span.stat-pill.stat-pending', `${pending} pending`),
        ]),
      ]),

      // Scrollable card list
      m('.overview-scroll', {
        onscroll: (e: Event) => {
          const el = e.target as HTMLElement
          scrollTop = el.scrollTop
          containerHeight = el.clientHeight
        },
        oncreate: (vnode: m.VnodeDOM) => {
          containerHeight = (vnode.dom as HTMLElement).clientHeight
        },
      }, [
        // Virtual scroll spacer
        topPad > 0 ? m('div', { style: { height: topPad + 'px' } }) : null,

        // Rendered cards
        filtered.slice(startIdx, endIdx).map((ts, i) => {
          const globalIdx = S.traces.indexOf(ts)
          return renderOverviewCard(ts, globalIdx)
        }),

        // Bottom spacer
        bottomPad > 0 ? m('div', { style: { height: bottomPad + 'px' } }) : null,
      ]),
    ])
  },
}
