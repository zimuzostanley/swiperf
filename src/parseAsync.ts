// src/parseAsync.ts — Promise-based API over the parse Web Worker
//
// Provides async functions that offload parsing to a background thread.
// Falls back to synchronous parsing if Workers are unavailable.
// Uses Vite's ?worker&inline to embed the worker code in the main bundle,
// ensuring compatibility with vite-plugin-singlefile (no separate .js file).

import { parseText } from './parse'
import type { TraceEntry } from './models/types'
import InlineWorker from './worker?worker&inline'

// ── Types ──

export type ProgressCallback = (
  message: string,
  current?: number,
  total?: number,
) => void

interface PendingRequest {
  resolve: (data: any) => void
  reject: (err: Error) => void
  onProgress?: ProgressCallback
}

// ── Worker lifecycle ──

let _worker: Worker | null = null
let _workerFailed = false
let _nextId = 1
const _pending = new Map<number, PendingRequest>()

function ensureWorker(): Worker | null {
  if (_workerFailed) return null
  if (_worker) return _worker

  try {
    _worker = new InlineWorker()

    _worker.onmessage = (e: MessageEvent) => {
      const { id, type, data, message, current, total } = e.data
      const handler = _pending.get(id)
      if (!handler) return

      if (type === 'progress') {
        handler.onProgress?.(message, current, total)
      } else if (type === 'result') {
        _pending.delete(id)
        handler.resolve(data)
      } else if (type === 'error') {
        _pending.delete(id)
        handler.reject(new Error(message))
      }
    }

    _worker.onerror = () => {
      _workerFailed = true
      _worker = null
      for (const [, handler] of _pending) {
        handler.reject(new Error('Worker unavailable'))
      }
      _pending.clear()
    }

    return _worker
  } catch {
    _workerFailed = true
    return null
  }
}

function send(
  type: string,
  payload: any,
  onProgress?: ProgressCallback,
): Promise<any> {
  return new Promise((resolve, reject) => {
    const id = _nextId++
    _pending.set(id, { resolve, reject, onProgress })
    ensureWorker()!.postMessage({ id, type, payload })
  })
}

// ── Public API ──

/**
 * Parse text (JSON/TSV/CSV) in a background worker.
 * Falls back to synchronous parsing if worker unavailable.
 */
export async function parseTextAsync(
  text: string,
  name: string,
  onProgress?: ProgressCallback,
): Promise<{ name: string; traces: TraceEntry[] }> {
  if (!ensureWorker()) {
    const traces = parseText(text)
    return { name, traces }
  }
  return send('parse-text', { text, name }, onProgress)
}

/**
 * Parse a session JSON string in a background worker.
 * Returns the raw SessionData object (already JSON.parse'd).
 */
export async function parseSessionAsync(
  json: string,
  onProgress?: ProgressCallback,
): Promise<{ session: any }> {
  if (!ensureWorker()) {
    const data = JSON.parse(json)
    if (data.version !== 1) throw new Error('Unknown session version')
    return { session: data }
  }
  return send('parse-session', { json }, onProgress)
}

/**
 * Parse multiple file contents in a background worker.
 * Each file produces a separate cluster.
 */
export async function parseFilesAsync(
  files: { name: string; content: string }[],
  onProgress?: ProgressCallback,
): Promise<{ files: { name: string; traces: TraceEntry[] }[] }> {
  if (!ensureWorker()) {
    const results: { name: string; traces: TraceEntry[] }[] = []
    for (const f of files) {
      try {
        const traces = parseText(f.content)
        if (traces.length > 0) {
          results.push({ name: f.name.replace(/\.\w+$/, ''), traces })
        }
      } catch { /* skip */ }
    }
    return { files: results }
  }
  return send('parse-files', { files }, onProgress)
}
