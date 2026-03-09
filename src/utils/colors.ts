import type { MergedSlice } from '../models/types'

// ── HSLuv minimal port (Apache 2.0) ──
// Used for the procedural hue-based palette, NOT for the MD palette or
// thread states (those use standard HSL, matching Perfetto exactly).

class Hsluv {
  hex = '#000000'
  rgb_r = 0; rgb_g = 0; rgb_b = 0
  xyz_x = 0; xyz_y = 0; xyz_z = 0
  luv_l = 0; luv_u = 0; luv_v = 0
  lch_l = 0; lch_c = 0; lch_h = 0
  hsluv_h = 0; hsluv_s = 0; hsluv_l = 0
  r0s = 0; r0i = 0; r1s = 0; r1i = 0
  g0s = 0; g0i = 0; g1s = 0; g1i = 0
  b0s = 0; b0i = 0; b1s = 0; b1i = 0

  static hexChars = '0123456789abcdef'
  static refY = 1.0; static refU = 0.19783000664283; static refV = 0.46831999493879
  static kappa = 903.2962962; static epsilon = 0.0088564516
  static m_r0 = 3.240969941904521; static m_r1 = -1.537383177570093; static m_r2 = -0.498610760293
  static m_g0 = -0.96924363628087; static m_g1 = 1.87596750150772; static m_g2 = 0.041555057407175
  static m_b0 = 0.055630079696993; static m_b1 = -0.20397695888897; static m_b2 = 1.056971514242878

  static fromLinear(c: number) { return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055 }
  static lToY(L: number) { return L <= 8 ? Hsluv.refY * L / Hsluv.kappa : Hsluv.refY * Math.pow((L + 16) / 116, 3) }
  static rgbChannelToHex(c: number) { const n = Math.round(c * 255), d2 = n % 16, d1 = (n - d2) / 16 | 0; return Hsluv.hexChars[d1] + Hsluv.hexChars[d2] }
  static distanceFromOriginAngle(s: number, i: number, a: number) { const d = i / (Math.sin(a) - s * Math.cos(a)); return d < 0 ? Infinity : d }
  static min6(a: number, b: number, c: number, d: number, e: number, f: number) { return Math.min(a, b, c, d, e, f) }

  rgbToHex() { this.hex = '#' + Hsluv.rgbChannelToHex(this.rgb_r) + Hsluv.rgbChannelToHex(this.rgb_g) + Hsluv.rgbChannelToHex(this.rgb_b) }

  xyzToRgb() {
    this.rgb_r = Hsluv.fromLinear(Hsluv.m_r0 * this.xyz_x + Hsluv.m_r1 * this.xyz_y + Hsluv.m_r2 * this.xyz_z)
    this.rgb_g = Hsluv.fromLinear(Hsluv.m_g0 * this.xyz_x + Hsluv.m_g1 * this.xyz_y + Hsluv.m_g2 * this.xyz_z)
    this.rgb_b = Hsluv.fromLinear(Hsluv.m_b0 * this.xyz_x + Hsluv.m_b1 * this.xyz_y + Hsluv.m_b2 * this.xyz_z)
  }

  luvToXyz() {
    if (this.luv_l === 0) { this.xyz_x = this.xyz_y = this.xyz_z = 0; return }
    const vU = this.luv_u / (13 * this.luv_l) + Hsluv.refU
    const vV = this.luv_v / (13 * this.luv_l) + Hsluv.refV
    this.xyz_y = Hsluv.lToY(this.luv_l)
    this.xyz_x = -9 * this.xyz_y * vU / ((vU - 4) * vV - vU * vV)
    this.xyz_z = (9 * this.xyz_y - 15 * vV * this.xyz_y - vV * this.xyz_x) / (3 * vV)
  }

  lchToLuv() {
    const r = this.lch_h / 180 * Math.PI
    this.luv_l = this.lch_l; this.luv_u = Math.cos(r) * this.lch_c; this.luv_v = Math.sin(r) * this.lch_c
  }

