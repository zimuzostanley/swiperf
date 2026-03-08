import m from 'mithril'
import type { TraceState } from '../state'
import { ensureCache } from '../state'
import { renderMiniCanvas, showTooltip, hideTooltip } from './Timeline'
import type { HitRect } from './Timeline'

// Store hit rects per canvas element — survives across Mithril lifecycle hooks
const canvasHits = new WeakMap<HTMLCanvasElement, { hits: HitRect[]; totalDur: number }>()

function doRender(dom: Element, ts: TraceState) {
  const canvas = dom.querySelector('canvas') as HTMLCanvasElement
  if (!canvas) return
  ensureCache(ts)
  const hits = renderMiniCanvas(canvas, ts)
  canvasHits.set(canvas, { hits, totalDur: ts.totalDur })
}

export const MiniTimeline: m.Component<{ ts: TraceState }> = {
  oncreate(vnode) {
    doRender(vnode.dom, vnode.attrs.ts)

    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    if (!canvas) return

    canvas.addEventListener('mousemove', (e: MouseEvent) => {
      const data = canvasHits.get(e.target as HTMLCanvasElement)
      if (!data) return
      showTooltip(e, data.hits, data.totalDur)
    })
    canvas.addEventListener('mouseleave', hideTooltip)
  },
  onupdate(vnode) {
    doRender(vnode.dom, vnode.attrs.ts)
  },
  view() {
    return m('.overview-mini-canvas', m('canvas'))
  },
}
