export const CSS = `
:root {
  --mono: 'IBM Plex Mono', monospace;
  --sans: 'Geist', sans-serif;
  --c-running:        #317d31;
  --c-runnable:       #99ba36;
  --c-preempted:      #99ba36;
  --c-unint-io:       #ff9900;
  --c-unint-nonio:    #a25b57;
  --c-sleeping-dark:  #2a2a3a;
  --c-sleeping-light: #c8ccd8;
  --c-positive:       #2e7d32;
  --c-negative:       #c62828;
}

[data-theme="light"] {
  --bg:          #f5f4f0;
  --surface:     #ffffff;
  --surface2:    #f9f8f5;
  --border:      #e2e0da;
  --border2:     #d0cec7;
  --muted:       #b0ada4;
  --dim:         #888480;
  --label:       #5a5754;
  --text:        #1c1b19;
  --bright:      #0e0d0c;
  --accent:      #5b4fcf;
  --accent-bg:   rgba(91,79,207,0.08);
  --track-bg:    #eceae4;
  --canvas-bg:   #ffffff;
  --c-sleeping:  var(--c-sleeping-light);
  --th-bg:       #f0ede7;
  --row-hover:   #f7f5f0;
  --shadow:      0 1px 3px rgba(0,0,0,0.08), 0 4px 12px rgba(0,0,0,0.04);
}

[data-theme="dark"] {
  --bg:          #0f0f11;
  --surface:     #17171a;
  --surface2:    #1c1c20;
  --border:      #252529;
  --border2:     #2e2e34;
  --muted:       #3a3a42;
  --dim:         #5a5a66;
  --label:       #8888a0;
  --text:        #d4d4e0;
  --bright:      #eeeef5;
  --accent:      #7c6af5;
  --accent-bg:   rgba(124,106,245,0.12);
  --track-bg:    #0f0f11;
  --canvas-bg:   #17171a;
  --c-sleeping:  var(--c-sleeping-dark);
  --th-bg:       #1c1c20;
  --row-hover:   #1e1e24;
  --shadow:      0 1px 3px rgba(0,0,0,0.4), 0 4px 16px rgba(0,0,0,0.3);
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { font-size: 13px; }
body {
  background: var(--bg);
  color: var(--text);
  font-family: var(--sans);
  font-weight: 400;
  line-height: 1.5;
  transition: background 0.2s, color 0.2s;
  min-height: 100vh;
}

.shell { max-width: 1440px; margin: 0 auto; padding: 24px 24px 56px; }

/* Header */
.header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--border);
}
.header-left h1 { font-size: 15px; font-weight: 600; color: var(--bright); letter-spacing: -0.2px; }
.header-left p { font-size: 11px; color: var(--dim); font-family: var(--mono); margin-top: 2px; }
.header-right { display: flex; align-items: center; gap: 8px; }

.theme-btn {
  display: flex; align-items: center; gap: 7px; padding: 6px 12px; border-radius: 6px;
  border: 1px solid var(--border2); background: var(--surface); color: var(--label);
  font-family: var(--sans); font-size: 11px; font-weight: 500; cursor: pointer;
  transition: all 0.15s; white-space: nowrap;
}
.theme-btn:hover { background: var(--surface2); color: var(--text); }
.theme-icon { font-size: 11px; line-height: 1; color: var(--accent); font-weight: 700; }

/* Sections */
.section { margin-bottom: 20px; }
.section-head {
  font-size: 10px; font-weight: 600; letter-spacing: 0.1em; text-transform: uppercase;
  color: var(--muted); margin-bottom: 8px; display: flex; align-items: center; gap: 10px;
}
.section-head::after { content: ''; flex: 1; height: 1px; background: var(--border); }

/* Card */
.card {
  background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
  box-shadow: var(--shadow); transition: background 0.2s, border-color 0.2s;
}

/* Buttons */
.btn {
  font-family: var(--sans); font-size: 11px; font-weight: 500; padding: 5px 11px; border-radius: 5px;
  cursor: pointer; border: 1px solid var(--border2); background: var(--surface2); color: var(--label);
  transition: all 0.15s; white-space: nowrap;
}
.btn:hover { background: var(--border2); color: var(--bright); }
.btn:disabled { opacity: 0.4; cursor: default; }
.btn.primary { background: var(--accent-bg); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }
.btn.primary:hover { background: color-mix(in srgb, var(--accent) 20%, transparent); }

/* Small verdict buttons for trace cards */
.verdict-btn-sm {
  font-family: var(--mono); font-size: 13px; font-weight: 700; width: 28px; height: 28px;
  border-radius: 6px; cursor: pointer; border: 1px solid var(--border2);
  background: var(--surface2); color: var(--label); transition: all 0.12s;
  display: inline-flex; align-items: center; justify-content: center; line-height: 1;
}
.verdict-btn-sm:hover { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.verdict-btn-sm.active-positive { background: var(--c-positive); color: #fff; border-color: var(--c-positive); }
.verdict-btn-sm.active-negative { background: var(--c-negative); color: #fff; border-color: var(--c-negative); }
.verdict-btn-sm.active-discard { background: var(--muted); color: #fff; border-color: var(--muted); }

kbd {
  font-family: var(--mono); font-size: 9px; color: var(--muted); padding: 1px 4px;
  border: 1px solid var(--border2); border-radius: 3px; background: var(--surface2);
}

/* Legend */
.legend { display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
.li { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--label); }
.ls { width: 8px; height: 8px; border-radius: 1px; flex-shrink: 0; }

/* Canvas */
.canvas-wrap { padding: 14px 14px 10px; overflow: hidden; }
canvas { display: block; width: 100%; cursor: crosshair; }

/* Tooltip */
.tooltip {
  position: fixed; background: var(--surface); border: 1px solid var(--border2); border-radius: 7px;
  padding: 11px 14px; font-size: 11px; line-height: 1; pointer-events: none; display: none; z-index: 10001;
  min-width: 280px; max-width: 520px; width: max-content;
  box-shadow: var(--shadow), 0 8px 24px rgba(0,0,0,0.15); transition: background 0.2s, border-color 0.2s;
}
.tt-name { font-weight: 600; color: var(--bright); font-size: 12px; margin-bottom: 8px; line-height: 1.4; word-break: break-all; white-space: normal; }
.tt-grid { display: grid; grid-template-columns: auto 1fr; gap: 4px 10px; align-items: baseline; }
.tt-k { color: var(--muted); font-size: 10px; font-family: var(--mono); white-space: nowrap; }
.tt-v { color: var(--text); font-family: var(--mono); font-size: 10px; word-break: break-all; white-space: normal; line-height: 1.5; }

/* Summary tables */
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
.table-card { overflow: hidden; }
.table-card-head {
  display: flex; align-items: center; justify-content: space-between; padding: 9px 14px;
  border-bottom: 1px solid var(--border); background: var(--th-bg);
  font-size: 10px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted);
}
.table-scroll { max-height: 260px; overflow-y: auto; }
.table-scroll::-webkit-scrollbar { width: 4px; }
.table-scroll::-webkit-scrollbar-track { background: transparent; }
.table-scroll::-webkit-scrollbar-thumb { background: var(--border2); border-radius: 2px; }

table.summary { width: 100%; border-collapse: collapse; }
table.summary thead th {
  position: sticky; top: 0; background: var(--th-bg); padding: 6px 14px; font-size: 10px;
  font-weight: 600; color: var(--dim); text-align: left; letter-spacing: 0.06em; text-transform: uppercase;
  border-bottom: 1px solid var(--border); cursor: pointer; user-select: none; white-space: nowrap;
}
table.summary thead th:hover { color: var(--text); }
table.summary thead th .sort-arrow { margin-left: 4px; opacity: 0.4; }
table.summary thead th.sorted .sort-arrow { opacity: 1; color: var(--accent); }
table.summary thead th:not(:first-child) { text-align: right; }

table.summary tbody tr { transition: background 0.1s; }
table.summary tbody tr:hover { background: var(--row-hover); }
table.summary tbody td {
  padding: 7px 14px; font-size: 11px; color: var(--label); border-bottom: 1px solid var(--border); vertical-align: middle;
}
table.summary tbody tr:last-child td { border-bottom: none; }
table.summary tbody td:first-child { color: var(--text); max-width: 220px; font-size: 11px; }
table.summary tbody td:not(:first-child) { font-family: var(--mono); font-size: 10px; color: var(--dim); text-align: right; white-space: nowrap; }

.cell-label { display: flex; align-items: center; gap: 6px; }
.swatch { width: 7px; height: 7px; border-radius: 1px; flex-shrink: 0; display: inline-block; }
.name-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; display: block; }
.bar-wrap { margin-top: 3px; height: 2px; background: var(--border); border-radius: 1px; overflow: hidden; }
.bar-fill { height: 2px; border-radius: 1px; }

/* Import */
.import-body { padding: 14px; }
.import-hint { font-size: 11px; color: var(--dim); margin-bottom: 10px; }
.import-hint code {
  font-family: var(--mono); font-size: 10px; color: var(--label);
  background: var(--surface2); border: 1px solid var(--border); padding: 1px 5px; border-radius: 3px;
}
textarea.json-area {
  width: 100%; min-height: 90px; max-height: 180px; background: var(--surface2);
  border: 1px solid var(--border); border-radius: 6px; color: var(--label);
  font-family: var(--mono); font-size: 10px; line-height: 1.6; padding: 10px 12px;
  resize: vertical; outline: none; transition: border-color 0.2s;
}
textarea.json-area:focus { border-color: var(--accent); }
.import-actions { display: flex; gap: 8px; margin-top: 9px; align-items: center; flex-wrap: wrap; }
.msg-ok { font-size: 10px; color: #4caf50; font-family: var(--mono); }
.msg-err { font-size: 10px; color: #ef5350; font-family: var(--mono); }

/* Progress bar */
.load-progress { margin-top: 10px; }
.progress-bar {
  height: 3px; border-radius: 2px; background: var(--border2); overflow: hidden;
}
.progress-fill {
  height: 100%; background: var(--accent); border-radius: 2px;
  transition: width 0.2s ease-out;
}
.progress-text {
  font-size: 10px; color: var(--muted); font-family: var(--mono); margin-top: 4px;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* ========================================
   TRACE LIST — unified vertical view
   ======================================== */
.list-toolbar {
  display: flex; align-items: center;
  padding: 8px 16px; margin-bottom: 14px; gap: 10px;
}
.list-filters { display: flex; gap: 4px; flex-shrink: 0; }
.filter-btn {
  font-family: var(--sans); font-size: 11px; font-weight: 500; padding: 4px 10px; border-radius: 5px;
  cursor: pointer; border: 1px solid var(--border2); background: var(--surface2); color: var(--label);
  transition: all 0.15s; display: flex; align-items: center; gap: 5px; white-space: nowrap;
}
.filter-btn:hover { background: var(--border2); color: var(--bright); }
.filter-btn.active { background: var(--accent-bg); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }
.filter-count {
  font-family: var(--mono); font-size: 9px; background: var(--surface); border-radius: 8px;
  padding: 0 5px; min-width: 18px; text-align: center;
}
.fc-positive { color: var(--c-positive); }
.fc-negative { color: var(--c-negative); }
.fc-pending { color: var(--dim); }
.fc-discarded { color: var(--muted); }
.fc-all { color: var(--label); }
.list-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* Trace cards */
.trace-list { display: flex; flex-direction: column; gap: 8px; }
.show-more-wrap { text-align: center; padding: 8px 0; }
.show-more { width: 100%; padding: 8px; font-size: 11px; }

.trace-card { transition: border-color 0.2s; }
.trace-card.verdict-positive { border-color: var(--c-positive); }
.trace-card.verdict-negative { border-color: var(--c-negative); }
.trace-card.verdict-discard { border-color: var(--muted); opacity: 0.5; }

.trace-card-header {
  display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 8px 14px;
  border-bottom: 1px solid var(--border); user-select: none;
}
.trace-card-header:hover { background: var(--row-hover); }
.collapse-arrow { font-size: 10px; color: var(--muted); transition: transform 0.15s; }
.collapse-arrow.open { transform: rotate(90deg); }
.trace-idx { font-family: var(--mono); font-size: 10px; color: var(--dim); min-width: 28px; }
.trace-pkg { font-size: 11px; font-weight: 500; color: var(--bright); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.trace-startup-dur {
  font-family: var(--mono); font-size: 12px; font-weight: 700; color: var(--accent);
  white-space: nowrap;
}
.trace-link {
  font-size: 12px; color: var(--accent); text-decoration: none; opacity: 0.6;
  transition: opacity 0.15s;
}
.trace-link:hover { opacity: 1; }
.trace-actions { margin-left: auto; display: flex; gap: 4px; flex-shrink: 0; }

.trace-card-body { padding: 6px 14px 10px; }

/* Inline slider */
.trace-slider {
  display: flex; align-items: center; gap: 8px; padding: 4px 0;
}
.slider-label { font-size: 10px; color: var(--dim); white-space: nowrap; }
.slider-num { font-family: var(--mono); font-size: 12px; font-weight: 500; color: var(--bright); min-width: 24px; }
.slider-of { font-family: var(--mono); font-size: 10px; color: var(--muted); }
.global-slider { padding: 0; flex: 1; min-width: 0; }
.global-slider input[type=range] { flex: 1; min-width: 60px; }

input[type=range] {
  -webkit-appearance: none; appearance: none; width: 200px; height: 2px; border-radius: 1px;
  background: var(--border2); outline: none; cursor: pointer; transition: background 0.2s;
}
input[type=range]::-webkit-slider-thumb {
  -webkit-appearance: none; width: 11px; height: 11px; border-radius: 50%;
  background: var(--accent); cursor: pointer; box-shadow: 0 0 0 3px var(--accent-bg);
}

/* Mini timeline in card body */
.overview-mini-canvas { padding: 4px 0; }
.overview-mini-canvas canvas { height: 30px; }

/* Expanded detail */
.trace-card-detail {
  border-top: 1px solid var(--border); padding: 14px;
  display: flex; flex-direction: column; gap: 16px;
}
.detail-section { }
.detail-label {
  font-size: 10px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--muted); margin-bottom: 6px;
}
.detail-meta {
  padding: 8px 0; border-top: 1px solid var(--border);
}

/* Cluster tabs */
.cluster-tabs {
  display: flex; gap: 2px; margin-bottom: 16px; flex-wrap: wrap;
  border-bottom: 1px solid var(--border); padding-bottom: 0;
}
.cluster-tab {
  display: flex; align-items: center; gap: 6px; padding: 7px 14px 7px 14px;
  font-size: 11px; font-weight: 500; color: var(--label); cursor: pointer;
  border: 1px solid var(--border); border-bottom: none; border-radius: 6px 6px 0 0;
  background: var(--surface2); transition: all 0.15s; position: relative; top: 1px;
}
.cluster-tab:hover { background: var(--surface); color: var(--text); }
.cluster-tab.active {
  background: var(--surface); color: var(--accent); border-color: var(--border);
  border-bottom: 1px solid var(--surface); font-weight: 600;
}
.cluster-name { cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; }
.cluster-count { font-family: var(--mono); font-size: 9px; color: var(--dim); }
.cluster-close {
  font-size: 14px; line-height: 1; color: var(--muted); background: none; border: none;
  cursor: pointer; padding: 0 2px; border-radius: 3px; transition: all 0.1s;
}
.cluster-close:hover { color: var(--c-negative); background: color-mix(in srgb, var(--c-negative) 10%, transparent); }
.cluster-rename {
  font-family: var(--sans); font-size: 11px; font-weight: 500; color: var(--text);
  background: var(--surface2); border: 1px solid var(--accent); border-radius: 3px;
  padding: 1px 6px; outline: none; width: 120px;
}

/* Split view button */
.btn.active-split { background: var(--accent-bg); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }

/* Split view label in toolbar */
.list-filters-label {
  font-size: 10px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--accent); padding: 4px 0;
}

/* Split container */
.split-container {
  display: flex; width: 100%; min-height: 400px;
  max-height: calc(100vh - 220px);
}
.split-panel {
  display: flex; flex-direction: column; min-width: 0; overflow: hidden;
}
.split-panel-header {
  display: flex; align-items: center; gap: 8px; padding: 6px 8px;
  border-bottom: 1px solid var(--border); background: var(--surface);
  border-radius: 8px 8px 0 0; border: 1px solid var(--border);
  border-bottom: none; flex-wrap: wrap;
}
.split-panel-header .list-filters { gap: 2px; }
.split-panel-header .filter-btn { padding: 2px 8px; font-size: 10px; }
.split-panel-header .filter-count { font-size: 8px; padding: 0 3px; min-width: 14px; }
.split-count {
  font-family: var(--mono); font-size: 9px; color: var(--dim);
  margin-left: auto; white-space: nowrap;
}
.split-panel-body {
  flex: 1; overflow-y: auto; padding: 8px;
  border: 1px solid var(--border); border-radius: 0 0 8px 8px;
  background: var(--bg);
}
.split-panel-body::-webkit-scrollbar { width: 4px; }
.split-panel-body::-webkit-scrollbar-track { background: transparent; }
.split-panel-body::-webkit-scrollbar-thumb { background: var(--border2); border-radius: 2px; }

.split-divider {
  width: 6px; cursor: col-resize; background: var(--border);
  flex-shrink: 0; position: relative; margin: 0 1px;
  transition: background 0.15s;
}
.split-divider:hover { background: var(--accent); }
.split-divider::after {
  content: ''; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  width: 2px; height: 32px; background: var(--muted); border-radius: 1px;
}

/* Filter dropdown */
.filter-dropdown-wrap { position: relative; }
.filter-dropdown {
  position: absolute; top: 100%; right: 0; margin-top: 4px; z-index: 100;
  background: var(--surface); border: 1px solid var(--border2); border-radius: 8px;
  box-shadow: var(--shadow), 0 8px 24px rgba(0,0,0,0.15);
  padding: 8px 0; min-width: 200px; max-height: 360px; overflow-y: auto;
}
.filter-field { padding: 4px 12px; }
.filter-field-header {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  margin-bottom: 4px;
}
.filter-field-name {
  font-size: 10px; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase;
  color: var(--muted);
}
.filter-clear {
  font-size: 9px; color: var(--accent); background: none; border: none;
  cursor: pointer; font-family: var(--sans); padding: 0;
}
.filter-clear:hover { text-decoration: underline; }
.filter-field-values { display: flex; flex-direction: column; gap: 2px; }
.filter-value-label {
  display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--label);
  cursor: pointer; padding: 2px 0;
}
.filter-value-label:hover { color: var(--text); }
.filter-value-label input[type=checkbox] { accent-color: var(--accent); }
.filter-field + .filter-field { border-top: 1px solid var(--border); padding-top: 8px; margin-top: 4px; }

/* Export dropdown */
.export-dropdown-wrap { position: relative; }
.export-caret { font-size: 9px; margin-left: 2px; }
.export-dropdown {
  position: absolute; top: 100%; right: 0; margin-top: 4px; z-index: 100;
  background: var(--surface); border: 1px solid var(--border2); border-radius: 8px;
  box-shadow: var(--shadow), 0 8px 24px rgba(0,0,0,0.15);
  padding: 6px 0; min-width: 120px;
}
.export-section-label {
  font-size: 9px; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase;
  color: var(--muted); padding: 4px 12px 2px;
}
.export-item {
  display: block; width: 100%; text-align: left; padding: 5px 12px;
  font-family: var(--sans); font-size: 11px; color: var(--label); background: none;
  border: none; cursor: pointer; transition: all 0.1s;
}
.export-item:hover { background: var(--accent-bg); color: var(--accent); }

/* ── Cross Compare Modal ── */
.cc-overlay {
  position: fixed; inset: 0; z-index: 10000;
  background: rgba(0,0,0,0.6); display: flex;
  align-items: center; justify-content: center;
}
.cc-modal {
  background: var(--surface); border: 1px solid var(--border2);
  border-radius: 12px; box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  max-width: 1200px; width: 95vw; max-height: 90vh;
  display: flex; flex-direction: column; overflow: hidden;
}
.cc-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; border-bottom: 1px solid var(--border);
}
.cc-title { font-size: 13px; font-weight: 600; color: var(--bright); }
.cc-close {
  background: none; border: none; font-size: 18px; cursor: pointer;
  color: var(--dim); padding: 0 4px; line-height: 1;
}
.cc-close:hover { color: var(--bright); }
.cc-progress { padding: 10px 20px; }
.cc-progress-text {
  font-size: 10px; color: var(--dim); font-family: var(--mono);
  margin-bottom: 4px;
}
.cc-progress-bar {
  height: 4px; background: var(--border); border-radius: 2px; overflow: hidden;
}
.cc-progress-fill {
  height: 100%; background: var(--accent); border-radius: 2px;
  transition: width 0.2s;
}
.cc-body {
  flex: 1; overflow-y: auto; padding: 16px 20px;
  display: flex; flex-direction: column; gap: 12px;
}
.cc-pair {
  display: flex; gap: 12px; align-items: stretch;
}
.cc-pair-divider {
  display: flex; align-items: center; font-size: 11px; font-weight: 600;
  color: var(--dim); padding: 0 4px;
}
.cc-panel {
  flex: 1; border: 2px solid var(--border); border-radius: 8px;
  padding: 10px; display: flex; flex-direction: column; gap: 6px;
  transition: border-color 0.15s; overflow: hidden;
}
.cc-panel.selected { border-color: var(--accent); }
.cc-panel-header {
  display: flex; align-items: center; gap: 8px;
}
.cc-panel-idx { font-family: var(--mono); font-size: 10px; color: var(--dim); }
.cc-panel-pkg { font-size: 11px; font-weight: 500; color: var(--bright); }
.cc-panel-dur {
  font-family: var(--mono); font-size: 12px; font-weight: 700;
  color: var(--accent); margin-left: auto;
}
.cc-panel-detail { max-height: 200px; overflow-y: auto; }
.cc-actions {
  display: flex; gap: 10px; justify-content: center; padding: 4px 0;
}
.cc-action-btn {
  padding: 7px 16px; border-radius: 6px; font-size: 11px;
  font-weight: 600; cursor: pointer; border: 1px solid var(--border2);
  background: var(--surface2); color: var(--label);
  display: flex; align-items: center; gap: 6px; transition: all 0.1s;
}
.cc-action-btn:hover { background: var(--border2); color: var(--bright); }
.cc-action-btn.positive { background: color-mix(in srgb, var(--c-positive) 15%, transparent); color: var(--c-positive); border-color: var(--c-positive); }
.cc-action-btn.positive:hover { background: color-mix(in srgb, var(--c-positive) 25%, transparent); }
.cc-action-btn.negative { background: color-mix(in srgb, var(--c-negative) 15%, transparent); color: var(--c-negative); border-color: var(--c-negative); }
.cc-action-btn.negative:hover { background: color-mix(in srgb, var(--c-negative) 25%, transparent); }
.cc-action-btn kbd {
  font-family: var(--mono); font-size: 9px; padding: 1px 4px;
  border: 1px solid currentColor; border-radius: 3px; opacity: 0.7;
  color: inherit;
}
.cc-slider {
  display: flex; align-items: center; gap: 8px; justify-content: center; padding: 2px 0;
}
.cc-slider input[type=range] { width: 200px; }
.cc-hint {
  font-size: 10px; color: var(--dim); text-align: center;
}
.cc-footer {
  display: flex; gap: 8px; justify-content: center;
  padding: 8px 20px; border-top: 1px solid var(--border);
}
.cc-results { padding: 16px 20px; text-align: center; }
.cc-results-title { font-size: 13px; font-weight: 600; color: var(--bright); margin-bottom: 8px; }
.cc-group-row {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 4px 0; font-size: 11px;
}
.cc-group-label { color: var(--dim); }
.cc-group-count { font-family: var(--mono); font-weight: 600; color: var(--bright); }
`
