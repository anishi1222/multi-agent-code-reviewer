# ADR-0004: Release Channel Strategy

| Field     | Value                |
|-----------|----------------------|
| Status    | Accepted             |
| Date      | 2026-04-14           |
| Deciders  | Project maintainers  |

## Context

The project lacked a formal release process. Releases were created manually on GitHub with no automated artifact publication, checksum generation, or pre-release staging. This introduced risks:

- No reproducible way to verify artifact integrity (no checksums or SBOM).
- No distinction between pre-release and stable releases.
- No documented rollback procedure.
- Release artifacts were not consistently attached to GitHub Releases.

WAF Operational Excellence guidance recommends automated, repeatable deployment pipelines with artifact verification and safe deployment patterns (blue/green, canary, or staged rollout).

## Decision

We adopt a **two-channel release model** with automated artifact publication:

### Release Channels

| Channel      | Tag Pattern                        | GitHub Release Type |
|--------------|------------------------------------|---------------------|
| Pre-release  | `v*-rc*`, `v*-alpha*`, `v*-beta*`  | Pre-release         |
| Stable       | `v*` (without rc/alpha/beta)       | Release             |

### Automated Pipeline

A new `release.yml` workflow triggers on tag push (`v*`) and:

1. Builds the JVM artifact with full test suite.
2. Builds the native image (best-effort, non-blocking).
3. Generates an SBOM in CycloneDX JSON format.
4. Computes SHA-256 checksums for all artifacts.
5. Creates a GitHub Release with all artifacts attached.
6. Marks the release as pre-release or stable based on the tag pattern.

### Rollback

- Download a previous release's artifact from GitHub Releases.
- Verify integrity via `sha256sum -c SHA256SUMS.txt`.
- Deploy the verified artifact.
- For source rollback: `git revert` or tag a new release from a known-good commit.

### SBOM

Every release includes a CycloneDX SBOM (`sbom.json`) for supply-chain transparency. The CycloneDX Maven plugin is added to `pom.xml` and invoked in both CI and release workflows.

## Alternatives Considered

1. **Single release channel only**: Simpler but no staging area for validation before promoting to stable.
2. **Container registry-based releases**: Premature — the project is a CLI tool, not a service. Docker image is available but secondary.
3. **GitHub Packages (Maven)**: Considered for library-style distribution, but the project is an end-user CLI tool distributed as a fat JAR and native binary.

## Consequences

### Positive

- Every release has verifiable checksums and SBOM.
- Pre-release channel allows testing before promoting to stable.
- Automated pipeline reduces manual error and ensures consistency.
- Rollback procedure is documented and artifact-verified.

### Negative

- Additional CI compute cost for release builds (acceptable — releases are infrequent).
- Native image build may fail for some releases (mitigated by `continue-on-error`).
- Tag naming discipline required to distinguish pre-release from stable.

### Risks

- The `release.yml` workflow requires `contents: write` permission. This is scoped to the workflow and uses `GITHUB_TOKEN` (not a PAT).
- SHA-256 checksums provide integrity verification but not authenticity (no GPG signing). GPG signing can be added later if needed.

## References

- [Azure WAF: Operational Excellence — Safe deployment practices](https://learn.microsoft.com/en-us/azure/well-architected/operational-excellence/safe-deployments)
- [OSSF Scorecard: Signed-Releases check](https://github.com/ossf/scorecard/blob/main/docs/checks.md#signed-releases)
- [CycloneDX SBOM specification](https://cyclonedx.org/specification/overview/)
