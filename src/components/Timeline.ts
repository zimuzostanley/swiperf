import m from 'mithril'
import type { MergedSlice } from '../models/types'
import type { TraceState } from '../state'
import { state_color, state_label, name_color, isDark } from '../utils/colors'
import { fmt_dur, fmt_pct } from '../utils/format'

const STATE_H = 10
const GAP = 3
const NAME_H = 21
const AXIS_H = 20
const LABEL_W = 88
const PAD_R = 10
const PAD_T = 8
const CANVAS_H = PAD_T + STATE_H + GAP + NAME_H + AXIS_H + 8

interface HitRect {
  x: number; y: number; w: number; h: number; d: MergedSlice
}

function renderCanvas(canvas: HTMLCanvasElement, ts: TraceState) {
  const seq = ts.currentSeq
  const totalDur = ts.totalDur

  const dpr = window.devicePixelRatio || 1
  const cssW = canvas.parentElement!.clientWidth - 28
  canvas.style.width = cssW + 'px'
  canvas.style.height = CANVAS_H + 'px'
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(CANVAS_H * dpr)

  const ctx = canvas.getContext('2d')!
  ctx.scale(dpr, dpr)

  const drawW = cssW - LABEL_W - PAD_R
  const scale = drawW / totalDur

  const dark = isDark()
  const C_BG = dark ? '#17171a' : '#ffffff'
  const C_TRK = dark ? '#0f0f11' : '#f0ede8'
  const C_SEP = dark ? '#17171a' : '#ffffff'
  const C_DIV = dark ? '#252529' : '#e2e0da'
  const C_LBL = dark ? '#3a3a42' : '#b0ada4'
  const C_TICK = dark ? '#252529' : '#e2e0da'
  const C_AXIS = dark ? '#3a3a42' : '#c0bdb5'
  const C_NULL = dark ? '#1c1c26' : '#ede9e2'
  const C_NULL_T = dark ? 'rgba(90,90,102,0.55)' : 'rgba(160,157,148,0.8)'
  const C_TEXT = dark ? 'rgba(255,255,255,0.75)' : 'rgba(28,27,25,0.80)'
  const C_BADGE = dark ? 'rgba(255,255,255,0.18)' : 'rgba(0,0,0,0.12)'

  ctx.fillStyle = C_BG
  ctx.fillRect(0, 0, cssW, CANVAS_H)

  const stateY = PAD_T
  const nameY = PAD_T + STATE_H + GAP
  const axisY = nameY + NAME_H + 4

  ctx.fillStyle = C_TRK
  ctx.fillRect(LABEL_W, stateY, drawW, STATE_H)
  ctx.fillRect(LABEL_W, nameY, drawW, NAME_H)

  ctx.fillStyle = C_LBL
  ctx.font = `500 9px ${getComputedStyle(document.body).getPropertyValue('--sans').trim()}`
  ctx.textAlign = 'right'
  ctx.textBaseline = 'middle'
  ctx.fillText('State', LABEL_W - 8, stateY + STATE_H / 2)
  ctx.fillText('Name', LABEL_W - 8, nameY + NAME_H / 2)

  ctx.fillStyle = C_DIV
  ctx.fillRect(LABEL_W - 1, stateY, 1, STATE_H + GAP + NAME_H)

  const hitState: HitRect[] = []
  const hitName: HitRect[] = []

  seq.forEach(d => {
    const x = LABEL_W + d.tsRel * scale
    const w = Math.max(d.dur * scale, 0.8)

    ctx.fillStyle = state_color(d)
    ctx.fillRect(x, stateY, w, STATE_H)
    ctx.fillStyle = C_SEP
    ctx.fillRect(x + w - 0.5, stateY, 0.5, STATE_H)
    hitState.push({ x, y: stateY, w, h: STATE_H, d })

    ctx.fillStyle = d.name ? name_color(d.name) : C_NULL
    ctx.fillRect(x, nameY, w, NAME_H)
    ctx.fillStyle = C_SEP
    ctx.fillRect(x + w - 0.5, nameY, 0.5, NAME_H)

    if (w > 12) {
      ctx.save()
      ctx.beginPath(); ctx.rect(x + 3, nameY + 1, w - 6, NAME_H - 2); ctx.clip()
      ctx.font = `400 9px 'IBM Plex Mono', monospace`
      ctx.textBaseline = 'middle'
      ctx.textAlign = 'left'
      if (d.name) {
        ctx.fillStyle = C_TEXT
        let lbl = d.name.replace('com.redfin.android.core.activity.launch.deeplink.', '')
        if (lbl.length > 55) lbl = lbl.slice(0, 53) + '\u2026'
        ctx.fillText(lbl, x + 4, nameY + NAME_H / 2)
      } else {
        ctx.fillStyle = C_NULL_T
        ctx.fillText('null', x + 4, nameY + NAME_H / 2)
      }
      if (d._merged > 1 && w > 40) {
        ctx.fillStyle = C_BADGE
        ctx.textAlign = 'right'
        ctx.textBaseline = 'top'
        ctx.fillText('\u00d7' + d._merged, x + w - 3, nameY + 3)
      }
      ctx.restore()
    }
    hitName.push({ x, y: nameY, w, h: NAME_H, d })
  })

  ctx.strokeStyle = C_TICK; ctx.lineWidth = 1
  ctx.fillStyle = C_AXIS
  ctx.font = `400 9px 'IBM Plex Mono', monospace`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'alphabetic'
  for (let i = 0; i <= 10; i++) {
    const x = LABEL_W + (i / 10) * drawW
    ctx.beginPath(); ctx.moveTo(x, PAD_T); ctx.lineTo(x, axisY); ctx.stroke()
    ctx.fillText(fmt_dur(totalDur / 10 * i), x, axisY + 14)
  }

  return { hitState, hitName }
}

