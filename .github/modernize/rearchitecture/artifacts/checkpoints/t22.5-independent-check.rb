#!/usr/bin/env ruby
# frozen_string_literal: true

require "pathname"
require "set"
require "yaml"

ROOT = Pathname.new(File.expand_path("../../../../..", __dir__))
CHECKPOINT_DIR = ROOT.join(".github/modernize/rearchitecture/artifacts/checkpoints")
ARTIFACT_DIR = ROOT.join(".github/modernize/rearchitecture/artifacts")

def load_yaml(path)
  YAML.safe_load(File.read(path), [], [], true)
end

def root_path(relative)
  ROOT.join(relative.split("#", 2).first)
end

def duplicate_values(values)
  counts = Hash.new(0)
  values.each { |value| counts[value] += 1 }
  counts.select { |_value, count| count > 1 }.keys
end

class Audit
  attr_reader :section_counts

  def initialize
    @passed = 0
    @failed = []
    @section_counts = Hash.new(0)
  end

  def check(section, name)
    ok = yield
    @section_counts[section] += 1
    if ok
      @passed += 1
      puts "PASS [#{section}] #{name}"
    else
      @failed << "[#{section}] #{name}"
      puts "FAIL [#{section}] #{name}"
    end
  rescue StandardError => error
    @section_counts[section] += 1
    @failed << "[#{section}] #{name}: #{error.class}: #{error.message}"
    puts "FAIL [#{section}] #{name}: #{error.class}: #{error.message}"
  end

  def finish(path_count)
    section_summary = @section_counts.map { |section, count| "#{section}=#{count}" }.join(" ")
    puts "SECTIONS #{section_summary}"
    puts "RESULT passed=#{@passed} failed=#{@failed.length} checked_paths=#{path_count}"
    @failed.each { |failure| warn failure }
    exit(@failed.empty? ? 0 : 1)
  end
end

def validation_complete?(document, expected_checks)
  validation = document.fetch("validation")
  validation["passed"] == true &&
    validation["status"] == "independently-validated" &&
    validation["validator_task"] == "t22.5" &&
    validation["validator_role"] == "architect" &&
    validation.dig("findings", "critical") == 0 &&
    validation.dig("findings", "high") == 0 &&
    validation.dig("independent_evidence", "checks_passed") == expected_checks &&
    root_path(validation.fetch("validation_report")).file?
end

def acyclic?(rows)
  dependencies = rows.to_h { |row| [row.fetch("id"), Array(row["depends_on"])] }
  visiting = Set.new
  visited = Set.new
  visit = lambda do |id|
    return true if visited.include?(id)
    return false if visiting.include?(id)

    visiting << id
    return false unless dependencies.fetch(id).all? { |dependency| visit.call(dependency) }

    visiting.delete(id)
    visited << id
    true
  end
  dependencies.keys.all? { |id| visit.call(id) }
end

def collect_path_values(value, active = false, result = [])
  path_fields = Set.new(%w[
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
    validation_report
  ])
  case value
  when Hash
    value.each do |key, child|
      collect_path_values(child, active || path_fields.include?(key), result)
    end
  when Array
    value.each { |child| collect_path_values(child, active, result) }
  when String
    result << value if active
  end
  result
end

audit = Audit.new
spec = load_yaml(CHECKPOINT_DIR.join("spec-to-plan.yaml"))
plan = load_yaml(CHECKPOINT_DIR.join("plan-to-tasks.yaml"))
impl = load_yaml(CHECKPOINT_DIR.join("tasks-to-impl.yaml"))
historical_trace = load_yaml(CHECKPOINT_DIR.join("traceability-matrix.yaml"))

# spec -> plan: derive the denominator independently from t3 plus the non-PM rows of the
# historical global traceability gate. Do not use the producer's declared denominator as input.
t3_text = File.read(ARTIFACT_DIR.join("t3-pm.md"))
pm_ids = t3_text.scan(/^\| (AGT|SKL|INS|TGT|ORC|AUTH|RTY|OUT)-(\d+) \|/)
  .map { |prefix, number| "#{prefix}-#{number}" }
