export function fmt_dur(ns: number): string {
  if (ns >= 1e9) return (ns / 1e9).toFixed(3) + ' s'
  if (ns >= 1e6) return (ns / 1e6).toFixed(1) + ' ms'
  return (ns / 1e3).toFixed(0) + ' \u00b5s'
}

export function fmt_pct(ns: number, total: number): string {
  if (total === 0) return '0.0%'
  return (ns / total * 100).toFixed(1) + '%'
}
