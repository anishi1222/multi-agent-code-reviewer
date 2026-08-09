#!/usr/bin/env ruby
# Deterministic structural self-check for the t22.4 checkpoint producer.
# This does not replace the independent semantic validation owned by t22.5.

require "set"
require "yaml"

ROOT = File.expand_path("../../../../..", __dir__)
CHECKPOINT_DIR = File.join(ROOT, ".github/modernize/rearchitecture/artifacts/checkpoints")

class CheckRun
  attr_reader :passed, :failed

  def initialize
    @passed = 0
    @failed = []
  end

  def assert(label, condition, detail = nil)
    if condition
      @passed += 1
      puts "[PASS] #{label}"
    else
      message = detail ? "#{label}: #{detail}" : label
      @failed << message
      warn "[FAIL] #{message}"
    end
  end

  def finish(path_count)
    puts "RESULT passed=#{@passed} failed=#{@failed.length} checked_paths=#{path_count}"
    exit(@failed.empty? ? 0 : 1)
  end
end

def load_yaml(name)
  YAML.load_file(File.join(CHECKPOINT_DIR, name))
end

def ids(rows)
  rows.map { |row| row.fetch("id") }
end

def index_by_id(rows)
  rows.each_with_object({}) { |row, result| result[row.fetch("id")] = row }
end

def duplicate_values(values)
  counts = Hash.new(0)
  values.each { |value| counts[value] += 1 }
  counts.select { |_value, count| count > 1 }.keys.sort
end

PATH_FIELD_NAMES = Set.new(%w[
  artifact
  evidence
  evidence_files
  execution_ledger
  final_files
  final_runtime_evidence
  plan_sources
  requirement_sources
  source_plan
  source_tasks
]).freeze

def collect_path_values(value, active = false, result = [])
  case value
  when Hash
    value.each do |key, child|
      collect_path_values(child, active || PATH_FIELD_NAMES.include?(key), result)
    end
  when Array
    value.each { |child| collect_path_values(child, active, result) }
  when String
    result << value if active
  end
  result
end

run = CheckRun.new

spec = load_yaml("spec-to-plan.yaml")
plan = load_yaml("plan-to-tasks.yaml")
impl = load_yaml("tasks-to-impl.yaml")
baseline = load_yaml("traceability-matrix.yaml")

run.assert("all four YAML documents parsed", [spec, plan, impl, baseline].all? { |doc| doc.is_a?(Hash) })

canonical_requirement_ids = baseline.fetch("traceability").map { |row| row.fetch("requirement") }
actual_requirement_ids = ids(spec.fetch("requirements"))
run.assert(
  "canonical 84 requirement IDs match the historical denominator exactly",
  actual_requirement_ids == canonical_requirement_ids && duplicate_values(actual_requirement_ids).empty?,
  "actual=#{actual_requirement_ids.length}, canonical=#{canonical_requirement_ids.length}"
)

expected_group_counts = {
  "pm-agent" => 13,
  "pm-skill" => 8,
  "pm-instruction" => 5,
  "pm-target" => 9,
  "pm-orchestration" => 10,
  "pm-auth" => 11,
  "pm-retry" => 4,
  "pm-output" => 9,
  "architecture" => 8,
  "build" => 3,
  "supplemental" => 4
}
actual_group_counts = Hash.new(0)
spec.fetch("requirements").each { |row| actual_group_counts[row.fetch("group")] += 1 }
run.assert("requirement group cardinalities are 69 + 8 + 3 + 4", actual_group_counts == expected_group_counts)

expected_plan_ids =
  (1..6).map { |number| "P0.#{number}" } +
  %w[P1.1 P1.2 P1.3 P2.1 P2.2 P3.1 P3.2 P3.3 P4.1 P4.2 P5.1 P5.2 P6.1 P6.2 P6.3 P6.4] +
  (1..15).map { |number| "R1.#{number}" }
actual_plan_ids = ids(spec.fetch("plan_item_inventory"))
run.assert(
  "37 plan items are canonical and unique",
  actual_plan_ids == expected_plan_ids && duplicate_values(actual_plan_ids).empty?
)

unknown_requirement_plan_ids = spec.fetch("requirements").flat_map do |row|
  Array(row["base_plan_items"]) + Array(row["remediation_plan_items"])
