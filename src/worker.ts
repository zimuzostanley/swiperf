// src/worker.ts — Web Worker for background parsing
//
// Runs CPU-intensive parsing off the main thread so the UI stays responsive.
// Receives text/JSON payloads, parses them using functions from parse.ts,
// sends progress updates and results back to the main thread.

import { parseText } from './parse'

// ── Message protocol ──

export interface WorkerRequest {
  id: number
  type: 'parse-text' | 'parse-session' | 'parse-files'
  payload: any
}

export interface WorkerResponse {
  id: number
  type: 'progress' | 'result' | 'error'
  data?: any
  message?: string
  current?: number
  total?: number
}

// ── Helpers ──

function sendProgress(
  id: number,
  message: string,
  current?: number,
  total?: number,
) {
  const msg: WorkerResponse = { id, type: 'progress', message, current, total }
  self.postMessage(msg)
}

function sendResult(id: number, data: any) {
  const msg: WorkerResponse = { id, type: 'result', data }
  self.postMessage(msg)
}

function sendError(id: number, message: string) {
  const msg: WorkerResponse = { id, type: 'error', message }
  self.postMessage(msg)
}

// ── Message handler ──

self.onmessage = (e: MessageEvent<WorkerRequest>) => {
  const { id, type, payload } = e.data

  try {
    switch (type) {
      case 'parse-text': {
        const traces = parseText(payload.text, p => {
          sendProgress(id, p.message, p.current, p.total)
        })
        sendResult(id, { name: payload.name, traces })
        break
      }

      case 'parse-session': {
        const sizeMB = (payload.json.length / (1024 * 1024)).toFixed(1)
        sendProgress(id, `Parsing ${sizeMB}MB of session JSON...`)
        const data = JSON.parse(payload.json)
        if (data.version !== 1) throw new Error('Unknown session version')
        const clusterCount = data.clusters?.length || 0
        const traceCount = data.clusters?.reduce(
          (sum: number, c: any) => sum + (c.traces?.length || 0), 0,
        ) || 0
        sendProgress(id, `Parsed ${traceCount} traces in ${clusterCount} cluster${clusterCount !== 1 ? 's' : ''}`)
        sendResult(id, { session: data })
        break
      }

      case 'parse-files': {
        const results: { name: string; traces: any[] }[] = []
        const files = payload.files as { name: string; content: string }[]
        let totalTraces = 0

        for (let i = 0; i < files.length; i++) {
          sendProgress(
            id,
            `Parsing file ${i + 1}/${files.length}: ${files[i].name}`,
            i,
            files.length,
          )
          try {
            const traces = parseText(files[i].content)
            if (traces.length > 0) {
              results.push({
                name: files[i].name.replace(/\.\w+$/, ''),
                traces,
              })
              totalTraces += traces.length
            }
          } catch {
            // Skip unparseable files
          }
        }

        sendProgress(id, `Parsed ${totalTraces} traces from ${results.length} files`)
        sendResult(id, { files: results })
        break
      }
    }
  } catch (err: any) {
    sendError(id, err.message || 'Unknown parsing error')
  }
}