trace_ids = historical_trace.fetch("traceability").map { |row| row.fetch("requirement") }
non_pm_ids = trace_ids.reject { |id| id.match?(/\A(?:AGT|SKL|INS|TGT|ORC|AUTH|RTY|OUT)-/) }
authoritative_ids = pm_ids + non_pm_ids
requirement_rows = spec.fetch("requirements")
requirement_ids = requirement_rows.map { |row| row.fetch("id") }
plan_inventory = spec.fetch("plan_item_inventory")
plan_ids = plan_inventory.map { |row| row.fetch("id") }
requirement_plan_refs = requirement_rows.flat_map do |row|
  Array(row["base_plan_items"]) + Array(row["remediation_plan_items"])
end
expected_groups = {
  "pm-agent" => 13, "pm-skill" => 8, "pm-instruction" => 5, "pm-target" => 9,
  "pm-orchestration" => 10, "pm-auth" => 11, "pm-retry" => 4, "pm-output" => 9,
  "architecture" => 8, "build" => 3, "supplemental" => 4
}
actual_groups = Hash.new(0)
requirement_rows.each { |row| actual_groups[row.fetch("group")] += 1 }
t22_3_text = File.read(ARTIFACT_DIR.join("t22.3-pm.md"))
t22_3_pass_ids = t22_3_text.scan(
  /^\| `(AGT|SKL|INS|TGT|ORC|AUTH|RTY|OUT)-(\d+)` \|.*\| PASS \|$/
).map { |prefix, number| "#{prefix}-#{number}" }

audit.check("spec-to-plan", "authoritative denominator is 84 unique IDs") do
  authoritative_ids.length == 84 && duplicate_values(authoritative_ids).empty?
end
audit.check("spec-to-plan", "checkpoint IDs exactly match the independently extracted denominator") do
  requirement_ids == authoritative_ids
end
audit.check("spec-to-plan", "requirement group cardinalities match authoritative categories") do
  actual_groups == expected_groups
end
audit.check("spec-to-plan", "37 plan items are declared exactly once") do
  plan_ids.length == 37 && duplicate_values(plan_ids).empty?
end
audit.check("spec-to-plan", "every requirement references only declared plan items") do
  (requirement_plan_refs.to_set - plan_ids.to_set).empty?
end
audit.check("spec-to-plan", "all 37 plan items have inverse requirement coverage") do
  requirement_plan_refs.to_set == plan_ids.to_set
end
audit.check("spec-to-plan", "summary arithmetic reconciles to 84 complete requirements") do
  spec.dig("summary", "total_requirements") == 84 &&
    spec.dig("summary", "mapped_requirements") == 84 &&
    spec.dig("summary", "final_requirement_state", "complete") == 84 &&
    spec.dig("summary", "final_requirement_state", "broken") == 0
end
audit.check("spec-to-plan", "all requirement final states are complete") do
  requirement_rows.all? { |row| row["final_status"] == "complete" }
end
audit.check("spec-to-plan", "all explicit per-requirement evidence paths resolve") do
  explicit_evidence = requirement_rows.map { |row| row["evidence"] }.compact
  explicit_evidence.length == 2 && explicit_evidence.all? { |path| root_path(path).exist? }
end
audit.check("spec-to-plan", "t22.3 independently re-signs the same 69 PM IDs") do
  t22_3_pass_ids == pm_ids && duplicate_values(t22_3_pass_ids).empty?
end
audit.check("spec-to-plan", "AGT-01 is corrected to DIRECT-CLOSURE") do
  requirement_rows.find { |row| row["id"] == "AGT-01" }.fetch("evidence_grade") == "DIRECT-CLOSURE" &&
    t22_3_text.include?("AGT-01 — configured-default agent discovery")
end
audit.check("spec-to-plan", "SKL-01 is corrected to DIRECT-CLOSURE") do
  requirement_rows.find { |row| row["id"] == "SKL-01" }.fetch("evidence_grade") == "DIRECT-CLOSURE" &&
    t22_3_text.include?("SKL-01 — discovered skills reach the executable catalog")
end
audit.check("spec-to-plan", "independent validation metadata is complete") do
  validation_complete?(spec, "13/13")
end

