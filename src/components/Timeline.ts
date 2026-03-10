import type { MergedSlice } from '../models/types'
import type { TraceState } from '../state'
import { state_color, state_label, name_color, isDark } from '../utils/colors'
import { fmt_dur, fmt_pct } from '../utils/format'

const LONG_PKG_PREFIX = 'com.redfin.android.core.activity.launch.deeplink.'

export interface HitRect {
  x: number; y: number; w: number; h: number; d: MergedSlice
}

export function renderMiniCanvas(canvas: HTMLCanvasElement, ts: TraceState): HitRect[] {
  const seq = ts.currentSeq
  const totalDur = ts.totalDur
  const dpr = window.devicePixelRatio || 1
  const cssW = canvas.parentElement!.clientWidth - 16
  const cssH = 30
  canvas.style.width = cssW + 'px'
  canvas.style.height = cssH + 'px'
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  const ctx = canvas.getContext('2d')
  if (!ctx) return []
  ctx.scale(dpr, dpr)

  const dark = isDark()
  ctx.fillStyle = dark ? '#17171a' : '#ffffff'
  ctx.fillRect(0, 0, cssW, cssH)

  const hits: HitRect[] = []
  const scale = cssW / totalDur
  seq.forEach(d => {
    const x = d.tsRel * scale
    const w = Math.max(d.dur * scale, 0.5)
    ctx.fillStyle = state_color(d)
    ctx.fillRect(x, 0, w, 12)
    ctx.fillStyle = d.name ? name_color(d.name) : (dark ? '#1c1c26' : '#ede9e2')
    ctx.fillRect(x, 14, w, 16)
    hits.push({ x, y: 0, w, h: cssH, d })
  })
  return hits
}

export function showTooltip(e: MouseEvent, hits: HitRect[], totalDur: number) {
  const tip = document.getElementById('tip')
  if (!tip) return
  const canvas = e.target as HTMLCanvasElement
  const rect = canvas.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const hit = hits.slice().reverse().find(r =>
    mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h)

  if (!hit) { tip.style.display = 'none'; return }

  const d = hit.d
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
  const name_display = esc((d.name ?? 'null').replace(LONG_PKG_PREFIX, ''))

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
}

export function hideTooltip() {
  const tip = document.getElementById('tip')
  if (tip) tip.style.display = 'none'
}
