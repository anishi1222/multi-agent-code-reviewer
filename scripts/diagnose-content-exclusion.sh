#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/diagnose-content-exclusion.sh --repo <owner/repo> [options]

Purpose:
  Diagnose which exclusion pattern is causing "content exclusion / review blocked"
  results for the security review agent by running controlled probes.

Options:
  --repo <owner/repo>          Repository target for the repo probe
  --agent <name>               Agent name (default: security)
  --local-source <path>        Local source root for local probes (default: repo root)
  --output-root <path>         Output root directory
                               (default: reports/policy-probe-<timestamp>)
  --jar <path>                 Reviewer JAR path
                               (default: target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar)
  --skip-repo                  Skip the repo probe (useful when only local checks are needed)
  --top-level-scan             Run optional top-level split scan (can take longer)
  --help                       Show this help

Examples:
  scripts/diagnose-content-exclusion.sh \
    --repo anishi1222/multi-agent-code-reviewer

  scripts/diagnose-content-exclusion.sh \
    --repo anishi1222/multi-agent-code-reviewer \
    --top-level-scan
EOF
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

timestamp="$(date +%Y%m%d-%H%M%S)"
repo_target=""
agent_name="security"
local_source="$repo_root"
output_root="reports/policy-probe-$timestamp"
jar_path="target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar"
skip_repo_probe=false
run_top_level_scan=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ $# -ge 2 ]] || { echo "Missing value for --repo" >&2; exit 64; }
      repo_target="$2"
      shift 2
      ;;
    --agent)
      [[ $# -ge 2 ]] || { echo "Missing value for --agent" >&2; exit 64; }
      agent_name="$2"
      shift 2
      ;;
    --local-source)
      [[ $# -ge 2 ]] || { echo "Missing value for --local-source" >&2; exit 64; }
      local_source="$2"
      shift 2
      ;;
    --output-root)
      [[ $# -ge 2 ]] || { echo "Missing value for --output-root" >&2; exit 64; }
      output_root="$2"
      shift 2
      ;;
    --jar)
      [[ $# -ge 2 ]] || { echo "Missing value for --jar" >&2; exit 64; }
      jar_path="$2"
      shift 2
      ;;
    --skip-repo)
      skip_repo_probe=true
      shift
      ;;
    --top-level-scan)
      run_top_level_scan=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 64
      ;;
  esac
done

if [[ "$skip_repo_probe" == false && -z "$repo_target" ]]; then
  echo "--repo is required unless --skip-repo is specified." >&2
  exit 64
fi

abs_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$repo_root" "$path"
  fi
}

local_source="$(abs_path "$local_source")"
output_root="$(abs_path "$output_root")"
jar_path="$(abs_path "$jar_path")"

if [[ ! -d "$local_source" ]]; then
  echo "Local source directory not found: $local_source" >&2
  exit 66
fi

if [[ ! -f "$jar_path" ]]; then
  echo "Reviewer JAR not found: $jar_path" >&2
  echo "Build first: ./mvnw -DskipTests clean package" >&2
  exit 66
fi

mkdir -p "$output_root"
log_dir="$output_root/logs"
mkdir -p "$log_dir"
summary_tsv="$output_root/diagnosis-summary.tsv"

blocked_pattern='コンテンツ除外ポリシー|レビュー不能|content exclusion|excluded by policy|cannot review|unable to review'

repo_status="SKIPPED"
local_full_status="N/A"
local_empty_status="N/A"
local_names_status="N/A"

printf 'probe\ttarget\texit_code\tclassification\treport\tlog\n' > "$summary_tsv"

find_report_file() {
  local probe_output_dir="$1"
  find "$probe_output_dir" -type f -name "${agent_name}-report.md" | sort | tail -n 1
}

classify_report() {
  local report_file="$1"
  if grep -Eqi "$blocked_pattern" "$report_file"; then
    echo "BLOCKED"
  else
    echo "OK"
  fi
}

sanitize_probe_name() {
  echo "$1" | tr -cs '[:alnum:]_.-' '_'
}

run_probe() {
  local probe_name="$1"
  local target_mode="$2"
  local target_value="$3"

  local safe_probe
  safe_probe="$(sanitize_probe_name "$probe_name")"
  local probe_output_dir="$output_root/$safe_probe"
  local log_file="$log_dir/$safe_probe.log"
  mkdir -p "$probe_output_dir"

  local -a cmd=(
    java --enable-preview -jar "$jar_path" run
    --agents "$agent_name"
    --no-summary
    --output "$probe_output_dir"
  )

  if [[ "$target_mode" == "repo" ]]; then
    cmd+=(--repo "$target_value")
  else
    cmd+=(--local "$target_value")
  fi

  echo "[probe:$safe_probe] running..."
  local exit_code=0
  if "${cmd[@]}" >"$log_file" 2>&1; then
    exit_code=0
  else
    exit_code=$?
  fi

  local report_file=""
  report_file="$(find_report_file "$probe_output_dir" || true)"

  local classification="NO_REPORT"
  if [[ -n "$report_file" ]]; then
    classification="$(classify_report "$report_file")"
  fi

  if [[ $exit_code -ne 0 && "$classification" == "NO_REPORT" ]]; then
    classification="ERROR"
  fi

  case "$safe_probe" in
    repo-security)
      repo_status="$classification"
      ;;
    local-full-security)
      local_full_status="$classification"
      ;;
    local-empty-security)
      local_empty_status="$classification"
      ;;
    local-names-only-security)
      local_names_status="$classification"
      ;;
  esac

  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$safe_probe" "$target_mode:$target_value" "$exit_code" "$classification" "$report_file" "$log_file" \
    >> "$summary_tsv"

  echo "[probe:$safe_probe] exit=$exit_code classification=$classification"
}