# plan -> tasks: extract T001-T016 and the execution ledger independently from their sources.
t5_text = File.read(ARTIFACT_DIR.join("t5-teamlead-tasks.md"))
t5_blocks = t5_text.split(/\n---\n/).select { |block| block.match?(/^## T\d{3}:/) }
t5_tasks = t5_blocks.map do |block|
  id = block[/^## (T\d{3}):/, 1]
  role_text = block[/^- \*\*Role\*\*: ([^\n]+)/, 1]
  dependency_text = block[/^- \*\*Depends on\*\*: ([^\n]+)/, 1]
  {
    "id" => id,
    "role" => role_text.split.first,
    "depends_on" => dependency_text == "none" ? [] : dependency_text.scan(/T\d{3}/),
    "source_annotation_present" => block.include?("- **Source**:")
  }
end
deep_rows = plan.fetch("deep_plan_tasks")
deep_by_id = deep_rows.to_h { |row| [row.fetch("id"), row] }
t5_by_id = t5_tasks.to_h { |row| [row.fetch("id"), row] }
expected_deep_ids = (1..16).map { |number| format("T%03d", number) }

board_lines = File.readlines(ROOT.join(".github/modernize/rearchitecture/board.md"))
board_entries = board_lines.map do |line|
  id = line[/^- .*?\b(t\d+(?:\.\d+)?) \[[a-z]+\]/, 1]
  role = line[/^- .*?\bt\d+(?:\.\d+)? \[([a-z]+)\]/, 1]
  id && [id, role]
end.compact
board_ids = board_entries.map(&:first)
board_unique_ids = board_ids.uniq
board_roles = {}
board_entries.each { |id, role| board_roles[id] ||= role }
execution_rows = plan.fetch("execution_tasks")
execution_ids = execution_rows.map { |row| row.fetch("id") }
declared_plan_ids = plan.fetch("plan_to_task_mapping").map { |row| row.fetch("plan_item") }
mapped_execution_ids = plan.fetch("plan_to_task_mapping")
  .flat_map { |row| Array(row["execution_tasks"]) }
  .to_set
status_counts = Hash.new(0)
execution_rows.each { |row| status_counts[row.fetch("status")] += 1 }
canonical_remediation_ids = plan.fetch("plan_to_task_mapping")
  .select { |row| row.fetch("plan_item").start_with?("R") }
  .flat_map { |row| Array(row["execution_tasks"]) }
  .uniq
auxiliary_remediation_ids = plan.fetch("remediation_and_regate_inventory")
auxiliary_missing = canonical_remediation_ids - auxiliary_remediation_ids

audit.check("plan-to-tasks", "37 plan mappings exactly cover the spec plan inventory") do
  declared_plan_ids.length == 37 && declared_plan_ids.to_set == plan_ids.to_set
end
audit.check("plan-to-tasks", "every plan mapping references declared deep and execution tasks") do
  deep_ids = deep_rows.map { |row| row.fetch("id") }.to_set
  execution_id_set = execution_ids.to_set
  plan.fetch("plan_to_task_mapping").all? do |row|
    (Array(row["deep_plan_tasks"]).to_set - deep_ids).empty? &&
      (Array(row["execution_tasks"]).to_set - execution_id_set).empty?
  end
end
audit.check("plan-to-tasks", "T001-T016 are unique, sequential, and match the source task set") do
  deep_rows.map { |row| row.fetch("id") } == expected_deep_ids &&
    t5_tasks.map { |row| row.fetch("id") } == expected_deep_ids
end
audit.check("plan-to-tasks", "T001-T016 roles match the source task definitions") do
  expected_deep_ids.all? { |id| deep_by_id[id].fetch("role") == t5_by_id[id].fetch("role") }
end
audit.check("plan-to-tasks", "T001-T016 dependency lists match the published source DAG") do
  expected_deep_ids.all? { |id| deep_by_id[id].fetch("depends_on") == t5_by_id[id].fetch("depends_on") }
end
audit.check("plan-to-tasks", "the published deep-plan dependency graph is acyclic") do
  acyclic?(deep_rows)
end
audit.check("plan-to-tasks", "T001-T013 carry applicable source annotations") do
  expected_deep_ids.first(13).all? do |id|
    t5_by_id[id]["source_annotation_present"] &&
      deep_by_id[id]["source_annotation_applicable"] == true &&
      deep_by_id[id]["source_annotation_present"] == true
  end
end
audit.check("plan-to-tasks", "T014-T016 are explicitly non-conversion tasks") do
  expected_deep_ids.last(3).all? do |id|
    !t5_by_id[id]["source_annotation_present"] &&
      deep_by_id[id]["source_annotation_applicable"] == false &&
      deep_by_id[id]["source_annotation_present"] == false &&
      !deep_by_id[id]["source_annotation_reason"].to_s.empty?
  end
end
audit.check("plan-to-tasks", "source-reference summary reconciles 13 applicable and 3 not-applicable tasks") do
  plan.dig("source_reference_checks", "applicable_tasks") == 13 &&
    plan.dig("source_reference_checks", "present") == 13 &&
    plan.dig("source_reference_checks", "missing") == 0 &&
    plan.dig("source_reference_checks", "not_applicable_tasks") == expected_deep_ids.last(3)
end
audit.check("plan-to-tasks", "board extraction yields 55 unique tasks with only historical t28 repeated") do
  board_unique_ids.length == 55 && duplicate_values(board_ids) == ["t28"]
end
audit.check("plan-to-tasks", "execution ledger exactly matches the 55 board task IDs") do
  execution_ids.length == 55 && duplicate_values(execution_ids).empty? &&
    execution_ids.to_set == board_unique_ids.to_set
end
audit.check("plan-to-tasks", "execution roles match the board") do
  execution_rows.all? { |row| row.fetch("role").split.first == board_roles.fetch(row.fetch("id")) }
end
audit.check("plan-to-tasks", "execution rows reference only declared plan items") do
  execution_rows.all? do |row|
    (Array(row["plan_items"]).to_set - declared_plan_ids.to_set).empty?
  end
end
audit.check("plan-to-tasks", "all 55 execution tasks have inverse plan coverage") do
  mapped_execution_ids == execution_ids.to_set
end
audit.check("plan-to-tasks", "producer snapshot status and 54 nonpending artifacts reconcile") do
  status_counts == { "completed" => 53, "failed-remediated" => 1, "pending" => 1 } &&
    execution_rows.reject { |row| row["status"] == "pending" }.all? do |row|
      row["artifact"] && root_path(row["artifact"]).file?
    end &&
    execution_rows.select { |row| row["status"] == "pending" }.map { |row| row["id"] } == ["t22.5"]
end
audit.check("plan-to-tasks", "all 42 canonically mapped remediation tasks are represented") do
  canonical_remediation_ids.length == 42 &&
    auxiliary_missing.sort == %w[t13 t16 t21 t22 t23] &&
    auxiliary_missing.all? { |id| execution_ids.include?(id) } &&
    plan.dig("validation", "advisory", "severity") == "LOW"
end
audit.check("plan-to-tasks", "independent validation metadata is complete") do
  validation_complete?(plan, "17/17")
end

# tasks -> implementation: verify current files and the execution evidence ledger, then inspect
# the exact source and runtime tests that close C-004/C-005.
implementation_rows = impl.fetch("deep_plan_implementation")
implementation_ids = implementation_rows.map { |row| row.fetch("id") }
final_files = implementation_rows.flat_map { |row| row.fetch("final_files") }
java_final_files = final_files.select { |path| path.end_with?(".java") }
impl_execution_rows = impl.fetch("execution_task_evidence")
impl_execution_ids = impl_execution_rows.map { |row| row.fetch("id") }
impl_by_id = impl_execution_rows.to_h { |row| [row.fetch("id"), row] }
plan_by_execution_id = execution_rows.to_h { |row| [row.fetch("id"), row] }
closure_rows = impl.fetch("remediation_closure")

load_agent_text = File.read(ROOT.join("src/main/java/dev/logicojp/reviewer/application/agent/LoadAgentUseCase.java"))
loader_adapter_text = File.read(
  ROOT.join("src/main/java/dev/logicojp/reviewer/infrastructure/parsing/AgentDefinitionLoaderAdapter.java")
)
skill_registry_text = File.read(
  ROOT.join("src/main/java/dev/logicojp/reviewer/infrastructure/parsing/SkillRegistry.java")
)
execute_skill_text = File.read(
  ROOT.join("src/main/java/dev/logicojp/reviewer/application/skill/ExecuteSkillUseCase.java")
)
packaged_smoke_text = File.read(ROOT.join("src/test/java/dev/logicojp/reviewer/PackagedCliSmokeIT.java"))

all_path_values = [spec, plan, impl].flat_map { |document| collect_path_values(document) }
missing_paths = all_path_values.reject { |path| root_path(path).exist? }

audit.check("tasks-to-impl", "T001-T016 implementation rows are unique and sequential") do
  implementation_ids == expected_deep_ids && duplicate_values(implementation_ids).empty?
end
audit.check("tasks-to-impl", "implementation plan-item mappings match plan-to-tasks") do
  implementation_rows.all? do |row|
    row.fetch("plan_item") == deep_by_id.fetch(row.fetch("id")).fetch("plan_item")
  end
end
audit.check("tasks-to-impl", "45 final-file declarations are present with one cross-task reuse") do
  final_files.length == 45 &&
    final_files.uniq.length == 44 &&
    duplicate_values(final_files) == ["src/main/java/dev/logicojp/reviewer/ReviewApp.java"]
end
audit.check("tasks-to-impl", "all 45 declared final files exist") do
  final_files.all? { |path| root_path(path).file? }
end
audit.check("tasks-to-impl", "all 41 Java final files declare their named primary type") do
  java_final_files.all? do |path|
    type_name = File.basename(path, ".java")
    File.read(root_path(path)).match?(/\b(?:class|interface|record|enum)\s+#{Regexp.escape(type_name)}\b/)
  end
end
audit.check("tasks-to-impl", "all deep-plan implementation task references are known") do
  implementation_rows.all? do |row|
    (Array(row["implementation_tasks"]).to_set - execution_ids.to_set).empty?
  end
end
audit.check("tasks-to-impl", "implementation ledger contains 55 unique task rows") do
  impl_execution_ids.length == 55 && duplicate_values(impl_execution_ids).empty?
end
audit.check("tasks-to-impl", "planning and implementation ledgers have exact task-ID parity") do
  impl_execution_ids.to_set == execution_ids.to_set
end
audit.check("tasks-to-impl", "roles, statuses, and plan-item links match the planning ledger") do
  impl_execution_rows.all? do |row|
    planned = plan_by_execution_id.fetch(row.fetch("id"))
    row.fetch("role") == planned.fetch("role") &&
      row.fetch("status") == planned.fetch("status") &&
      row.fetch("plan_items") == planned.fetch("plan_items")
  end
end
audit.check("tasks-to-impl", "54 nonpending evidence sets resolve and t22.5 alone is pending") do
  nonpending = impl_execution_rows.reject { |row| row["status"] == "pending" }
  pending = impl_execution_rows.select { |row| row["status"] == "pending" }
  nonpending.length == 54 &&
    nonpending.all? do |row|
      row["artifact"] && root_path(row["artifact"]).file? &&
        !row.fetch("evidence_files").empty? &&
        row.fetch("evidence_files").all? { |path| root_path(path).exist? }
    end &&
    pending.map { |row| row["id"] } == ["t22.5"] &&
    pending.first.fetch("evidence_files").empty?
end
audit.check("tasks-to-impl", "every declared checkpoint repository path resolves") do
  all_path_values.length >= 330 && missing_paths.empty?
end
audit.check("tasks-to-impl", "C-001 through C-005 closure rows are complete") do
  closure_rows.map { |row| row.fetch("finding") } == %w[C-001 C-002 C-003 C-004 C-005] &&
    closure_rows.all? do |row|
      !row.fetch("closed_by").empty? && root_path(row.fetch("evidence")).exist?
    end
end
audit.check("tasks-to-impl", "C-004 source chain preserves configured-default agent discovery") do
  load_agent_text.include?("return agentLoader.load(normalizeAdditionalDirectories(directories));") &&
    loader_adapter_text.include?("new ArrayList<>(configuredDirectories)") &&
    loader_adapter_text.include?("merged.addAll(additionalDirectories)") &&
    packaged_smoke_text.include?("discoversAgentsFromConfiguredDefaultDirectory") &&
    packaged_smoke_text.include?('doesNotContain("No agents found.")')
end
audit.check("tasks-to-impl", "C-005 source chain publishes and queries one canonical skill catalog") do
  loader_adapter_text.include?("skillCatalog.replaceAll(report.discoveredSkills())") &&
    skill_registry_text.include?("implements ManageSkillCatalogPort") &&
    skill_registry_text.include?("new AtomicReference<>(Map.of())") &&
    execute_skill_text.include?("skillCatalog.findById(skillId)") &&
    execute_skill_text.include?("return skillCatalog.listAll();")
end
audit.check("tasks-to-impl", "packaged runtime tests discriminate populated AGT-01 and SKL-01 flows") do
  packaged_smoke_text.include?('contains("Available agents:", "packaged-default-agent")') &&
    packaged_smoke_text.include?('contains("Available Skills:", "packaged-catalog-skill")') &&
    packaged_smoke_text.include?('doesNotContain("No skills found.")') &&
    t22_3_pass_ids.include?("AGT-01") &&
    t22_3_pass_ids.include?("SKL-01")
end
audit.check("tasks-to-impl", "independent validation metadata is complete") do
  validation_complete?(impl, "16/16")
end

audit.finish(all_path_values.length)
