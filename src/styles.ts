export const CSS = `
:root {
  --mono: 'IBM Plex Mono', monospace;
  --sans: 'Geist', sans-serif;
  --c-running:        #357b34;
  --c-runnable:       #99b93a;
  --c-preempted:      #99b93a;
  --c-unint-io:       #e65100;
  --c-unint-nonio:    #a25c58;
  --c-sleeping-dark:  #2a2a3a;
  --c-sleeping-light: #c8ccd8;
  --c-like:           #2e7d32;
  --c-dislike:        #c62828;
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

/* Controls */
.controls { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 12px 16px; }
.ctrl-group { display: flex; align-items: center; gap: 10px; }
.ctrl-label { font-size: 11px; color: var(--dim); white-space: nowrap; }
.count-num { font-family: var(--mono); font-size: 20px; font-weight: 500; color: var(--bright); min-width: 32px; transition: color 0.2s; }
.count-sub { font-family: var(--mono); font-size: 10px; color: var(--muted); }

input[type=range] {
  -webkit-appearance: none; appearance: none; width: 200px; height: 2px; border-radius: 1px;
  background: var(--border2); outline: none; cursor: pointer; transition: background 0.2s;
}
input[type=range]::-webkit-slider-thumb {
  -webkit-appearance: none; width: 11px; height: 11px; border-radius: 50%;
  background: var(--accent); cursor: pointer; box-shadow: 0 0 0 3px var(--accent-bg);
}

.vdivider { width: 1px; height: 20px; background: var(--border); flex-shrink: 0; }

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
.btn.active-like { background: var(--c-like); color: #fff; border-color: var(--c-like); }
.btn.active-dislike { background: var(--c-dislike); color: #fff; border-color: var(--c-dislike); }

/* Verdict buttons — bigger, more tactile */
.verdict-btn {
  font-family: var(--sans); font-size: 12px; font-weight: 600; padding: 6px 16px; border-radius: 6px;
  cursor: pointer; border: 2px solid var(--border2); background: var(--surface2); color: var(--label);
  transition: all 0.12s; white-space: nowrap; display: inline-flex; align-items: center; gap: 6px;
}
.verdict-btn:hover { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.verdict-btn.active-like { background: var(--c-like); color: #fff; border-color: var(--c-like); transform: scale(1.02); }
.verdict-btn.active-dislike { background: var(--c-dislike); color: #fff; border-color: var(--c-dislike); transform: scale(1.02); }

kbd {
  font-family: var(--mono); font-size: 9px; color: var(--muted); padding: 1px 4px;
  border: 1px solid var(--border2); border-radius: 3px; background: var(--surface2);
}
.verdict-btn.active-like kbd,
.verdict-btn.active-dislike kbd { color: rgba(255,255,255,0.7); border-color: rgba(255,255,255,0.3); background: rgba(255,255,255,0.15); }

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
  padding: 11px 14px; font-size: 11px; line-height: 1; pointer-events: none; display: none; z-index: 9999;
  min-width: 280px; max-width: 520px; width: max-content;
  box-shadow: var(--shadow), 0 8px 24px rgba(0,0,0,0.15); transition: background 0.2s, border-color 0.2s;
}
.tt-name { font-weight: 600; color: var(--bright); font-size: 12px; margin-bottom: 8px; line-height: 1.4; word-break: break-all; white-space: normal; }
.tt-grid { display: grid; grid-template-columns: 80px 1fr; gap: 4px 10px; align-items: baseline; }
.tt-k { color: var(--muted); font-size: 10px; font-family: var(--mono); }
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

/* ========================================
   NAV BAR — multi-trace navigation
   ======================================== */
.progress-bar-wrap {
  position: relative; height: 22px; background: var(--surface2); border-radius: 8px 8px 0 0;
  overflow: hidden; border-bottom: 1px solid var(--border);
}
.progress-bar-fill {
  position: absolute; left: 0; top: 0; height: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--c-like) 30%, transparent), color-mix(in srgb, var(--accent) 30%, transparent));
  transition: width 0.3s ease;
}
.progress-bar-text {
  position: relative; z-index: 1; text-align: center; font-family: var(--mono);
  font-size: 10px; line-height: 22px; color: var(--dim); font-weight: 500;
}

.nav-bar {
  display: flex; align-items: center; gap: 12px; padding: 10px 16px; flex-wrap: wrap;
}
.nav-group { display: flex; align-items: center; gap: 8px; }
.nav-counter { font-family: var(--mono); font-size: 12px; color: var(--bright); font-weight: 500; min-width: 60px; text-align: center; }
.nav-info { display: flex; align-items: center; gap: 8px; flex-shrink: 1; min-width: 0; overflow: hidden; }
.nav-pkg { font-size: 11px; font-weight: 500; color: var(--bright); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 200px; }
.nav-dur { font-family: var(--mono); font-size: 10px; color: var(--dim); }
.nav-startup { font-family: var(--mono); font-size: 10px; color: var(--muted); }
.nav-verdict { display: flex; gap: 8px; }
.nav-stats { display: flex; gap: 6px; align-items: center; }

/* Stat pills */
.stat-pill {
  font-family: var(--mono); font-size: 10px; font-weight: 500; padding: 2px 8px;
  border-radius: 10px; white-space: nowrap;
}
.stat-liked { background: color-mix(in srgb, var(--c-like) 15%, transparent); color: var(--c-like); }
.stat-disliked { background: color-mix(in srgb, var(--c-dislike) 15%, transparent); color: var(--c-dislike); }
.stat-pending { background: var(--surface2); color: var(--dim); }

/* Auto-advance toggle */
.auto-advance {
  display: flex; align-items: center; gap: 5px; font-size: 10px; color: var(--dim);
  cursor: pointer; white-space: nowrap; margin-left: auto;
}
.auto-advance input { cursor: pointer; }

/* ========================================
   OVERVIEW
   ======================================== */
.overview-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;
}
.overview-filters { display: flex; gap: 4px; }
.filter-btn {
  font-family: var(--sans); font-size: 11px; font-weight: 500; padding: 4px 12px; border-radius: 5px;
  cursor: pointer; border: 1px solid var(--border2); background: var(--surface2); color: var(--label);
  transition: all 0.15s; display: flex; align-items: center; gap: 6px;
}
.filter-btn:hover { background: var(--border2); color: var(--bright); }
.filter-btn.active { background: var(--accent-bg); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }
.filter-count {
  font-family: var(--mono); font-size: 9px; background: var(--surface); border-radius: 8px;
  padding: 0 5px; min-width: 18px; text-align: center;
}
.overview-summary { display: flex; gap: 8px; align-items: center; }

.overview-scroll {
  max-height: calc(100vh - 200px); overflow-y: auto; display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 10px;
  align-content: start;
}
.overview-scroll::-webkit-scrollbar { width: 4px; }
.overview-scroll::-webkit-scrollbar-track { background: transparent; }
.overview-scroll::-webkit-scrollbar-thumb { background: var(--border2); border-radius: 2px; }

.overview-card { transition: transform 0.1s, border-color 0.2s; }
.overview-card:hover { transform: translateY(-1px); }
.overview-card.verdict-liked { border-color: var(--c-like); }
.overview-card.verdict-disliked { border-color: var(--c-dislike); }

.overview-card-head {
  display: flex; align-items: center; gap: 8px; padding: 8px 14px;
  border-bottom: 1px solid var(--border); flex-wrap: wrap;
}
.overview-card-head .pkg { font-size: 11px; font-weight: 500; color: var(--bright); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 200px; }
.overview-card-head .dur { font-family: var(--mono); font-size: 10px; color: var(--dim); }
.overview-mini-canvas { padding: 6px 8px; }
.overview-mini-canvas canvas { height: 30px; }

/* Collapsible */
.collapsible-header {
  display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 8px 14px;
  border-bottom: 1px solid var(--border); user-select: none;
}
.collapsible-header:hover { background: var(--row-hover); }
.collapse-arrow { font-size: 10px; color: var(--muted); transition: transform 0.15s; }
.collapse-arrow.open { transform: rotate(90deg); }
.ov-idx { font-family: var(--mono); font-size: 10px; color: var(--dim); min-width: 28px; }
.ov-pkg { font-size: 11px; font-weight: 500; color: var(--bright); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; }
.ov-dur { font-family: var(--mono); font-size: 10px; color: var(--dim); }
.ov-startup { font-family: var(--mono); font-size: 10px; color: var(--muted); }
.ov-verdict-badge { font-size: 12px; }
.ov-actions { margin-left: auto; display: flex; gap: 4px; }
.ov-actions .btn { padding: 2px 8px; font-size: 10px; }
.collapsible-body { padding: 8px 14px; border-top: 1px solid var(--border); }

/* Report */
.report-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
  flex-wrap: wrap; gap: 12px;
}
.report-stats { display: flex; gap: 20px; }
.report-stat { text-align: center; }
.report-stat .val { font-family: var(--mono); font-size: 20px; font-weight: 500; color: var(--bright); }
.report-stat .lbl { font-size: 10px; color: var(--dim); text-transform: uppercase; letter-spacing: 0.08em; }
.report-actions { display: flex; gap: 8px; flex-wrap: wrap; }

/* View tabs */
.view-tabs { display: flex; gap: 2px; }
.view-tab {
  font-family: var(--sans); font-size: 11px; font-weight: 500; padding: 5px 12px;
  border: 1px solid var(--border2); background: var(--surface2); color: var(--label);
  cursor: pointer; transition: all 0.15s; display: inline-flex; align-items: center; gap: 5px;
}
.view-tab:first-child { border-radius: 5px 0 0 5px; }
.view-tab:last-child { border-radius: 0 5px 5px 0; }
.view-tab.active { background: var(--accent-bg); color: var(--accent); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }
.view-tab kbd { font-size: 8px; }

/* Overview grid (for report too) */
.overview-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 10px;
}
`