end.to_set - actual_plan_ids.to_set
referenced_requirement_plan_ids = spec.fetch("requirements").flat_map do |row|
  Array(row["base_plan_items"]) + Array(row["remediation_plan_items"])
end.to_set
run.assert("all requirement mappings reference declared plan items", unknown_requirement_plan_ids.empty?)
run.assert("all 37 plan items are referenced by requirements", referenced_requirement_plan_ids == actual_plan_ids.to_set)
run.assert(
  "all 84 requirements are complete",
  spec.fetch("requirements").all? { |row| row["final_status"] == "complete" }
)

spec_by_id = index_by_id(spec.fetch("requirements"))
run.assert(
  "AGT-01 and SKL-01 retain DIRECT-CLOSURE",
  %w[AGT-01 SKL-01].all? do |id|
    spec_by_id.fetch(id)["evidence_grade"] == "DIRECT-CLOSURE" &&
      spec_by_id.fetch(id)["remediation_plan_items"].include?("R1.14")
  end
)

mapping_rows = plan.fetch("plan_to_task_mapping")
mapping_plan_ids = mapping_rows.map { |row| row.fetch("plan_item") }
run.assert(
  "plan-to-task mapping has one row for every plan item",
  mapping_plan_ids == expected_plan_ids && duplicate_values(mapping_plan_ids).empty?
)

expected_deep_task_ids = (1..16).map { |number| format("T%03d", number) }
deep_rows = plan.fetch("deep_plan_tasks")
actual_deep_task_ids = ids(deep_rows)
run.assert(
  "deep-plan task sequence is exactly T001-T016",
  actual_deep_task_ids == expected_deep_task_ids && duplicate_values(actual_deep_task_ids).empty?
)

expected_dependencies = {
  "T001" => [],
  "T002" => ["T001"],
  "T003" => ["T002"],
  "T004" => ["T003"],
  "T005" => ["T003"],
  "T006" => ["T003"],
  "T007" => ["T003"],
  "T008" => ["T003"],
  "T009" => %w[T005 T006 T007 T008],
  "T010" => %w[T005 T006 T007 T008],
  "T011" => %w[T009 T010],
  "T012" => ["T011"],
  "T013" => ["T012"],
  "T014" => ["T013"],
  "T015" => ["T014"],
  "T016" => ["T015"]
}
run.assert(
  "T001-T016 dependencies match the published DAG",
  deep_rows.all? { |row| row.fetch("depends_on") == expected_dependencies.fetch(row.fetch("id")) }
)

source_annotations_valid = deep_rows.all? do |row|
  number = row.fetch("id").delete_prefix("T").to_i
  if number <= 13
    row["source_annotation_applicable"] == true && row["source_annotation_present"] == true
  else
    row["source_annotation_applicable"] == false && row["source_annotation_present"] == false &&
      !row["source_annotation_reason"].to_s.empty?
  end
end
run.assert("source annotations are present for T001-T013 and explicitly N/A for T014-T016", source_annotations_valid)

board_rows = []
File.readlines(File.join(ROOT, ".github/modernize/rearchitecture/board.md"), encoding: "UTF-8").each do |line|
  match = line.match(/^- (?:✅|❌|🔄|⏳).*?\b(t\d+(?:\.\d+)?) \[([^\]]+)\]/)
  board_rows << [match[1], match[2]] if match
end
board_ids = board_rows.map(&:first)
board_unique_ids = board_ids.uniq
board_roles = board_rows.each_with_object({}) { |(id, role), result| result[id] = role }
run.assert("board contains 55 unique task IDs", board_unique_ids.length == 55)
run.assert("the sole duplicate board history row is t28", duplicate_values(board_ids) == ["t28"])

execution_rows = plan.fetch("execution_tasks")
execution_ids = ids(execution_rows)
run.assert(
  "execution ledger matches all 55 board task IDs exactly",
  execution_ids.to_set == board_unique_ids.to_set && duplicate_values(execution_ids).empty?
)
run.assert(
  "execution ledger roles match the authoritative board",
  execution_rows.all? { |row| board_roles.fetch(row.fetch("id")) == row.fetch("role") }
)

inverse_task_mapping = Hash.new { |hash, key| hash[key] = Set.new }
mapping_rows.each do |row|
  row.fetch("execution_tasks").each { |task_id| inverse_task_mapping[task_id] << row.fetch("plan_item") }
