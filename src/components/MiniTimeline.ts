import m from 'mithril'
import type { TraceState } from '../state'
import { ensureCache } from '../state'
import { renderMiniCanvas, showTooltip, hideTooltip } from './Timeline'
import type { HitRect } from './Timeline'

// Store hit rects + trace state per canvas element — survives across Mithril lifecycle hooks
const canvasHits = new WeakMap<HTMLCanvasElement, { hits: HitRect[]; totalDur: number; ts: TraceState }>()
const canvasHover = new WeakMap<HTMLCanvasElement, number | undefined>()

// ── Viewport-gated rendering ──
// A shared IntersectionObserver tracks which MiniTimeline elements are in or
// near the viewport. Canvases outside the viewport skip rendering entirely;
// when they scroll into view, the observer triggers a redraw so onupdate fires.

const isVisible = new WeakMap<Element, boolean>()
const needsRender = new WeakSet<Element>()

let observer: IntersectionObserver | null = null
let observerRefCount = 0
const hasIO = typeof IntersectionObserver !== 'undefined'

function getObserver(): IntersectionObserver | null {
  if (!hasIO) return null
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        let anyBecameVisible = false
        for (const entry of entries) {
          const wasVisible = isVisible.get(entry.target) ?? false
          isVisible.set(entry.target, entry.isIntersecting)
          if (entry.isIntersecting && !wasVisible && needsRender.has(entry.target)) {
            needsRender.delete(entry.target)
            anyBecameVisible = true
          }
        }
        if (anyBecameVisible) m.redraw()
      },
      { rootMargin: '200px 0px' },
    )
  }
  observerRefCount++
  return observer
}

function releaseObserver() {
  if (!hasIO) return
  observerRefCount--
  if (observerRefCount <= 0 && observer) {
    observer.disconnect()
    observer = null
    observerRefCount = 0
  }
}

// ── Render logic ──

function doRender(dom: Element, ts: TraceState) {
  if (hasIO && !isVisible.get(dom)) {
    needsRender.add(dom)
    return
  }
  needsRender.delete(dom)
  const canvas = dom.querySelector('canvas') as HTMLCanvasElement
  if (!canvas) return
  ensureCache(ts)
  const hits = renderMiniCanvas(canvas, ts, canvasHover.get(canvas))
  canvasHits.set(canvas, { hits, totalDur: ts.totalDur, ts })
}

// ── Component ──

export const MiniTimeline: m.Component<{ ts: TraceState }> = {
  oncreate(vnode) {
    const obs = getObserver()
    if (obs) {
      // Mark as needing render so the observer callback will trigger a redraw
      // when this element is found to be in the viewport.
      needsRender.add(vnode.dom)
      obs.observe(vnode.dom)
    } else {
      // No IntersectionObserver (jsdom) — render immediately
      doRender(vnode.dom, vnode.attrs.ts)
    }

    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    if (!canvas) return

    canvas.addEventListener('mousemove', (e: MouseEvent) => {
      const cvs = e.target as HTMLCanvasElement
      const data = canvasHits.get(cvs)
      if (!data) return
      showTooltip(e, data.hits, data.totalDur)

      // Find hovered segment index and re-render with highlight
      const rect = cvs.getBoundingClientRect()
      const mx = e.clientX - rect.left
      const my = e.clientY - rect.top
      let hitIdx: number | undefined
      for (let i = data.hits.length - 1; i >= 0; i--) {
        const r = data.hits[i]
        if (mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h) {
          hitIdx = i
          break
        }
      }
      if (hitIdx !== canvasHover.get(cvs)) {
        canvasHover.set(cvs, hitIdx)
        const hits = renderMiniCanvas(cvs, data.ts, hitIdx)
        canvasHits.set(cvs, { hits, totalDur: data.totalDur, ts: data.ts })
      }
    })
    canvas.addEventListener('mouseleave', (e: MouseEvent) => {
      hideTooltip()
      const cvs = e.target as HTMLCanvasElement
      const data = canvasHits.get(cvs)
      if (data && canvasHover.get(cvs) != null) {
        canvasHover.delete(cvs)
        const hits = renderMiniCanvas(cvs, data.ts)
        canvasHits.set(cvs, { hits, totalDur: data.totalDur, ts: data.ts })
      }
    })
  },
  onupdate(vnode) {
    doRender(vnode.dom, vnode.attrs.ts)
  },
  onremove(vnode) {
    if (observer) observer.unobserve(vnode.dom)
    isVisible.delete(vnode.dom)
    needsRender.delete(vnode.dom)
    releaseObserver()
  },
  view() {
    return m('.overview-mini-canvas', m('canvas'))
  },
}
