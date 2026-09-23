#!/usr/bin/env bash
# Prints token usage totals from .review/usage.csv
# Usage: scripts/usage-report.sh            (all tasks)
#        scripts/usage-report.sh T05        (one task)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
CSV=".review/usage.csv"
[[ -f "$CSV" ]] || { echo "No usage data yet ($CSV)"; exit 0; }
FILTER="${1:-}"

awk -F',' -v f="$FILTER" '
  NR == 1 { next }
  { gsub(/"/, "") }
  f != "" && $2 != f { next }
  {
    key = $2 " " $3
    runs[key]++; inp[key] += $5; cr[key] += $6; cw[key] += $7; out[key] += $8; cost[key] += $9; dur[key] += $10
    tin[$3] += $5; tcr[$3] += $6; tcw[$3] += $7; tout[$3] += $8; tcost[$3] += $9
    if (!(key in seen)) { seen[key] = 1; order[++n] = key }
  }
  END {
    printf "%-6s %-7s %5s %12s %12s %12s %10s %9s %8s\n", "TASK", "AGENT", "RUNS", "INPUT", "CACHE_READ", "CACHE_WRITE", "OUTPUT", "COST_USD", "MIN"
    for (i = 1; i <= n; i++) {
      k = order[i]; split(k, p, " ")
      c = (p[2] == "codex") ? "-" : sprintf("%.2f", cost[k])
      printf "%-6s %-7s %5d %12d %12d %12d %10d %9s %8.1f\n", p[1], p[2], runs[k], inp[k], cr[k], cw[k], out[k], c, dur[k] / 60
    }
    print ""
    for (a in tin) {
      c = (a == "codex") ? "n/a (Codex reports no cost)" : sprintf("$%.2f", tcost[a])
      printf "TOTAL %-7s input %d, cache read %d, cache write %d, output %d, cost %s\n", a, tin[a], tcr[a], tcw[a], tout[a], c
    }
    print "Note: Claude input = new tokens only; Codex input already includes its cached tokens."
  }' "$CSV"
