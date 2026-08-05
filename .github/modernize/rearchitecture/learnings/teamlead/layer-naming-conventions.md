# Layer Naming Conventions

Naming conventions for the Ports & Adapters rewrite: ports are VerbNounPort, adapters are TechNounAdapter, use-cases are VerbNounUseCase.

## What Happened
During t1 constitution creation, established naming patterns to keep the codebase consistent across all implementers.

## Takeaway
All port interfaces must follow `<Verb><Noun>Port` (e.g., `LoadTemplatePort`). Adapter classes: `<Tech><Noun>Adapter`. Use-cases: `<Verb><Noun>UseCase`. Domain models are plain nouns with no prefix/suffix.

## History
- 2026-08-05 (rearchitecture/t1): initial
