import m from 'mithril'
import type { Slice, TraceEntry } from '../models/types'
import { DEFAULT_COLUMN_CONFIG, DEFAULT_SLICE_FIELD_CONFIG } from '../models/types'
import { S, loadSingleJson, loadMultipleTraces, activeCluster, exportSession, importSession } from '../state'

let _debounce: ReturnType<typeof setTimeout> | null = null

export function resolveField<T>(obj: Record<string, any>, aliases: string[], fallback: T | (() => T)): T {
  for (const alias of aliases) { if (obj[alias] !== undefined) return obj[alias] as T }
  return typeof fallback === 'function' ? (fallback as () => T)() : fallback
}

export function normalizeSlice(raw: Record<string, any>): Slice {
  const cfg = DEFAULT_SLICE_FIELD_CONFIG
  return {
    ts: resolveField(raw, cfg.ts.aliases, cfg.ts.fallback),
    dur: resolveField(raw, cfg.dur.aliases, cfg.dur.fallback),
    name: resolveField(raw, cfg.name.aliases, cfg.name.fallback),
    state: resolveField(raw, cfg.state.aliases, cfg.state.fallback),
    depth: resolveField(raw, cfg.depth.aliases, cfg.depth.fallback),
    io_wait: resolveField(raw, cfg.io_wait.aliases, cfg.io_wait.fallback),
    blocked_function: resolveField(raw, cfg.blocked_function.aliases, cfg.blocked_function.fallback),
  }
}

// Aliases that indicate milliseconds — convert to nanoseconds
const MS_ALIASES = new Set(['startup_dur_ms', 'startup_ms'])

function resolveStartupDur(obj: Record<string, any>): number {
  const cfg = DEFAULT_COLUMN_CONFIG
  for (const alias of cfg.startup_dur.aliases) {
    if (obj[alias] !== undefined) {
      const val = parseFloat(obj[alias]) || 0
      return MS_ALIASES.has(alias) ? val * 1e6 : val
    }
  }
  return 0
}

// If a package_name value is a JSON string like '{"package_name":"com.foo",...}', extract the real name
export function resolvePackageName(raw: Record<string, any>): string {
  const cfg = DEFAULT_COLUMN_CONFIG
  const val = resolveField(raw, cfg.package_name.aliases, cfg.package_name.fallback)
  if (typeof val === 'string' && val.startsWith('{')) {
    try {
      const parsed = JSON.parse(val)
      if (parsed.package_name) return parsed.package_name
    } catch {}
  }
  return val
}

// Convert array-of-arrays [[header,...], [val,...], ...] to array-of-objects
export function arrayOfArraysToObjects(arr: any[][]): Record<string, any>[] {
  const headers = arr[0] as string[]
  return arr.slice(1).map(row => {
    const obj: Record<string, any> = {}
    headers.forEach((h, i) => { if (row[i] !== undefined) obj[h] = row[i] })
    return obj
  })
}

export function normalizeTrace(raw: Record<string, any>): TraceEntry | null {
  const cfg = DEFAULT_COLUMN_CONFIG
  const rawSlices = resolveField<any>(raw, cfg.slices.aliases, cfg.slices.fallback)
  let slices: Slice[]
  if (typeof rawSlices === 'string') {
    let decoded = rawSlices
    if (!decoded.startsWith('[') && !decoded.startsWith('{')) {
      try { decoded = atob(decoded) } catch { return null }
    }
    try {
      let parsedSlices: any
      try { parsedSlices = JSON.parse(decoded) } catch {
        // Try repairing truncated slice JSON
        parsedSlices = JSON.parse(repairJson(decoded))
      }
      slices = (Array.isArray(parsedSlices) ? parsedSlices : []).map((s: any) => normalizeSlice(s))
    } catch { return null }
  } else if (Array.isArray(rawSlices)) {
    slices = rawSlices.map((s: any) => normalizeSlice(s))
  } else { return null }
  if (slices.length === 0) return null
  const knownKeys = new Set([
    ...cfg.trace_uuid.aliases, ...cfg.package_name.aliases,
    ...cfg.startup_dur.aliases, ...cfg.slices.aliases,
  ])
  const extra: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(raw)) { if (!knownKeys.has(k)) extra[k] = v }
  return {
    trace_uuid: resolveField(raw, cfg.trace_uuid.aliases, cfg.trace_uuid.fallback),
    package_name: resolvePackageName(raw),
    startup_dur: resolveStartupDur(raw),
    slices, extra: Object.keys(extra).length > 0 ? extra : undefined,
  }
}

