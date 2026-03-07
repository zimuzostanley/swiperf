import m from 'mithril'
import type { TraceState } from '../state'
import { ensureCache } from '../state'
import { renderMiniCanvas } from './Timeline'

export const MiniTimeline: m.Component<{ ts: TraceState }> = {
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