end
run.assert(
  "every execution task has an exact inverse plan mapping",
  execution_rows.all? do |row|
    inverse_task_mapping.fetch(row.fetch("id"), Set.new) == row.fetch("plan_items").to_set
  end && inverse_task_mapping.keys.to_set == execution_ids.to_set
)

mapped_deep_ids = mapping_rows.flat_map { |row| row.fetch("deep_plan_tasks") }
run.assert(
  "every deep-plan task is mapped exactly once",
  mapped_deep_ids.to_set == expected_deep_task_ids.to_set && duplicate_values(mapped_deep_ids).empty?
)

impl_deep_rows = impl.fetch("deep_plan_implementation")
impl_deep_by_id = index_by_id(impl_deep_rows)
plan_deep_by_id = index_by_id(deep_rows)
run.assert(
  "all 16 deep-plan tasks map to non-empty final-file sets",
  ids(impl_deep_rows) == expected_deep_task_ids &&
    impl_deep_rows.all? { |row| !row.fetch("final_files").empty? }
)
run.assert(
  "deep-plan implementation rows preserve plan-item contracts",
  expected_deep_task_ids.all? do |id|
    impl_row = impl_deep_by_id.fetch(id)
    plan_row = plan_deep_by_id.fetch(id)
    impl_row["plan_item"] == plan_row["plan_item"]
  end
)

impl_execution_rows = impl.fetch("execution_task_evidence")
impl_execution_by_id = index_by_id(impl_execution_rows)
plan_execution_by_id = index_by_id(execution_rows)
ledger_fields = %w[role kind status plan_items artifact]
run.assert(
  "tasks-to-implementation ledger matches all 55 planning-ledger rows",
  ids(impl_execution_rows).to_set == execution_ids.to_set &&
    execution_ids.all? do |id|
      ledger_fields.all? do |field|
        impl_execution_by_id.fetch(id)[field] == plan_execution_by_id.fetch(id)[field]
      end
    end
)

status_counts = Hash.new(0)
execution_rows.each { |row| status_counts[row.fetch("status")] += 1 }
run.assert(
  "execution status arithmetic is 53 completed + 1 failed/remediated + 1 pending",
  status_counts == {"completed" => 53, "failed-remediated" => 1, "pending" => 1}
)
run.assert(
  "only t22.5 is pending and only it lacks evidence",
  execution_rows.select { |row| row["status"] == "pending" }.map { |row| row["id"] } == ["t22.5"] &&
    impl_execution_by_id.fetch("t22.5")["artifact"].nil? &&
    impl_execution_by_id.fetch("t22.5").fetch("evidence_files").empty?
)

closure_rows = impl.fetch("remediation_closure")
run.assert(
  "C-001 through C-005 closure chains are explicit",
  closure_rows.map { |row| row.fetch("finding") } == %w[C-001 C-002 C-003 C-004 C-005] &&
    closure_rows.all? { |row| !row.fetch("closed_by").empty? && !row.fetch("evidence").empty? }
)

path_values = [spec, plan, impl].flat_map { |document| collect_path_values(document) }
missing_paths = path_values.reject do |path|
  File.exist?(File.join(ROOT, path.split("#", 2).first))
end
run.assert(
  "all 330 declared repository paths exist",
  path_values.length == 330 && missing_paths.empty?,
  "count=#{path_values.length}, missing=#{missing_paths.inspect}"
)

[spec, plan, impl].each do |document|
  producer_checks = document.fetch("producer_checks")
  issue_lists = producer_checks.select do |key, value|
    value.is_a?(Array) && key.match?(/missing|duplicate|unknown|orphan/)
  end
  run.assert(
    "#{document.fetch("metadata").fetch("schema")} producer issue lists are empty",
    producer_checks["status"] == "ready-for-independent-validation" &&
      issue_lists.values.all?(&:empty?)
  )
end

run.assert(
  "producer/auditor separation is preserved in all checkpoints",
  [spec, plan, impl].all? do |document|
    validation = document.fetch("validation")
    validation["passed"] == false &&
      validation["status"] == "pending-independent-validation" &&
      validation["validator_task"] == "t22.5" &&
      validation["validator_role"] == "architect"
  end
)

run.finish(path_values.length)