// Attempt to repair truncated JSON by closing open strings/arrays/objects
export function repairJson(text: string): string {
  let result = text.trimEnd()
  // Track nesting outside of strings
  let inStr = false
  let escape = false
  const stack: string[] = []
  for (let i = 0; i < result.length; i++) {
    const ch = result[i]
    if (escape) { escape = false; continue }
    if (ch === '\\' && inStr) { escape = true; continue }
    if (ch === '"' && !escape) { inStr = !inStr; continue }
    if (inStr) continue
    if (ch === '[') stack.push(']')
    else if (ch === '{') stack.push('}')
    else if (ch === ']' || ch === '}') { if (stack.length > 0 && stack[stack.length - 1] === ch) stack.pop() }
  }
  // If we ended inside a string, close it
  if (inStr) result += '"'
  // Close any open arrays/objects
  while (stack.length > 0) result += stack.pop()
  return result
}

function tryLoadJson(text: string, clusterName: string) {
  try {
    let parsed: any
    try { parsed = JSON.parse(text) } catch {
      // Try repairing truncated JSON
      const repaired = repairJson(text)
      parsed = JSON.parse(repaired)
    }
    if (Array.isArray(parsed) && parsed.length) {
      let items = parsed
      // Array-of-arrays: first row is headers, rest are data rows
      if (Array.isArray(parsed[0]) && parsed.length >= 2 && parsed[0].every((h: any) => typeof h === 'string')) {
        items = arrayOfArraysToObjects(parsed)
      }
      const first = items[0]
      if (typeof first !== 'object' || first === null || Array.isArray(first)) {
        throw new Error('Expected array of objects or [headers, ...rows]')
      }
      const looksLikeSlice = DEFAULT_SLICE_FIELD_CONFIG.ts.aliases.some(a => first[a] !== undefined)
        && DEFAULT_SLICE_FIELD_CONFIG.dur.aliases.some(a => first[a] !== undefined)
      const looksLikeTrace = DEFAULT_COLUMN_CONFIG.slices.aliases.some(a => first[a] !== undefined)
      if (looksLikeSlice && !looksLikeTrace) {
        loadSingleJson(items.map((s: any) => normalizeSlice(s)))
        S.importMsg = { text: `Loaded ${items.length} slices`, ok: true }; return
      }
      if (looksLikeTrace) {
        const traces = items.map((t: any) => normalizeTrace(t)).filter(Boolean) as TraceEntry[]
        if (traces.length === 0) throw new Error('No valid traces in array')
        loadMultipleTraces(clusterName, traces)
        S.importMsg = { text: `Loaded ${traces.length} traces`, ok: true }; return
      }
      throw new Error('Array items need ts+dur (slices) or a slices/json/data column (traces)')
    }
    if (typeof parsed === 'object' && parsed !== null) {
      const trace = normalizeTrace(parsed)
      if (trace) {
        loadMultipleTraces(clusterName, [trace])
        S.importMsg = { text: `Loaded 1 trace (${trace.slices.length} slices)`, ok: true }; return
      }
      throw new Error('Object must have a slices/json/data field')
    }
    throw new Error('Expected array or object')
  } catch (err: any) { S.importMsg = { text: err.message, ok: false }; m.redraw() }
}

function parseDelimitedLine(line: string, delimiter: string): string[] {
  const fields: string[] = []; let current = ''; let inQ = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') { inQ = !inQ; continue }
    if (ch === delimiter && !inQ) { fields.push(current); current = ''; continue }
    current += ch
  }
  fields.push(current); return fields
}

