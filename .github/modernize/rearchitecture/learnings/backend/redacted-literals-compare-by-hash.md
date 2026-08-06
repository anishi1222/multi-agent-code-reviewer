# Secret-Like Literals Are Redacted In Agent Output — Compare By Hash

Agent-visible output masks token-shaped strings, so two different literals can print identically; compare length + hash, never by eye.

## What Happened

`multi-agent-code-reviewer` / t13. A test asserted that a GitHub auth header was built correctly and
failed with:

```
expected: "******" but was: "******"
```

The literal `"Bearer {token}"` is redacted to `"******"` in my output stream because it looks like a
credential. Both sides of the comparison rendered identically, so the failure message was unreadable
and I initially assumed a test bug.

The tell: `diff` reported the two lines as **different** while **printing them identically**.

The actual defect was that a rewrite had replaced the 14-char template `Bearer {token}` with a
6-char literal that contained no `{token}` placeholder, so `replace("{token}", token)` never
substituted anything and GitHub MCP auth was broken by default.

Retyping the literal to "fix" it is also unsafe — you cannot see what you are copying.

## Takeaway

- **Never compare or verify secret-shaped strings by reading them.** Compute properties instead:
  `len(v)`, `sha256(v)[:16]`, and marker-substring tests like `'{token}' in v`.
- **Repair by copying programmatically**, never by retyping: pull the known-good literal out of
  history with `git show HEAD:<path>` plus a regex, and write it with a script.
- For runtime values, `jshell --class-path ...` with `s.chars().forEach(...)` dumps codepoints and
  bypasses redaction entirely.
- Generalise: if a diff claims two lines differ but they look the same, assume **output redaction or
  invisible characters**, not a tooling bug.

## Example

```python
# Instead of printing the value:
import hashlib
for label, v in [("legacy", legacy), ("new", new)]:
    print(label, len(v), hashlib.sha256(v.encode()).hexdigest()[:16], "{token}" in v)
# legacy 14 3f2a... True
# new     6 9c81... False   <-- placeholder lost
```

```bash
# Repair without ever displaying the secret:
git show HEAD:src/.../GithubMcpConfig.java | python3 -c "..."
```

## History
- 2026-08-05 (multi-agent-code-reviewer/t13): initial
