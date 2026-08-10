# Packaged CLI Smoke at Verify

CLI distribution health must be tested against the shaded JAR after `package`, from outside the repository.

## What Happened

In t19, classpath tests were green while the real JAR failed at startup: root-level templates
were absent from the archive and Logback's shipped pattern failed real Joran parsing. Separate
compile/test/package CI steps also let `package -DskipTests` bypass artifact startup entirely.

## Takeaway

- Add root `templates/` as a Maven resource with target path `templates`.
- Bind a `*IT` Failsafe test to `integration-test`/`verify`; Shade must run in `package` first.
- Launch the absolute JAR from `@TempDir`, with a timeout and an isolated `PATH`, so repository
  files and an installed Copilot CLI cannot hide packaging or bootstrap defects.
- Cover `--help`, `--version`, `list`, and `doctor --help`; run environment-dependent `doctor`
  separately as manual evidence.
- `-DskipTests` native packaging is diagnostic only. A failing native test image is not a green build.

## Example

```bash
JAVA_HOME=~/.sdkman/candidates/java/28.ea.9-open ./mvnw -B clean verify
```

## History

- 2026-08-07 (anishi1222/t19): initial
