// src/components/Import.ts — Data import UI
//
// Small pastes (<100KB) use synchronous parsing for snappy feel.
// Large pastes, file/directory imports, and session loads all use the
// Web Worker via parseAsync to keep the UI responsive.

import m from 'mithril'
import { parseText } from '../parse'
import { parseTextAsync, parseFilesAsync, parseSessionAsync } from '../parseAsync'
import {
  S, addCluster, loadSingleJson, loadMultipleTraces,
  activeCluster, exportSession, importSessionDataAsync,
} from '../state'

// Re-export pure parsing functions for tests
export {
  resolveField, normalizeSlice, normalizeTrace, resolvePackageName,
  arrayOfArraysToObjects, repairJson, parseDelimitedRows, parseText,
} from '../parse'

let _debounce: ReturnType<typeof setTimeout> | null = null

// ── Paste handler ──

function applyParsedTraces(traces: import('../models/types').TraceEntry[], clusterName: string) {
  if (traces.length === 0) {
    S.importMsg = { text: 'No valid traces found', ok: false }
    return
  }
  if (
    traces.length === 1 &&
    traces[0].package_name === 'unknown' &&
    traces[0].startup_dur === 0
  ) {
    loadSingleJson(traces[0].slices)
    S.importMsg = { text: `Loaded ${traces[0].slices.length} slices`, ok: true }
  } else {
    loadMultipleTraces(clusterName, traces)
    S.importMsg = { text: `Loaded ${traces.length} traces`, ok: true }
  }
}

export async function handleTextInput(text: string, clusterName: string) {
  text = text.trim()
  if (!text) return

  // Small inputs (<100KB) — parse synchronously for snappy feel
  if (text.length < 100_000) {
    try {
      applyParsedTraces(parseText(text), clusterName)
    } catch (err: any) {
      S.importMsg = { text: err.message, ok: false }
    }
    m.redraw()
    return
  }

  // Large inputs — parse in worker to keep UI responsive
  S.loadProgress = { message: 'Parsing pasted data...' }
  m.redraw()

  try {
    const result = await parseTextAsync(text, clusterName, (msg, cur, tot) => {
      S.loadProgress = {
        message: msg,
        pct: tot ? ((cur ?? 0) / tot) * 100 : undefined,
      }
      m.redraw()
    })
    applyParsedTraces(result.traces, clusterName)
  } catch (err: any) {
    S.importMsg = { text: err.message, ok: false }
  }

  S.loadProgress = null
  m.redraw()
}

// ── File reading helper ──

function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error(`Failed to read ${file.name}`))
    reader.readAsText(file)
  })
}

// ── File import (worker-based for large files) ──

async function loadFromFile(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  // Copy file list before clearing input (clearing removes the live FileList)
  const fileList = Array.from(input.files).filter(f =>
    f.name.match(/\.(json|txt|tsv|csv)$/i),
  )
  input.value = ''
  if (fileList.length === 0) {
    S.importMsg = { text: 'No supported files', ok: false }
    m.redraw()
    return
  }

  S.loadProgress = { message: `Reading ${fileList.length} file${fileList.length > 1 ? 's' : ''}...` }
  m.redraw()

  try {
    // Read all files on main thread (FileReader is fast, just I/O)
    const fileContents: { name: string; content: string }[] = []
    for (let i = 0; i < fileList.length; i++) {
      S.loadProgress = {
        message: `Reading file ${i + 1}/${fileList.length}...`,
        pct: (i / fileList.length) * 100,
      }
      m.redraw()
      const content = await readFileAsText(fileList[i])
      fileContents.push({ name: fileList[i].name, content })
    }

    // Parse in worker
    S.loadProgress = { message: 'Parsing...', pct: 0 }
    m.redraw()

    const result = await parseFilesAsync(fileContents, (msg, cur, tot) => {
      S.loadProgress = {
        message: msg,
        pct: tot ? ((cur ?? 0) / tot) * 100 : undefined,
      }
      m.redraw()
    })

    // Create clusters on main thread
    let totalTraces = 0
    for (const { name, traces } of result.files) {
      addCluster(name, traces)
      totalTraces += traces.length
    }

    S.importMsg = totalTraces > 0
      ? { text: `Loaded ${totalTraces} traces from ${result.files.length} files`, ok: true }
      : { text: 'No valid traces found', ok: false }
  } catch (err: any) {
    S.importMsg = { text: err.message, ok: false }
  }

  S.loadProgress = null
  m.redraw()
}

// ── Copy compressed slices ──

