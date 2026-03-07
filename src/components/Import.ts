import m from 'mithril'
import type { Slice, TraceEntry } from '../models/types'
import { DEFAULT_COLUMN_CONFIG, DEFAULT_SLICE_FIELD_CONFIG } from '../models/types'
import { S, loadSingleJson, loadMultipleTraces } from '../state'

let _debounce: ReturnType<typeof setTimeout> | null = null

// Resolve a field from an object using alias list + fallback
function resolveField<T>(obj: Record<string, any>, aliases: string[], fallback: T | (() => T)): T {
  for (const alias of aliases) {
    if (obj[alias] !== undefined) return obj[alias] as T
  }
  return typeof fallback === 'function' ? (fallback as () => T)() : fallback
}

// Normalize a raw slice object, handling missing/renamed fields
function normalizeSlice(raw: Record<string, any>): Slice {
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

// Normalize a raw trace object, handling missing/renamed fields
function normalizeTrace(raw: Record<string, any>): TraceEntry | null {
  const cfg = DEFAULT_COLUMN_CONFIG
  const rawSlices = resolveField<any>(raw, cfg.slices.aliases, cfg.slices.fallback)

  let slices: Slice[]
  if (typeof rawSlices === 'string') {
    // Might be base64 or JSON string
    let decoded = rawSlices
    if (!decoded.startsWith('[') && !decoded.startsWith('{')) {
      try { decoded = atob(decoded) } catch { return null }
    }
    try { slices = JSON.parse(decoded).map((s: any) => normalizeSlice(s)) }
    catch { return null }
  } else if (Array.isArray(rawSlices)) {
    slices = rawSlices.map((s: any) => normalizeSlice(s))
  } else {
    return null
  }

  if (slices.length === 0) return null

  // Preserve all extra fields that aren't our known fields
  const knownKeys = new Set([
    ...cfg.trace_uuid.aliases, ...cfg.package_name.aliases,
    ...cfg.startup_dur.aliases, ...cfg.slices.aliases,
  ])
  const extra: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(raw)) {
    if (!knownKeys.has(k)) extra[k] = v
  }

  return {
    trace_uuid: resolveField(raw, cfg.trace_uuid.aliases, cfg.trace_uuid.fallback),
    package_name: resolveField(raw, cfg.package_name.aliases, cfg.package_name.fallback),
    startup_dur: resolveField(raw, cfg.startup_dur.aliases, cfg.startup_dur.fallback),
    slices,
    extra: Object.keys(extra).length > 0 ? extra : undefined,
  }
}

function tryLoadJson(text: string) {
  try {
    const parsed = JSON.parse(text)

    // Case 1: plain array — could be slices or trace entries
    if (Array.isArray(parsed) && parsed.length) {
      // Check if these look like raw slices (have ts+dur)
      const first = parsed[0]
      const looksLikeSlice = DEFAULT_SLICE_FIELD_CONFIG.ts.aliases.some(a => first[a] !== undefined)
        && DEFAULT_SLICE_FIELD_CONFIG.dur.aliases.some(a => first[a] !== undefined)
      const looksLikeTrace = DEFAULT_COLUMN_CONFIG.slices.aliases.some(a => first[a] !== undefined)

      if (looksLikeSlice && !looksLikeTrace) {
        // Array of slices
        loadSingleJson(parsed.map((s: any) => normalizeSlice(s)))
        S.importMsg = { text: `Loaded ${parsed.length} slices`, ok: true }
        return
      }

      if (looksLikeTrace) {
        // Array of trace entries
        const traces = parsed.map((t: any) => normalizeTrace(t)).filter(Boolean) as TraceEntry[]
        if (traces.length === 0) throw new Error('No valid traces in array')
        loadMultipleTraces(traces)
        S.importMsg = { text: `Loaded ${traces.length} traces`, ok: true }
        return
      }

      throw new Error('Array items need ts+dur (slices) or a slices/json/data column (traces)')
    }

    // Case 2: single trace object
    if (typeof parsed === 'object' && parsed !== null) {
      const trace = normalizeTrace(parsed)
      if (trace) {
        loadMultipleTraces([trace])
        S.importMsg = { text: `Loaded 1 trace (${trace.slices.length} slices)`, ok: true }
        return
      }
      throw new Error('Object must have a slices/json/data field')
    }

    throw new Error('Expected array or object')
  } catch (err: any) {
    S.importMsg = { text: err.message, ok: false }
    m.redraw()
  }
}

