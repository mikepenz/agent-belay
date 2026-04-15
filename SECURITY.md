# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Agent Approver, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please send an email to [opensource-sec@mikepenz.dev](mailto:opensource-sec@mikepenz.dev) with:

- A description of the vulnerability
- Steps to reproduce the issue
- Any potential impact assessment

## Mitigations — 2026-04-15

**Data-at-rest protection for the SQLite history database**

Sensitive history columns are now AES-GCM encrypted at the application layer
before being written to `agent-approver.db`. The encrypted columns are
`raw_request_json`, `raw_response_json`, `tool_input_json`, `feedback`,
`protection_detail`, and `risk_message`. Indexed / filterable columns
(`id`, `type`, `source`, `tool_name`, `tool_type`, `session_id`, `decision`,
`requested_at`, `decided_at`, `cwd`) remain in plaintext because they are
used in `WHERE` / `ORDER BY` / `GROUP BY` clauses.

Encryption parameters:

- AES/GCM/NoPadding, 256-bit key, 12-byte random IV per value, 128-bit auth tag
- Storage format: `v1:<base64(iv || ciphertext || tag)>` — the `v1:` prefix is
  a version tag for future rotation
- Backward compatibility: pre-encryption rows lack the `v1:` prefix and are
  returned as-is by the decrypt path; they upgrade in place on the next write

The 256-bit key lives in `<dataDir>/db.key`. On POSIX platforms it is created
with `rw-------` (0600). On Windows POSIX permissions are unsupported by the
JVM file attribute view, so the file inherits the user's default ACL — known
limitation.

**Log sanitization**

By default the application no longer writes raw commands, AI explanations, or
request/response bodies into log streams. A new `verboseLogging` setting
(default `false`) re-enables full detail when explicitly toggled in
Settings → Diagnostics. The flag is read at every sensitive call site through
`com.mikepenz.agentapprover.logging.Logging.verbose` and is updated
synchronously from `AppStateManager.updateSettings`, so no restart is required.

**Remaining gaps**

- The key file at `<dataDir>/db.key` is readable by any process running as the
  same user. This is a defense-in-depth measure to make casual disk access
  (backup tarballs, recovered drives, mistakenly synced cloud folders) yield
  opaque ciphertext — it is **not** a credential vault.
- `cwd` is intentionally left in plaintext for grouping. Treat the dataDir as
  user-confidential.
- `verboseLogging`, when enabled, restores the previous behaviour and will
  again emit raw content to whichever logger sink is configured. Turn it back
  off after diagnosing.