function tryLoadDelimited(text: string, delimiter: string, clusterName: string) {
  try {
    const lines = text.trim().split('\n')
    if (lines.length < 2) throw new Error('Need header + data rows')
    const headers = parseDelimitedLine(lines[0], delimiter)
    const cfg = DEFAULT_COLUMN_CONFIG
    const findCol = (aliases: string[]): number =>
      headers.findIndex(h => aliases.some(a => h.toLowerCase().trim() === a.toLowerCase()))
    const slicesIdx = findCol(cfg.slices.aliases)
    const uuidIdx = findCol(cfg.trace_uuid.aliases)
    const pkgIdx = findCol(cfg.package_name.aliases)
    const durIdx = findCol(cfg.startup_dur.aliases)
    const durIsMs = durIdx >= 0 && MS_ALIASES.has(headers[durIdx].toLowerCase().trim())
    if (slicesIdx < 0) throw new Error(`Need a column matching: ${cfg.slices.aliases.join(', ')}`)
    const traces: TraceEntry[] = []; let parseErrors = 0
    for (let i = 1; i < lines.length; i++) {
      const cols = parseDelimitedLine(lines[i], delimiter)
      if (!cols[slicesIdx]?.trim()) continue
      let raw = cols[slicesIdx].trim()
      if (!raw.startsWith('[') && !raw.startsWith('{')) { try { raw = atob(raw) } catch {} }
      try {
        let parsed: any
        try { parsed = JSON.parse(raw) } catch { parsed = JSON.parse(repairJson(raw)) }
        const sliceArr = Array.isArray(parsed) ? parsed : parsed.slices ?? parsed.data
        if (!Array.isArray(sliceArr) || sliceArr.length === 0) { parseErrors++; continue }
        const slices = sliceArr.map((s: any) => normalizeSlice(s))
        const extra: Record<string, unknown> = {}
        headers.forEach((h, idx) => {
          if (idx !== slicesIdx && idx !== uuidIdx && idx !== pkgIdx && idx !== durIdx)
            if (cols[idx]?.trim()) extra[h] = cols[idx].trim()
        })
        let pkgName = pkgIdx >= 0 && cols[pkgIdx] ? cols[pkgIdx].trim() : cfg.package_name.fallback()
        // If package column is JSON like {"package_name":"com.foo",...}, extract the real name
        if (pkgName.startsWith('{')) {
          try { const p = JSON.parse(pkgName); if (p.package_name) pkgName = p.package_name } catch {}
        }
        traces.push({
          trace_uuid: uuidIdx >= 0 && cols[uuidIdx] ? cols[uuidIdx].trim() : cfg.trace_uuid.fallback(),
          package_name: pkgName,
          startup_dur: durIdx >= 0 && cols[durIdx] ? (parseFloat(cols[durIdx]) || 0) * (durIsMs ? 1e6 : 1) : 0,
          slices, extra: Object.keys(extra).length > 0 ? extra : undefined,
        })
      } catch { parseErrors++ }
    }
    if (!traces.length) throw new Error(`No valid traces (${parseErrors} parse errors)`)
    loadMultipleTraces(clusterName, traces)
    S.importMsg = { text: `Loaded ${traces.length} traces` + (parseErrors ? ` (${parseErrors} skipped)` : ''), ok: true }
  } catch (err: any) { S.importMsg = { text: err.message, ok: false }; m.redraw() }
}

export function handleTextInput(text: string, clusterName: string) {
  text = text.trim(); if (!text) return
  if (text.startsWith('[') || text.startsWith('{')) { tryLoadJson(text, clusterName) }
  else {
    const firstLine = text.split('\n')[0]
    if (firstLine.includes('\t')) tryLoadDelimited(text, '\t', clusterName)
    else if (firstLine.includes(',')) tryLoadDelimited(text, ',', clusterName)
    else tryLoadJson(text, clusterName)
  }
}

function loadFromFile(e: Event) {
  const input = e.target as HTMLInputElement; const files = input.files
  if (!files?.length) return
  if (files.length === 1) {
    const file = files[0]
    const name = file.name.replace(/\.\w+$/, '')
    const reader = new FileReader()
    reader.onload = (ev) => { handleTextInput(ev.target?.result as string, name); m.redraw() }
    reader.readAsText(file)
  } else { loadMultipleFiles(files) }
  input.value = ''
}

