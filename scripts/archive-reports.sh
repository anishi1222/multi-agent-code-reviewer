#!/usr/bin/env bash
set -euo pipefail

# Archives a report directory into a .tar.gz artifact.
# Usage:
#   scripts/archive-reports.sh <report-dir> [output-tar-gz]
#
# Example:
#   scripts/archive-reports.sh reports/owner/repo/2026-03-18-11-46-29

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <report-dir> [output-tar-gz]" >&2
  exit 64
fi

report_dir="$1"

if [[ ! -d "$report_dir" ]]; then
  echo "Report directory not found: $report_dir" >&2
  exit 66
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
default_output="${report_dir%/}-${timestamp}.tar.gz"
output_path="${2:-$default_output}"

parent_dir="$(dirname "$report_dir")"
base_name="$(basename "$report_dir")"

tar -C "$parent_dir" -czf "$output_path" -- "$base_name"
echo "Archived reports to: $output_path"
