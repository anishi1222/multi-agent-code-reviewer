# McpServerConfig Generics Invariance Fix

## Context
`McpHttpServerConfig extends McpServerConfig` (confirmed via javap on SDK 1.0.6).
However, Java generics invariance means `Map<String, McpHttpServerConfig>` is NOT
assignable to `Map<String, McpServerConfig>` even though the element type is a subtype.

## Fix Pattern
When building the map with a subtype but passing to a method requiring the supertype map:

```java
// WRONG — compile error
Map<String, McpHttpServerConfig> sdkServers = mcpSpecs.stream()
    .collect(Collectors.toMap(
        McpServerSpec::name,
        spec -> new McpHttpServerConfig()...
    ));
sessionConfig.setMcpServers(sdkServers);   // ← type mismatch

// CORRECT — declare map as supertype, cast element at construction
Map<String, McpServerConfig> sdkServers = mcpSpecs.stream()
    .collect(Collectors.toMap(
        McpServerSpec::name,
        spec -> (McpServerConfig) new McpHttpServerConfig()...
    ));
sessionConfig.setMcpServers(sdkServers);   // ← OK
```

## Files Affected in t11
- `ReviewSessionConfigFactory.java`
- `SdkRubberDuckSessionFactory.java`
