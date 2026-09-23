#!/usr/bin/env bash
# Shared helper: appends one line per agent run to .review/usage.csv
# Sourced by codex-review.sh and run-until.sh.
# Uses Node.js (already installed, because Claude Code and Codex need it). No jq needed.

USAGE_CSV=".review/usage.csv"

usage_init() {
  mkdir -p .review
  if [[ ! -f "$USAGE_CSV" ]]; then
    echo "date,task,agent,run,input_tokens,cache_read_tokens,cache_write_tokens,output_tokens,cost_usd,duration_s,status" > "$USAGE_CSV"
  fi
}

# usage_log_claude <task> <session_no> <result_json_file> <status>
# Reads the JSON printed by: claude -p ... --output-format json
usage_log_claude() {
  local task="$1" run="$2" file="$3" status="$4" line
  usage_init
  line=$(node -e '
    const fs = require("fs");
    try {
      const d = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
      const u = d.usage || {};
      console.log([u.input_tokens || 0, u.cache_read_input_tokens || 0,
        u.cache_creation_input_tokens || 0, u.output_tokens || 0,
        d.total_cost_usd ?? "", Math.floor((d.duration_ms || 0) / 1000)].join(","));
    } catch (e) { console.log("0,0,0,0,,0"); }' "$file" 2>/dev/null) || line="0,0,0,0,,0"
  echo "$(date '+%Y-%m-%d %H:%M'),$task,claude,$run,$line,$status" >> "$USAGE_CSV"
}

# usage_log_codex <task> <round> <events_jsonl_file> <duration_s> <status>
# Reads the JSONL events printed by: codex exec --json ...
# Takes the last object that contains token counts (tolerant to version differences).
usage_log_codex() {
  local task="$1" run="$2" file="$3" dur="$4" status="$5" line
  usage_init
  line=$(node -e '
    const fs = require("fs");
    let last = null;
    const walk = o => { if (o && typeof o === "object") {
      if ("input_tokens" in o && "output_tokens" in o) last = o;
      for (const v of Object.values(o)) walk(v); } };
    try {
      for (const l of fs.readFileSync(process.argv[1], "utf8").split("\n")) {
        if (!l.trim()) continue; try { walk(JSON.parse(l)); } catch (e) {} }
    } catch (e) {}
    const u = last || {};
    console.log([u.input_tokens || 0, u.cached_input_tokens || 0, 0, u.output_tokens || 0, ""].join(","));' "$file" 2>/dev/null) || line="0,0,0,0,"
  echo "$(date '+%Y-%m-%d %H:%M'),$task,codex,$run,$line,$dur,$status" >> "$USAGE_CSV"
}

# usage_result_text <result_json_file>  -> prints Claude's final message
usage_result_text() {
  node -e '
    const fs = require("fs");
    try { const d = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); console.log(d.result || "(no result text)"); }
    catch (e) { console.log(fs.readFileSync(process.argv[1], "utf8")); }' "$1" 2>/dev/null
}