function tryLoadDelimited(text: string, delimiter: string) {
  try {
    const lines = text.trim().split('\n')
    if (lines.length < 2) throw new Error('Need header + data rows')

    // Parse header
    const headers = parseDelimitedLine(lines[0], delimiter)

    // Find column indices using alias matching
    const cfg = DEFAULT_COLUMN_CONFIG
    const findCol = (aliases: string[]): number =>
      headers.findIndex(h => aliases.some(a => h.toLowerCase().trim() === a.toLowerCase()))

    const slicesIdx = findCol(cfg.slices.aliases)
    const uuidIdx = findCol(cfg.trace_uuid.aliases)
    const pkgIdx = findCol(cfg.package_name.aliases)
    const durIdx = findCol(cfg.startup_dur.aliases)

    if (slicesIdx < 0) {
      throw new Error(`Need a column matching: ${cfg.slices.aliases.join(', ')}`)
    }

    const traces: TraceEntry[] = []
    let parseErrors = 0

    for (let i = 1; i < lines.length; i++) {
      const cols = parseDelimitedLine(lines[i], delimiter)
      if (!cols[slicesIdx]?.trim()) continue

      let raw = cols[slicesIdx].trim()
      // Try base64 decode
      if (!raw.startsWith('[') && !raw.startsWith('{')) {
        try { raw = atob(raw) } catch { /* not base64 */ }
      }

      try {
        const parsed = JSON.parse(raw)
        const sliceArr = Array.isArray(parsed) ? parsed : parsed.slices ?? parsed.data
        if (!Array.isArray(sliceArr) || sliceArr.length === 0) { parseErrors++; continue }

        const slices = sliceArr.map((s: any) => normalizeSlice(s))

        // Collect extra columns
        const extra: Record<string, unknown> = {}
        headers.forEach((h, idx) => {
          if (idx !== slicesIdx && idx !== uuidIdx && idx !== pkgIdx && idx !== durIdx) {
            if (cols[idx]?.trim()) extra[h] = cols[idx].trim()
          }
        })

        traces.push({
          trace_uuid: uuidIdx >= 0 && cols[uuidIdx] ? cols[uuidIdx].trim() : cfg.trace_uuid.fallback(),
          package_name: pkgIdx >= 0 && cols[pkgIdx] ? cols[pkgIdx].trim() : cfg.package_name.fallback(),
          startup_dur: durIdx >= 0 && cols[durIdx] ? parseFloat(cols[durIdx]) || 0 : 0,
          slices,
          extra: Object.keys(extra).length > 0 ? extra : undefined,
        })
      } catch {
        parseErrors++
      }
    }

    if (!traces.length) throw new Error(`No valid traces (${parseErrors} parse errors)`)
    loadMultipleTraces(traces)
    S.importMsg = {
      text: `Loaded ${traces.length} traces` + (parseErrors ? ` (${parseErrors} skipped)` : ''),
      ok: true,
    }
  } catch (err: any) {
    S.importMsg = { text: err.message, ok: false }
    m.redraw()
  }
}

// Parse a delimited line, respecting quoted fields
function parseDelimitedLine(line: string, delimiter: string): string[] {
  const fields: string[] = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') { inQuotes = !inQuotes; continue }
    if (ch === delimiter && !inQuotes) { fields.push(current); current = ''; continue }
    current += ch
  }
  fields.push(current)
  return fields
}

function handleTextInput(text: string) {
  text = text.trim()
  if (!text) return

  if (text.startsWith('[') || text.startsWith('{')) {
    tryLoadJson(text)
  } else {
    const firstLine = text.split('\n')[0]
    if (firstLine.includes('\t')) {
      tryLoadDelimited(text, '\t')
    } else if (firstLine.includes(',')) {
      tryLoadDelimited(text, ',')
    } else {
      tryLoadJson(text)
    }
  }
}

