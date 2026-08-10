
## [t7] Target environment prep — Java 27 + GraalVM 25 + pom-native.xml logback fix

- **Key discovery**: project-profile.yaml says "Java 26 (GraalVM 26 EA)" but pom.xml has evolved to java.version=27 during 519 prior commits. Task title "GraalVM 26 EA" is stale — actual build uses Java 27 (main) and GraalVM 25 (native).
- **GraalVM 27 EA not available in SDKMAN** — using OpenJDK 27-ea+32 for main build (works; pom.xml enforcer + compile both pass). For native-image, GraalVM 25.0.4 is used via pom-native.xml.
- **pom-native.xml logback bug**: micronaut-parent:5.0.2 resolves logback-classic:1.5.37 with transitive dep on logback-core:1.5.32. Fix: add `<logback.version>1.5.37</logback.version>` to pom-native.xml properties (mirrors what pom.xml already had). Commit: f63a79c.
- **No toolchains plugin in either pom**: toolchains.xml created at ~/.m2/ but build selects JDK via JAVA_HOME only.
- **Downstream critical**: main build (`pom.xml`) MUST be run with `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open`. Running with active GraalVM 25 will fail (--release 27 requires JDK 27+).
- **Deprecation warning**: `CopilotService.initializeOrThrow` deprecated for removal — backend/infra-copilot (T009) should address this.
- Learnings consumed: (none — no prior devops learnings)

## [t19] Packaged CLI smoke is green; native full verify blocked on reflection metadata

- The current shaded JAR already had a valid `Main-Class`; t14's missing-manifest finding was stale
  after a later owner commit. Real isolated-CWD startup instead found two live defects: root
  `templates/` were not packaged, and Logback/Joran rejected raw converter delimiters inside the
  substituted header-masking regex.
- Packaging `templates/` and encoding regex delimiters (`\x2C`, `\x7D`, `\x5D`) made all JVM
  entry points start. Failsafe now owns four shaded-JAR probes during `verify`, with external CLI
  paths removed so local prerequisites cannot hide defects.
- `pom-native.xml`'s old annotation-processor failure was also stale. Aligning it to
  `micronaut-parent:5.1.0` and removing the explicit processor path allowed compile and native
  executable generation on GraalVM 25.0.4.
- The unmodified native `clean verify` exposed a different blocker: its 1,058-test native image has
  2 failures and 3 errors from missing record/accessor reflection metadata. A diagnostic
  `-DskipTests` build produced a native executable whose five safe entry points all exited 0, but
  that is not accepted as a passing build. Metadata remains with t33.
- Corrected two misleading assumptions: the command is `list`, not `list-agents`, and `.sdkmanrc`
  must select Java 28 for the default POM rather than a stale GraalVM 25 patch.
- Main verification: 1,058 Surefire + 4 packaged-JAR Failsafe tests passed; native required gate is
  blocked until t33 and must be rerun without skip flags.
- Learnings consumed: [devops/dual-jdk-build-activation, devops/logback-version-bom-override]

## [t33] Layered native reflection metadata repaired; release-note key corrected

- Replaced 18 deleted `cli` bean-definition registrations in each metadata copy with the exact
  generated layered `presentation` definitions and kept both JSON files identical.
- Registered only the record accessors and canonical constructor required by reflective tests;
  deliberately avoided blanket `allDeclaredMethods` metadata.
- The upstream five-failure list was not exhaustive: after those entries were fixed, the first
  exact rerun exposed `InstructionFrontmatter$Parsed.body()` as one additional hidden accessor.
- Final exact GraalVM 25 `clean verify -Pnative -f pom-native.xml`: 1,058 JVM + 1,058 native
  + 4 packaged-JAR tests passed, with zero failures/errors/skips.
- EN/JA v0.03 notes now explicitly distinguish the live
  `reviewer.execution.concurrency.review-passes` key from the former banner-only key.
- Learnings consumed: [devops/dual-jdk-build-activation,
  devops/packaged-cli-smoke-at-verify,
  backend/record-component-names-break-descriptor-regex,
  backend/route-config-to-presentation-through-an-inbound-port]

## [t19] Independent packaged JVM and native CLI re-pass after t33

- Re-ran the Java 28 root gate from clean state: 1,058 Surefire + 4 packaged-JAR Failsafe tests,
  all green. Five isolated-CWD shaded-JAR entry points also exited 0 with no fallback.
- Re-ran the exact unskipped GraalVM 25.0.4 gate: 1,058 JVM + 1,058 native-image + 4 packaged-JAR
  tests, all green. The generated arm64 `target/review` passed five isolated-CWD CLI probes.
- Independently rechecked t33 rather than inheriting its report: metadata copies are byte-identical,
  contain zero stale `cli` references, and expose the expected exact record/accessor members.
- The local Python launcher is `python3`, not `python`; the first ad-hoc probe also had a harness-only
  f-string quoting error. Both were corrected before any artifact verdict was recorded.
- No product/configuration change was needed in this re-pass; only t19 evidence and this log changed.
- Learnings consumed: [devops/dual-jdk-build-activation, devops/exact-member-native-reflection,
  devops/logback-replace-option-delimiters, devops/logback-version-bom-override,
  devops/packaged-cli-smoke-at-verify, tester/concurrent-agents-shared-worktree-build-races,
  tester/never-pipe-a-verification-build, backend/surefire-declared-vs-actual-test-counts]
