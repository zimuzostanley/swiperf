import m from 'mithril'
import type { TraceState } from '../state'
import { updateSlider } from '../state'
import { fmt_dur } from '../utils/format'

interface ControlsAttrs {
  ts: TraceState
}

export const Controls: m.Component<ControlsAttrs> = {
  view(vnode) {
    const ts = vnode.attrs.ts
    return m('.card.controls', [
      m('.ctrl-group', [
        m('span.ctrl-label', 'Slices'),
        m('span.count-num', String(ts.currentSeq.length)),
        m('input[type=range]', {
          min: 2,
          max: ts.origN,
          value: ts.sliderValue,
          step: 1,
          oninput: (e: Event) => {
            const val = +(e.target as HTMLInputElement).value
            updateSlider(ts, val)
          },
        }),
        m('span.count-sub', `/ ${ts.origN}`),
      ]),
      m('.vdivider'),
      m('.legend', [
        m('.li', [m('.ls', { style: { background: 'var(--c-running)' } }), 'Running']),
        m('.li', [m('.ls', { style: { background: 'var(--c-runnable)' } }), 'Runnable']),
        m('.li', [m('.ls', { style: { background: 'var(--c-preempted)' } }), 'Runnable (Preempted)']),
        m('.li', [m('.ls', { style: { background: 'var(--c-unint-nonio)' } }), 'Unint. Sleep (non-IO)']),
        m('.li', [m('.ls', { style: { background: 'var(--c-unint-io)' } }), 'Unint. Sleep (IO)']),
        m('.li', [m('.ls', { style: { background: 'var(--c-sleeping-dark)', border: '1px solid var(--border2)' } }), 'Sleeping']),
      ]),
    ])
  },
}