function copyCompressed() {
  const cl = activeCluster()
  if (!cl) return
  const ts = cl.traces[0]
  if (!ts) return
  const clean = ts.currentSeq.map(
    ({ ts, dur, name, state, depth, io_wait, blocked_function, _merged }) => ({
      ts, dur, name, state, depth, io_wait, blocked_function, _merged,
    }),
  )
  navigator.clipboard.writeText(JSON.stringify(clean, null, 2))
}

// ── Session save ──

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
  S.importMsg = { text: 'Session saved', ok: true }
  m.redraw()
}

// ── Session load (worker-based for large sessions) ──

async function loadSession(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''

  S.loadProgress = { message: 'Reading session file...' }
  m.redraw()

  try {
    const json = await readFileAsText(file)

    S.loadProgress = { message: 'Parsing session...' }
    m.redraw()

    const { session } = await parseSessionAsync(json, (msg) => {
      S.loadProgress = { message: msg }
      m.redraw()
    })

    // Hydrate state on main thread with granular progress
    await importSessionDataAsync(session, (msg, pct) => {
      S.loadProgress = { message: msg, pct }
      m.redraw()
    })
    S.importMsg = {
      text: `Session restored (${S.clusters.length} clusters)`,
      ok: true,
    }
  } catch (err: any) {
    S.importMsg = { text: `Session load failed: ${err.message}`, ok: false }
  }

  S.loadProgress = null
  m.redraw()
}

// ── Import component ──

export const Import: m.Component = {
  view() {
    const loading = S.loadProgress !== null

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
          disabled: loading,
          onpaste: (e: ClipboardEvent) => {
            // Read from clipboardData and prevent default to avoid
            // inserting large text into the textarea DOM (causes freeze/crash).
            const text = e.clipboardData?.getData('text/plain')
            if (!text?.trim()) return
            e.preventDefault()
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(async () => {
              await handleTextInput(text, 'Paste')
              m.redraw()
            }, 50)
          },
          oninput: (e: Event) => {
            // Fallback for typed/autofill input (not paste)
            if (_debounce) clearTimeout(_debounce)
            _debounce = setTimeout(async () => {
              const el = e.target as HTMLTextAreaElement
              if (el.value.trim()) {
                const text = el.value
                el.value = ''
                await handleTextInput(text, 'Paste')
              }
              m.redraw()
            }, 600)
          },
        }),

        // Progress bar (shown during worker operations)
        S.loadProgress ? m('.load-progress', [
          m('.progress-bar', [
            m('.progress-fill', {
              style: {
                width: S.loadProgress.pct != null
                  ? S.loadProgress.pct + '%'
                  : '100%',
                animation: S.loadProgress.pct == null
                  ? 'pulse 1.5s ease-in-out infinite'
                  : 'none',
              },
            }),
          ]),
          m('.progress-text', S.loadProgress.message),
        ]) : null,

        m('.import-actions', [
          m('button.btn', {
            onclick: () => (document.getElementById('file-input') as HTMLInputElement).click(),
            disabled: loading,
          }, 'Import files\u2026'),
          m('input#file-input', {
            type: 'file', accept: '.json,.txt,.tsv,.csv', multiple: true,
            style: { display: 'none' }, onchange: loadFromFile,
          }),
          m('button.btn', {
            onclick: () => (document.getElementById('dir-input') as HTMLInputElement).click(),
            disabled: loading,
          }, 'Import directory\u2026'),
          m('input#dir-input', {
            type: 'file', style: { display: 'none' }, onchange: loadFromFile,
          }),
          S.clusters.length > 0
            ? m('button.btn', { onclick: saveSession, disabled: loading }, 'Save session')
            : null,
          m('button.btn', {
            onclick: () => (document.getElementById('session-input') as HTMLInputElement).click(),
            disabled: loading,
          }, 'Load session'),
          m('input#session-input', {
            type: 'file', accept: '.json', style: { display: 'none' }, onchange: loadSession,
          }),
          activeCluster()
            ? m('button.btn', { onclick: copyCompressed }, 'Copy compressed')
            : null,
          !loading && S.importMsg
            ? m(
                `span.${S.importMsg.ok ? 'msg-ok' : 'msg-err'}`,
                (S.importMsg.ok ? '\u2713 ' : '\u2717 ') + S.importMsg.text,
              )
            : null,
        ]),
      ]),
    ])
  },
  oncreate() {
    const d = document.getElementById('dir-input')
    if (d) {
      d.setAttribute('webkitdirectory', '')
      d.setAttribute('directory', '')
    }
  },
}
