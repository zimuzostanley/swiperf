import m from 'mithril'
import type { TraceState } from '../state'
import { ensureCache } from '../state'
import { renderMiniCanvas, showTooltip, hideTooltip } from './Timeline'
import type { HitRect } from './Timeline'

export const MiniTimeline: m.Component<{ ts: TraceState }> = {
  oncreate(vnode) {
    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    ensureCache(vnode.attrs.ts)
    let hits: HitRect[] = []
    if (canvas) hits = renderMiniCanvas(canvas, vnode.attrs.ts) || []
    ;(vnode.dom as any)._hits = hits
    ;(vnode.dom as any)._totalDur = vnode.attrs.ts.totalDur

    if (canvas) {
      canvas.addEventListener('mousemove', (e: MouseEvent) => {
        const h = (vnode.dom as any)?._hits || []
        const td = (vnode.dom as any)?._totalDur || 0
        showTooltip(e, h, td)
      })
      canvas.addEventListener('mouseleave', hideTooltip)
    }
  },
  onupdate(vnode) {
    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    ensureCache(vnode.attrs.ts)
    if (canvas) {
      const hits = renderMiniCanvas(canvas, vnode.attrs.ts) || []
      ;(vnode.dom as any)._hits = hits
      ;(vnode.dom as any)._totalDur = vnode.attrs.ts.totalDur
    }
  },
  view() {
    return m('.overview-mini-canvas', m('canvas'))
  },
}