  calculateBoundingLines(l: number) {
    const sub1 = Math.pow(l + 16, 3) / 1560896
    const sub2 = sub1 > Hsluv.epsilon ? sub1 : l / Hsluv.kappa
    const s1r = sub2 * (284517 * Hsluv.m_r0 - 94839 * Hsluv.m_r2), s2r = sub2 * (838422 * Hsluv.m_r2 + 769860 * Hsluv.m_r1 + 731718 * Hsluv.m_r0), s3r = sub2 * (632260 * Hsluv.m_r2 - 126452 * Hsluv.m_r1)
    const s1g = sub2 * (284517 * Hsluv.m_g0 - 94839 * Hsluv.m_g2), s2g = sub2 * (838422 * Hsluv.m_g2 + 769860 * Hsluv.m_g1 + 731718 * Hsluv.m_g0), s3g = sub2 * (632260 * Hsluv.m_g2 - 126452 * Hsluv.m_g1)
    const s1b = sub2 * (284517 * Hsluv.m_b0 - 94839 * Hsluv.m_b2), s2b = sub2 * (838422 * Hsluv.m_b2 + 769860 * Hsluv.m_b1 + 731718 * Hsluv.m_b0), s3b = sub2 * (632260 * Hsluv.m_b2 - 126452 * Hsluv.m_b1)
    this.r0s = s1r / s3r; this.r0i = s2r * l / s3r; this.r1s = s1r / (s3r + 126452); this.r1i = (s2r - 769860) * l / (s3r + 126452)
    this.g0s = s1g / s3g; this.g0i = s2g * l / s3g; this.g1s = s1g / (s3g + 126452); this.g1i = (s2g - 769860) * l / (s3g + 126452)
    this.b0s = s1b / s3b; this.b0i = s2b * l / s3b; this.b1s = s1b / (s3b + 126452); this.b1i = (s2b - 769860) * l / (s3b + 126452)
  }

  calcMaxChromaHsluv(h: number) {
    const r = h / 360 * Math.PI * 2
    return Hsluv.min6(
      Hsluv.distanceFromOriginAngle(this.r0s, this.r0i, r), Hsluv.distanceFromOriginAngle(this.r1s, this.r1i, r),
      Hsluv.distanceFromOriginAngle(this.g0s, this.g0i, r), Hsluv.distanceFromOriginAngle(this.g1s, this.g1i, r),
      Hsluv.distanceFromOriginAngle(this.b0s, this.b0i, r), Hsluv.distanceFromOriginAngle(this.b1s, this.b1i, r)
    )
  }

  hsluvToLch() {
    if (this.hsluv_l > 99.9999999) { this.lch_l = 100; this.lch_c = 0 }
    else if (this.hsluv_l < 0.00000001) { this.lch_l = 0; this.lch_c = 0 }
    else { this.lch_l = this.hsluv_l; this.calculateBoundingLines(this.hsluv_l); this.lch_c = this.calcMaxChromaHsluv(this.hsluv_h) / 100 * this.hsluv_s }
    this.lch_h = this.hsluv_h
  }

  hsluvToHex() { this.hsluvToLch(); this.lchToLuv(); this.luvToXyz(); this.xyzToRgb(); this.rgbToHex() }
}

// ── Standard HSL → hex (matches Perfetto's hslToRgb exactly) ──

function hslToHex(h: number, s: number, l: number): string {
  s /= 100; l /= 100
  const c = (1 - Math.abs(2 * l - 1)) * s
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = l - c / 2
  let r = 0, g = 0, b = 0
  if (h < 60) { r = c; g = x }
  else if (h < 120) { r = x; g = c }
  else if (h < 180) { g = c; b = x }
  else if (h < 240) { g = x; b = c }
  else if (h < 300) { r = x; b = c }
  else { r = c; b = x }
  const ri = Math.round((r + m) * 255)
  const gi = Math.round((g + m) * 255)
  const bi = Math.round((b + m) * 255)
  return '#' + [ri, gi, bi].map(v => v.toString(16).padStart(2, '0')).join('')
}

// ── FNV-1a hash — exact Perfetto implementation ──
// Note: Perfetto uses 0xfffffff (28-bit mask) for the initial value,
// NOT 0xffffffff. This produces different results from standard FNV-1a.

