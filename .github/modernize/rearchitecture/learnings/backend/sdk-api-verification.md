# SDK API Verification via javap

## Context
When implementing infrastructure adapters that wrap a third-party SDK, training-data knowledge
of method names is frequently wrong. Always verify against the actual installed JAR.

## Pattern
```bash
JAVA_HOME=~/.sdkman/candidates/java/27.ea.32-open $JAVA_HOME/bin/javap \
  -classpath ~/.m2/repository/com/github/copilot-sdk-java/1.0.6/copilot-sdk-java-1.0.6.jar \
  com.github.copilot.rpc.CopilotClientOptions
```

## t11 Discoveries (SDK 1.0.6)
| Wrong assumption | Correct method |
|-----------------|----------------|
| `setCopilotClientPath(String)` | `setCliPath(String)` |
| `setSdkLogLevel(String)` | `setLogLevel(String)` |
| `setAutoStart(boolean)` | `setAutoRestart(boolean)` (for reconnect on crash) |
| `cliPathResolver.resolve()` | `cliPathResolver.resolveCliPath()` |

## Rule
**Always run `javap` on the target class before writing the first call site.** Do NOT rely on
training data or IDE autocomplete stubs. One `javap` call saves a full compile-error debug cycle.
