# Multi-Agent Code Reviewer

AI-powered parallel code review tool that orchestrates multiple specialized agents using the GitHub Copilot SDK for Java.

![alt text](image.png)

## Quick Start

```bash
mvn clean package
java --enable-preview -jar target/multi-agent-reviewer-1.0.0-SNAPSHOT.jar run --repo owner/repo --all
```

## Documentation

- English: [README_en.md](./README_en.md)
- 日本語: [README_ja.md](./README_ja.md)
- Release Notes (EN): [RELEASE_NOTES_en.md](./RELEASE_NOTES_en.md)
- リリースノート (JA): [RELEASE_NOTES_ja.md](./RELEASE_NOTES_ja.md)

### Doc Sync Note

`reviewer.copilot` settings (`cli-path`, `healthcheck-seconds`, `authcheck-seconds`, `start-timeout-seconds`) are documented in both the English and Japanese READMEs.
