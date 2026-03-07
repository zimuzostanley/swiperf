import type { Slice, MergedSlice } from './types'

function token_distance(a: MergedSlice, b: MergedSlice): number {
  let d = 0
  if (a.state !== b.state) {
    d += 4
  } else if (a.state === 'Uninterruptible Sleep' && a.io_wait !== b.io_wait) {
    d += 2
  }
  if (a.name !== b.name) {
    d += (a.name === null || b.name === null) ? 1 : 2
  }
  if (a.blocked_function !== b.blocked_function) {
    d += (a.blocked_function === null || b.blocked_function === null) ? 0.5 : 1
  }
  return d
}

function merge_cost(a: MergedSlice, b: MergedSlice): number {
  const dist = token_distance(a, b)
  if (dist === 0) {
    return 0.01 * Math.log1p((a.dur + b.dur) / 1e6)
  }
  const loser = a.dur <= b.dur ? a : b
  const loser_weight = Math.log1p(loser.dur / 1e6)
    * (1 + (loser.name !== null ? 1 : 0) + (loser.blocked_function !== null ? 1 : 0))
  return dist * loser_weight
}

function merge_two(a: MergedSlice, b: MergedSlice): MergedSlice {
  const winner = (a.dur >= b.dur) ? a : b
  return {
    ts: a.ts,
    tsRel: a.tsRel,
    dur: a.dur + b.dur,
    state: winner.state,
    io_wait: winner.io_wait,
    name: winner.name,
    depth: winner.depth,
    blocked_function: winner.blocked_function,
    _merged: (a._merged || 1) + (b._merged || 1),
  }
}

export function build_merge_cache(raw_data: Slice[]): Map<number, MergedSlice[]> {
  const cache = new Map<number, MergedSlice[]>()
  if (raw_data.length === 0) return cache

  let seq: MergedSlice[] = raw_data.map(d => ({
    ...d, tsRel: d.ts - raw_data[0].ts, _merged: 1,
  }))
  cache.set(seq.length, seq.map(d => ({ ...d })))

  while (seq.length > 2) {
    let best_i = 0, best_cost = Infinity
    for (let i = 0; i < seq.length - 1; i++) {
      const c = merge_cost(seq[i], seq[i + 1])
      if (c < best_cost) { best_cost = c; best_i = i }
    }
    seq = [
      ...seq.slice(0, best_i),
      merge_two(seq[best_i], seq[best_i + 1]),
      ...seq.slice(best_i + 2),
    ]
    if (!cache.has(seq.length)) cache.set(seq.length, seq.map(d => ({ ...d })))
  }
  return cache
}

export function get_compressed(cache: Map<number, MergedSlice[]>, orig_n: number, target: number): MergedSlice[] {
  let best = orig_n
  for (const k of cache.keys()) if (k >= target && k < best) best = k
  return cache.get(best) ?? cache.get(2) ?? []
}
