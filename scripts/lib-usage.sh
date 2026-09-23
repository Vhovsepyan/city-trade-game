#!/usr/bin/env bash
# Shared helper: appends one line per agent run to .review/usage.csv
# Sourced by codex-review.sh and run-until.sh. Needs: jq

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
  local task="$1" run="$2" file="$3" status="$4"
  usage_init
  command -v jq >/dev/null 2>&1 || { echo "jq not found - usage not logged"; return 0; }
  local line
  line=$(jq -r '[
      (.usage.input_tokens // 0),
      (.usage.cache_read_input_tokens // 0),
      (.usage.cache_creation_input_tokens // 0),
      (.usage.output_tokens // 0),
      (.total_cost_usd // ""),
      (((.duration_ms // 0) / 1000) | floor)
    ] | @csv' "$file" 2>/dev/null) || line='0,0,0,0,"",0'
  echo "$(date '+%Y-%m-%d %H:%M'),$task,claude,$run,$line,$status" >> "$USAGE_CSV"
}

# usage_log_codex <task> <round> <events_jsonl_file> <duration_s> <status>
# Reads the JSONL events printed by: codex exec --json ...
# Takes the last event object that contains token counts (field names differ
# between Codex versions, so the search is tolerant).
usage_log_codex() {
  local task="$1" run="$2" file="$3" dur="$4" status="$5"
  usage_init
  command -v jq >/dev/null 2>&1 || { echo "jq not found - usage not logged"; return 0; }
  local line
  line=$(jq -rs '
      ([ .[] | .. | objects | select(has("input_tokens") and has("output_tokens")) ] | last) // {}
      | [ (.input_tokens // 0), (.cached_input_tokens // 0), 0, (.output_tokens // 0), "" ]
      | @csv' "$file" 2>/dev/null) || line='0,0,0,0,""'
  echo "$(date '+%Y-%m-%d %H:%M'),$task,codex,$run,$line,$dur,$status" >> "$USAGE_CSV"
}