export function perfetto_hash(s: string, max: number): number {
  let h = 0x811c9dc5 & 0xfffffff
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = (h * 16777619) & 0xffffffff
  }
  return Math.abs(h) % max
}

// ── Material Design palette — standard HSL, desaturated by 20 ──
// Matches Perfetto's MD_PALETTE_RAW.map(c => c.desaturate(20))

const MD_PALETTE_HSL: [number, number, number][] = [
  [4, 90, 58], [340, 82, 52], [291, 64, 42], [262, 52, 47], [231, 48, 48], [207, 90, 54],
  [199, 98, 48], [187, 100, 42], [174, 100, 29], [122, 39, 49], [88, 50, 53], [66, 70, 54],
  [45, 100, 51], [36, 100, 50], [14, 100, 57], [16, 25, 38], [200, 18, 46], [54, 100, 62],
]

// Pre-computed: MD_PALETTE_HSL with saturation reduced by 20 → hex
const MD_PALETTE_HEX: string[] = MD_PALETTE_HSL.map(
  ([h, s, l]) => hslToHex(h, Math.max(0, s - 20), l),
)

const _name_color_cache = new Map<string, string>()

/**
 * Assign a color to a slice/function name using Perfetto's exact algorithm:
 * 1. Strip trailing digits from name
 * 2. FNV-1a hash with Perfetto's init value → index into 18-color MD palette
 * 3. MD palette values are standard HSL, desaturated by 20
 */
export function name_color(name: string): string {
  const seed = name.replace(/( )?\d+/g, '')
  if (_name_color_cache.has(seed)) return _name_color_cache.get(seed)!
  const idx = perfetto_hash(seed, MD_PALETTE_HEX.length)
  const hex = MD_PALETTE_HEX[idx]
  _name_color_cache.set(seed, hex)
  return hex
}

export function isDark(): boolean {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

// ── Thread state colors — standard HSL, matches Perfetto exactly ──
// Source: perfetto/ui/src/plugins/dev.perfetto.Sched/common.ts

const STATE_RUNNING      = hslToHex(120, 44, 34)  // DARK_GREEN
const STATE_RUNNABLE     = hslToHex(75, 55, 47)   // LIME_GREEN
const STATE_IO_WAIT      = hslToHex(36, 100, 50)  // ORANGE
const STATE_NONIO        = hslToHex(3, 30, 49)    // DESAT_RED
const STATE_CREATED      = hslToHex(0, 0, 70)     // LIGHT_GRAY
const STATE_UNKNOWN      = hslToHex(44, 63, 91)   // OFF_WHITE_YELLOW
const STATE_DEAD         = hslToHex(0, 0, 62)     // GRAY
const STATE_INDIGO       = hslToHex(231, 48, 48)  // INDIGO (default)

export function state_color(d: { state: string | null; io_wait: number | null }): string {
  const s = d.state
  if (!s) return isDark() ? '#44444e' : STATE_UNKNOWN
  if (s === 'Created') return STATE_CREATED
  if (s === 'Running') return STATE_RUNNING
  if (s.startsWith('Runnable')) return STATE_RUNNABLE
  if (s.includes('Uninterruptible Sleep')) {
    // Perfetto uses combined strings like 'Uninterruptible Sleep (non-IO)';
    // swiperf stores io_wait as a separate field
    if (s.includes('non-IO') || d.io_wait === 0) return STATE_NONIO
    return STATE_IO_WAIT
  }
  if (s.includes('Dead')) return STATE_DEAD
  if (s.includes('Sleeping') || s.includes('Idle')) {
    return isDark() ? '#2a2a3a' : '#ffffff'
  }
  if (s.includes('Unknown')) return STATE_UNKNOWN
  return STATE_INDIGO
}

export function state_label(d: { state: string | null; io_wait: number | null }): string {
  if (!d.state) return 'Unknown'
  if (d.state === 'Uninterruptible Sleep') {
    if (d.io_wait === 1) return 'Unint. Sleep (IO)'
    if (d.io_wait === 0) return 'Unint. Sleep (non-IO)'
    return 'Unint. Sleep'
  }
  return d.state
}