function loadFromFile(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files?.length) return

  if (files.length === 1) {
    const reader = new FileReader()
    reader.onload = (ev) => {
      handleTextInput(ev.target?.result as string)
      m.redraw()
    }
    reader.readAsText(files[0])
  } else {
    // Multiple files selected
    loadMultipleFiles(files)
  }
  input.value = ''
}

function loadMultipleFiles(files: FileList) {
  const traces: TraceEntry[] = []
  let loaded = 0
  const total = files.length

  Array.from(files).forEach(file => {
    if (!file.name.match(/\.(json|txt|tsv|csv)$/i)) { loaded++; checkDone(); return }
    const reader = new FileReader()
    reader.onload = (ev) => {
      try {
        const text = ev.target?.result as string
        const parsed = JSON.parse(text)
        const trace = normalizeTrace(
          Array.isArray(parsed) ? { slices: parsed, trace_uuid: file.name.replace(/\.\w+$/, '') } : parsed
        )
        if (trace) traces.push(trace)
      } catch { /* skip invalid */ }
      loaded++
      checkDone()
    }
    reader.readAsText(file)
  })

  function checkDone() {
    if (loaded < total) return
    if (traces.length) {
      loadMultipleTraces(traces)
      S.importMsg = { text: `Loaded ${traces.length} traces from ${total} files`, ok: true }
    } else {
      S.importMsg = { text: 'No valid traces found in files', ok: false }
    }
    m.redraw()
  }
}

function loadDirectory(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  loadMultipleFiles(input.files)
  input.value = ''
}

function copyCompressed() {
  const ts = S.traces[S.currentIndex]
  if (!ts) return
  const clean = ts.currentSeq.map(({ ts, dur, name, state, depth, io_wait, blocked_function, _merged }) =>
    ({ ts, dur, name, state, depth, io_wait, blocked_function, _merged }))
  navigator.clipboard.writeText(JSON.stringify(clean, null, 2))
}

export const Import: m.Component = {
  view() {
    return m('.section', [
      m('.section-head', 'Data'),
      m('.card.import-body', [
        m('p.import-hint', [
          'Paste JSON, TSV, or CSV. Supports: ',
          m('code', '[{ts, dur, state}]'),
          ' slices, ',
          m('code', '{trace_uuid, slices}'),
          ' traces, or delimited files with a ',
          m('code', 'slices/json/data/base64'),
          ' column. Missing fields use defaults.',
        ]),
        m('textarea.json-area', {
          placeholder: 'Paste JSON / TSV / CSV here\u2026',
          spellcheck: false,
          onpaste: (e: Event) => {
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(() => {
              const el = e.target as HTMLTextAreaElement
              if (el.value.trim()) handleTextInput(el.value)
              m.redraw()
            }, 50)
          },
          oninput: (e: Event) => {
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(() => {
              const el = e.target as HTMLTextAreaElement
              if (el.value.trim()) handleTextInput(el.value)
              m.redraw()
            }, 600)
          },
        }),
        m('.import-actions', [
          m('button.btn', {
            onclick: () => (document.getElementById('file-input') as HTMLInputElement).click(),
          }, 'Import files\u2026'),
          m('input#file-input', {
            type: 'file',
            accept: '.json,.txt,.tsv,.csv',
            multiple: true,
            style: { display: 'none' },
            onchange: loadFromFile,
          }),
          m('button.btn', {
            onclick: () => (document.getElementById('dir-input') as HTMLInputElement).click(),
          }, 'Import directory\u2026'),
          m('input#dir-input', {
            type: 'file',
            style: { display: 'none' },
            onchange: loadDirectory,
          }),
          S.traces.length > 0 ? m('button.btn', { onclick: copyCompressed }, 'Copy compressed') : null,
          S.importMsg ? m(`span.${S.importMsg.ok ? 'msg-ok' : 'msg-err'}`,
            (S.importMsg.ok ? '\u2713 ' : '\u2717 ') + S.importMsg.text
          ) : null,
        ]),
      ]),
    ])
  },
  oncreate() {
    const dirInput = document.getElementById('dir-input')
    if (dirInput) {
      dirInput.setAttribute('webkitdirectory', '')
      dirInput.setAttribute('directory', '')
    }
  },
}
