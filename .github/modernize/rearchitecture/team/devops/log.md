
## [t7] Target environment prep — Java 27 + GraalVM 25 + pom-native.xml logback fix

- **Key discovery**: project-profile.yaml says "Java 26 (GraalVM 26 EA)" but pom.xml has evolved to java.version=27 during 519 prior commits. Task title "GraalVM 26 EA" is stale — actual build uses Java 27 (main) and GraalVM 25 (native).
- **GraalVM 27 EA not available in SDKMAN** — using OpenJDK 27-ea+32 for main build (works; pom.xml enforcer + compile both pass). For native-image, GraalVM 25.0.4 is used via pom-native.xml.
- **pom-native.xml logback bug**: micronaut-parent:5.0.2 resolves logback-classic:1.5.37 with transitive dep on logback-core:1.5.32. Fix: add `<logback.version>1.5.37</logback.version>` to pom-native.xml properties (mirrors what pom.xml already had). Commit: f63a79c.
- **No toolchains plugin in either pom**: toolchains.xml created at ~/.m2/ but build selects JDK via JAVA_HOME only.
- **Downstream critical**: main build (`pom.xml`) MUST be run with `JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open`. Running with active GraalVM 25 will fail (--release 27 requires JDK 27+).
- **Deprecation warning**: `CopilotService.initializeOrThrow` deprecated for removal — backend/infra-copilot (T009) should address this.
- Learnings consumed: (none — no prior devops learnings)
