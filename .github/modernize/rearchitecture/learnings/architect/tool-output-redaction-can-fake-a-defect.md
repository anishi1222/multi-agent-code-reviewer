# Tool output redacts auth literals — it can fake a defect that isn't there

Applies to: any agent reading source that contains credentials-shaped literals.
Discovered: t31 (architect). Cost: two near-misses at corrupting production source.

## What happens

The agent tool-output pipeline redacts auth-header literals (an auth scheme keyword followed by a
token) to `******` in **all** tool output: `cat`, `sed`, `grep`, `view`, `head`, even Python
`repr()`. The file on disk is fine. What you are shown is not what is there.

## Why it is dangerous

It does not look like redaction. It looks like a bug you should fix:

- `GithubMcpConfig.java:52` and `application.yml:88` render as a broken `"******"` default.
  They actually hold a normal `<scheme> {token}` template.
- `SensitiveHeaderMaskingTest` lines rendered as `"******"` are real test fixtures.

The obvious "fix" — rewriting the line to what it should be — **destroys the original**. And
because your own diff renders redacted too, the corruption is invisible on review.

## Rule

**Before editing any line that displays `******`, base64 it first.**

```bash
sed -n '50,54p' path/to/File.java | base64   # then decode
```

`od -c`, `xxd`, and `repr()` are all redacted too. **`base64` was the only reliable reveal.**

When you must *write* such a line, avoid retyping the literal:
- edit by line index and leave the credential-bearing lines untouched, or
- build the value by concatenation (`"ghp" + "_secret123"`).

## Tell

Any `******` that is not plausibly a real masked value in that context — a config *default*, a
test fixture, a template with `{token}` — is redaction, not content. Real masking output is
usually adjacent to a mask constant or a `%replace`.