function loadMultipleFiles(files: FileList) {
  let loaded = 0; const total = files.length; let totalTraces = 0
  const name = total > 1 ? `${total} files` : (files[0]?.name.replace(/\.\w+$/, '') || 'Import')
  Array.from(files).forEach(file => {
    if (!file.name.match(/\.(json|txt|tsv|csv)$/i)) { loaded++; check(); return }
    const reader = new FileReader()
    reader.onload = (ev) => {
      const text = (ev.target?.result as string || '').trim()
      if (text) {
        const before = S.clusters.length
        handleTextInput(text, file.name.replace(/\.\w+$/, ''))
        if (S.clusters.length > before) totalTraces += S.clusters[S.clusters.length - 1].traces.length
      }
      loaded++; check()
    }
    reader.readAsText(file)
  })
  function check() {
    if (loaded < total) return
    if (totalTraces > 0) { S.importMsg = { text: `Loaded ${totalTraces} traces from ${total} files`, ok: true } }
    else { S.importMsg = { text: 'No valid traces found', ok: false } }
    m.redraw()
  }
}

function loadDirectory(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  loadMultipleFiles(input.files); input.value = ''
}

function copyCompressed() {
  const cl = activeCluster(); if (!cl) return
  const ts = cl.traces[0]; if (!ts) return
  const clean = ts.currentSeq.map(({ ts, dur, name, state, depth, io_wait, blocked_function, _merged }) =>
    ({ ts, dur, name, state, depth, io_wait, blocked_function, _merged }))
  navigator.clipboard.writeText(JSON.stringify(clean, null, 2))
}

function saveSession() {
  const json = exportSession()
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  const date = new Date().toISOString().slice(0, 10)
  a.download = `swiperf-session-${date}.json`
  a.click()
  URL.revokeObjectURL(url)
  S.importMsg = { text: 'Session saved', ok: true }; m.redraw()
}

function loadSession(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    try {
      importSession(ev.target?.result as string)
      S.importMsg = { text: `Session restored (${S.clusters.length} clusters)`, ok: true }
    } catch (err: any) {
      S.importMsg = { text: `Session load failed: ${err.message}`, ok: false }
    }
    m.redraw()
  }
  reader.readAsText(file)
  input.value = ''
}

export const Import: m.Component = {
  view() {
    return m('.section', [
      m('.section-head', 'Data'),
      m('.card.import-body', [
        m('p.import-hint', [
          'Each import creates a new cluster tab. Supports: ',
          m('code', '[{ts, dur, state}]'), ' slices, ',
          m('code', '{trace_uuid, slices}'), ' traces, or TSV/CSV with a ',
          m('code', 'slices/json/data/base64'), ' column.',
        ]),
        m('textarea.json-area', {
          placeholder: 'Paste JSON / TSV / CSV \u2014 creates a new cluster tab\u2026',
          spellcheck: false,
          onpaste: (e: Event) => {
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(() => {
              const el = e.target as HTMLTextAreaElement
              if (el.value.trim()) { handleTextInput(el.value, 'Paste'); el.value = '' }
              m.redraw()
            }, 50)
          },
          oninput: (e: Event) => {
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(() => {
              const el = e.target as HTMLTextAreaElement
              if (el.value.trim()) { handleTextInput(el.value, 'Paste'); el.value = '' }
              m.redraw()
            }, 600)
          },
        }),
        m('.import-actions', [
          m('button.btn', { onclick: () => (document.getElementById('file-input') as HTMLInputElement).click() }, 'Import files\u2026'),
          m('input#file-input', { type: 'file', accept: '.json,.txt,.tsv,.csv', multiple: true, style: { display: 'none' }, onchange: loadFromFile }),
          m('button.btn', { onclick: () => (document.getElementById('dir-input') as HTMLInputElement).click() }, 'Import directory\u2026'),
          m('input#dir-input', { type: 'file', style: { display: 'none' }, onchange: loadDirectory }),
          S.clusters.length > 0 ? m('button.btn', { onclick: saveSession }, 'Save session') : null,
          m('button.btn', { onclick: () => (document.getElementById('session-input') as HTMLInputElement).click() }, 'Load session'),
          m('input#session-input', { type: 'file', accept: '.json', style: { display: 'none' }, onchange: loadSession }),
          activeCluster() ? m('button.btn', { onclick: copyCompressed }, 'Copy compressed') : null,
          S.importMsg ? m(`span.${S.importMsg.ok ? 'msg-ok' : 'msg-err'}`, (S.importMsg.ok ? '\u2713 ' : '\u2717 ') + S.importMsg.text) : null,
        ]),
      ]),
    ])
  },
  oncreate() {
    const d = document.getElementById('dir-input')
    if (d) { d.setAttribute('webkitdirectory', ''); d.setAttribute('directory', '') }
  },
}
