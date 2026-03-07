import type { MergedSlice } from '../models/types'

// HSLuv minimal port (Apache 2.0)
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

const MD_PALETTE_HSLUV: [number, number, number][] = [
  [4, 90, 58], [340, 82, 52], [291, 64, 42], [262, 52, 47], [231, 48, 48], [207, 90, 54],
  [199, 98, 48], [187, 100, 42], [174, 100, 29], [122, 39, 49], [88, 50, 53], [66, 70, 54],
  [45, 100, 51], [36, 100, 50], [14, 100, 57], [16, 25, 38], [200, 18, 46], [54, 100, 62],
]

const _name_color_cache = new Map<string, string>()

function perfetto_hash(s: string, max: number): number {
  let h = 0x811c9dc5 & 0xffffffff
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619) & 0xffffffff
  }
  return Math.abs(h) % max
}

export function name_color(name: string): string {
  const seed = name.replace(/( )?\d+/g, '')
  if (_name_color_cache.has(seed)) return _name_color_cache.get(seed)!
  const idx = perfetto_hash(seed, MD_PALETTE_HSLUV.length)
  const [ph, ps, pl] = MD_PALETTE_HSLUV[idx]
  const conv = new Hsluv()
  conv.hsluv_h = ph
  conv.hsluv_s = Math.max(0, ps - 20)
  conv.hsluv_l = pl
  conv.hsluvToHex()
  _name_color_cache.set(seed, conv.hex)
  return conv.hex
}

export function isDark(): boolean {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

export function state_color(d: { state: string | null; io_wait: number | null }): string {
  const dark = isDark()
  switch (d.state) {
    case 'Running': return '#357b34'
    case 'Runnable': return '#99b93a'
    case 'Runnable (Preempted)': return '#99b93a'
    case 'Uninterruptible Sleep':
      return (d.io_wait === 1) ? '#e65100' : '#a25c58'
    case 'Sleeping':
      return dark ? '#2a2a3a' : '#c8ccd8'
    default:
      return dark ? '#44444e' : '#aaaaaa'
  }
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
