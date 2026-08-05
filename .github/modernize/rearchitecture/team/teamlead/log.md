## [t1] Establish migration constitution and layer dependency rules
- Defined 5-layer Ports & Adapters model (presentation/application/domain/infrastructure/shared) with application.port as the contract surface
- Key decision: domain purity = no Micronaut, no Jakarta, no SLF4J, no SDK — only java.* and shared
- Port naming convention: VerbNounPort; adapter naming: TechNounAdapter
- ArchUnit is the sole enforcement mechanism (no JPMS, no multi-module)
- SDK isolation to infrastructure only; framework annotations allowed in infrastructure + presentation only
- Learnings consumed: (none)