export function renderMiniCanvas(canvas: HTMLCanvasElement, ts: TraceState) {
  const seq = ts.currentSeq
  const totalDur = ts.totalDur
  const dpr = window.devicePixelRatio || 1
  const cssW = canvas.parentElement!.clientWidth - 16
  const cssH = 30
  canvas.style.width = cssW + 'px'
  canvas.style.height = cssH + 'px'
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  const ctx = canvas.getContext('2d')!
  ctx.scale(dpr, dpr)

  const dark = isDark()
  ctx.fillStyle = dark ? '#17171a' : '#ffffff'
  ctx.fillRect(0, 0, cssW, cssH)

  const scale = cssW / totalDur
  seq.forEach(d => {
    const x = d.tsRel * scale
    const w = Math.max(d.dur * scale, 0.5)
    ctx.fillStyle = state_color(d)
    ctx.fillRect(x, 0, w, 12)
    ctx.fillStyle = d.name ? name_color(d.name) : (dark ? '#1c1c26' : '#ede9e2')
    ctx.fillRect(x, 14, w, 16)
  })
}

interface TimelineAttrs {
  ts: TraceState
}

export const Timeline: m.Component<TimelineAttrs> = {
  oncreate(vnode) {
    const canvas = vnode.dom.querySelector('canvas') as HTMLCanvasElement
    const tip = document.getElementById('tip')!
    const ts = vnode.attrs.ts
    let hits = renderCanvas(canvas, ts)

    ;(vnode as any)._hits = hits
    ;(vnode as any)._canvas = canvas
    ;(vnode as any)._ts = ts
    ;(vnode as any)._resizeHandler = () => {
      const curTs = (vnode as any)._ts as TraceState
      hits = renderCanvas(canvas, curTs)
      ;(vnode as any)._hits = hits
    }
    window.addEventListener('resize', (vnode as any)._resizeHandler)

    canvas.addEventListener('mousemove', (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect()
      const mx = e.clientX - rect.left
      const my = e.clientY - rect.top
      const hits = (vnode as any)._hits as { hitState: HitRect[]; hitName: HitRect[] }
      const all = [...hits.hitState, ...hits.hitName]
      const hit = all.slice().reverse().find(r =>
        mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h)

      if (!hit) { tip.style.display = 'none'; return }

      const d = hit.d
      const totalDur = vnode.attrs.ts.totalDur
      const rows = [
        ['state', state_label(d), state_color(d)],
        ['io_wait', d.io_wait !== null ? String(d.io_wait) : '\u2014', null],
        ['blocked', d.blocked_function ?? '\u2014', null],
        ['dur', fmt_dur(d.dur) + '  (' + fmt_pct(d.dur, totalDur) + ')', null],
        ['start', '+' + fmt_dur(d.tsRel), null],
        ['depth', d.depth !== null ? String(d.depth) : '\u2014', null],
        ['\u00d7merged', String(d._merged), null],
      ]

      const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      const name_display = esc((d.name ?? 'null')
        .replace('com.redfin.android.core.activity.launch.deeplink.', ''))

      tip.innerHTML =
        `<div class="tt-name">${name_display}</div>` +
        `<div class="tt-grid">` +
        rows.map(([k, v, col]) =>
          `<span class="tt-k">${esc(String(k))}</span>` +
          `<span class="tt-v"${col ? ` style="color:${esc(String(col))}"` : ''}>${esc(String(v))}</span>`
        ).join('') +
        `</div>`

      tip.style.display = 'block'
      const TW = tip.offsetWidth || 300
      const TH = tip.offsetHeight || 160
      const VW = window.innerWidth
      const VH = window.innerHeight
      let tx = e.clientX + 16
      let ty = e.clientY - 8
      if (tx + TW > VW - 8) tx = e.clientX - TW - 12
      if (ty + TH > VH - 8) ty = VH - TH - 8
      tip.style.left = tx + 'px'
      tip.style.top = ty + 'px'
    })

    canvas.addEventListener('mouseleave', () => { tip.style.display = 'none' })
  },

  onupdate(vnode) {
    const canvas = (vnode as any)._canvas as HTMLCanvasElement
    ;(vnode as any)._ts = vnode.attrs.ts
    if (canvas) {
      const hits = renderCanvas(canvas, vnode.attrs.ts)
      ;(vnode as any)._hits = hits
    }
  },

  onremove(vnode) {
    window.removeEventListener('resize', (vnode as any)._resizeHandler)
  },

  view() {
    return m('.card.canvas-wrap', m('canvas'))
  },
}