build_names_only_tree() {
  local src_root="$1"
  local dst_root="$2"

  rm -rf "$dst_root"
  mkdir -p "$dst_root"

  if git -C "$src_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    while IFS= read -r -d '' rel_path; do
      mkdir -p "$dst_root/$(dirname "$rel_path")"
      : > "$dst_root/$rel_path"
    done < <(git -C "$src_root" ls-files -z)
  else
    while IFS= read -r -d '' abs_file; do
      rel_path="${abs_file#"$src_root"/}"
      mkdir -p "$dst_root/$(dirname "$rel_path")"
      : > "$dst_root/$rel_path"
    done < <(find "$src_root" -type f -print0)
  fi
}

infer_results() {
  echo
  echo "=== Inference ==="
  echo "repo-security=$repo_status"
  echo "local-full-security=$local_full_status"
  echo "local-empty-security=$local_empty_status"
  echo "local-names-only-security=$local_names_status"
  echo

  if [[ "$repo_status" == "BLOCKED" && "$local_full_status" == "OK" ]]; then
    echo "Likely repository-level exclusion (owner/repo match)."
  fi

  if [[ "$local_empty_status" == "BLOCKED" ]]; then
    echo "Likely prompt/model-level safety restriction (blocked even with empty source)."
  fi

  if [[ "$local_names_status" == "BLOCKED" && "$local_empty_status" == "OK" ]]; then
    echo "Likely path/name-based exclusion pattern."
  fi

  if [[ "$local_full_status" == "BLOCKED" && "$local_names_status" == "OK" && "$local_empty_status" == "OK" ]]; then
    echo "Likely content-based exclusion pattern in source code body."
  fi

  if [[ "$repo_status" == "OK" && "$local_full_status" == "OK" && "$local_empty_status" == "OK" && "$local_names_status" == "OK" ]]; then
    echo "No exclusion detected by these probes."
  fi
}

run_top_level_component_scan() {
  local src_root="$1"
  local scan_root="$output_root/top-level-scan-input"
  mkdir -p "$scan_root"

  echo
  echo "=== Optional top-level scan ==="
  while IFS= read -r -d '' entry; do
    local base_name
    base_name="$(basename "$entry")"
    case "$base_name" in
      .git|target|reports|logs)
        continue
        ;;
    esac

    local sandbox="$scan_root/$base_name"
    rm -rf "$sandbox"
    mkdir -p "$sandbox"
    cp -R "$entry" "$sandbox/"
    run_probe "top-${base_name}" "local" "$sandbox"
  done < <(find "$src_root" -mindepth 1 -maxdepth 1 -print0)
}

echo "Output root: $output_root"
echo "Using JAR: $jar_path"
echo "Agent: $agent_name"

if [[ "$skip_repo_probe" == false ]]; then
  run_probe "repo-security" "repo" "$repo_target"
fi

run_probe "local-full-security" "local" "$local_source"

empty_dir="$output_root/probe-empty"
mkdir -p "$empty_dir"
run_probe "local-empty-security" "local" "$empty_dir"

names_only_dir="$output_root/probe-names-only"
build_names_only_tree "$local_source" "$names_only_dir"
run_probe "local-names-only-security" "local" "$names_only_dir"

if [[ "$run_top_level_scan" == true ]]; then
  run_top_level_component_scan "$local_source"
fi

infer_results

echo
echo "Diagnosis summary: $summary_tsv"
echo "Logs directory: $log_dir"
