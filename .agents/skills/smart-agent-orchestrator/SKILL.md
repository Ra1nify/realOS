---
name: smart-agent-orchestrator
description: Decide whether to delegate to subagents, then dispatch, verify, and escalate. Load before any non-trivial task - debugging, "where is X handled", refactors, architecture, multi-file changes, test generation, log triage - or any mention of subagents, parallel work, or model choice.
compatibility: opencode
---

# Smart Agent Orchestration

Default answer: **do not delegate.** Delegate only when Section 1 says so.

Model IDs, context sizes, prices, and quotas are never remembered facts. Resolve them at session start or before the first delegation (Section 2).

## 1. Decide

Delegate only if at least one is true:

- Read-only search or tracing spans 3+ files, or the location is unknown.
- Edits are needed in decoupled modules with non-overlapping paths.
- Several independent logs or failures need triage.
- Test generation can be parallelized.

Never delegate:

- Single-file or small local edits.
- Tightly coupled or stateful refactors.
- Core design that needs global consistency.
- Anything that touches git writes, builds, formatters, or tests (the parent owns these, see Section 4).

If nothing in the first list applies, stop here and do the work directly.

The capability ledger (Section 3) shifts borderline cases only:

- Positive bias: delegate 2-file read-only work or one-module scoped edits.
- Negative bias: require 5+ files or two independent modules.

Hard "never delegate" rules stay unchanged.

Fan-out costs quota: every child pays for its own context. Prefer one child with a broader scope over several tiny ones. Default cap: 3 concurrent children. Go lower if plan usage is visible and tight (unverified; check what your harness exposes).

## 2. Resolve before dispatching

- Roles: inspect the active delegation tool schema (`task` or `subagent`) for the agent/type parameter (`subagent_type` in V1, `agent` in V2) and the `model` field. Use only roles that exist in this session.
- Models: use the `models` tool (or ask the user to run `/models`, which is a user-side command) for availability and variants. Use provider metadata or models.dev for `limit.context` and `limit.output`. Never write a model ID from memory.
- Explicit model: pass the resolved ID as `model` on every `subagent` call. Omitting `model` silently runs the child on the parent session model. Prefer the cheapest model that covers the role's context and capability; use the main model or `deep` only when the cost of failure is high.
- Resolve baselines at session start or before the first delegation. Cache the result in a scratch note kept outside the repo (a temp file or session note), not only in your head. Re-resolve after compaction, a model switch, a new day, or any not-found, auth, or region error.
- If resolution fails, assume 200K, say so, and constrain children by file count or line ranges instead.
- Availability depends on account, region, and workspace privacy settings. Free models vary widely in context size.

Channel health:

- Model not found, auth failure, or region blocked: re-resolve once. If it still fails, drop that channel for the session.
- 429, timeout, or one formatting error: cool down (do other work first, retry no sooner than the next turn), retry once, then escalate (Section 6).
- A custom or unverified role gets one tiny read-only probe first (for example, read lines 1-5 of a known file). Do not probe again once verified this session.

## 3. Roles and routing

Roles describe responsibility, not plan tier. Bindings live in configuration. Use one naming set only: if the config still has older names, rename them in config or update this table (see `opencode-config-maintainer`).

| Role | Use | Constraint |
| :--- | :--- | :--- |
| `explore` | Read-only search, file location, call-chain tracing | No writes |
| `general` | Multi-step analysis, independent module work | Cannot launch subagents |
| `reader` (optional) | Cheap read-only analysis, review; vision-capable if bound to such a model | Must be configured; read-only via permissions |
| `deep` (optional) | Large context, hard concurrency or low-level debugging | Must be configured |

Route by cost of failure = reversibility x blast radius:

- Low (read-only, easy to redo): `explore`, `reader`, or a fast free model.
- Medium (scoped edits in one module): `general`.
- High (shared state, hard to undo, deep design): main model or `deep`.
- Vision tasks need a model that accepts image input.
- Do not route on price alone. A failed cheap attempt plus a redo can cost more than going straight to `deep`.

Context budget: a child's input should stay within 70% of its own `limit.context`, minus `limit.output` and the system prompt. If you cannot estimate tokens, bound the scope by files and line ranges.

### Capability ledger

Append one JSONL record per verified child run to `<temp>/realos-agent-ledger.jsonl`:
`role`, `model`, `task_class`, `source`, `weight`, `scale_files`, `scale_lines`, `steps_used`,
`started_at`, `ended_at`, `wall_ms`, `status`, `rework_files`, `rework_lines`,
`out_of_scope_files`, `context_overflow`, `verification_failures`, `cost_usd`
(null when unreported), `suite_version` (null for real rows; integer for calibration rows).
`source` is `calibration` or `real`; `weight` is 0.5 for calibration, 1.0 for real.
No prompts, code, logs, or secrets. The parent writes the record after Section 6; child self-reports are input only.

Per role+model, keep the last 20 runs or 14 days. Compute weighted 0..1 factors:

- `quality` = 1 - sum(weight * rework_lines) / max(1, sum(weight * scale_lines))
- `reliability` = sum(weight * ok) / sum(weight)
- `scope` = 1 - sum(weight * out_of_scope_files) / max(1, sum(weight * scale_files))
- `speed` = clamp(observed_lines_per_min / max(1, role_median), 0, 1); real rows only
- `capacity` = 1 - sum(weight * context_overflow) / sum(weight)

`capability = 0.35*quality + 0.25*reliability + 0.2*scope + 0.1*speed + 0.1*capacity`
`bias = clamp((capability - 0.7) * 4, -2, +2)`

Skip factors with no data and renormalize the remaining weights.

### Calibration

Run the fixed micro-suite in `calibration/README.md` once per role+model before the first real dispatch.
Copy `calibration/sandbox/` to a fresh temp directory for each run; never run it in the repo.

- Five tasks: locate, scope, format, fail-closed, comment.
- Tier 1 filters gross failures only. An all-pass batch does not rank models; run tier 2 (`calibration/tier2/README.md`) or use real-task results before ordering them.
- Batch: one temp sandbox per model; up to 5 concurrent calibration children. Real work keeps the 3-child cap.
- A free channel that returns a quota, auth, or region error is skipped for the session. Free quota is often shared across models from one provider.
- Parent verifies each task; child self-reports do not score.
- Write five rows with `source=calibration`, `weight=0.5`, `wall_ms=null`, `suite_version=1`.
- Calibration alone caps `bias` at +/-1.
- Two verified real runs unlock the full +/-2 range.
- Skip calibration when a matching entry is younger than 14 days and the model version/provider is unchanged.
- Bump `suite_version` when fixtures or prompts change; ignore rows from older versions.

Rules:

- Apply bias only after 3 verified runs. Before that, use the base rules.
- Key on role+model; split by `task_class` once that class has 5+ runs.
- Bias shifts Section 1 borderline cases; it never overrides hard no-delegate rules, permissions, or fail-closed behavior.
- `rework_lines / scale_lines > 0.2`, or two consecutive failed/partial runs: force `bias = -2` until a clean run.
- After two consecutive failed/partial runs in one task class, the parent does the next attempt directly.
- Reset the role+model entry after a model version or provider change.
- If the harness exposes no timestamps, record `wall_ms = null` and skip `speed`.
- Model choice: among candidates that meet context and capability, take the highest `capability`; prefer the cheaper model when scores differ by 0.1 or less.
- On a tie, prefer an available channel, then the model with the fewest real-task samples.

## 4. Ownership and isolation

- The parent exclusively owns git writes, builds, formatters, and tests. Children may use read-only git (`status`, `diff`, `log`, `blame`) when their shell permission allows it.
- Read-only children may run concurrently.
- Write children get disjoint path allowlists, enforced with path-restricted `edit` permissions on the agent. Edit rules do not cover shell, so a write role must also have shell restricted, or it can bypass the allowlist.
- Always verify after the fact too:
  1. Before dispatching writers, record `git status --porcelain`.
  2. After each child returns, compare. For anything outside the allowlist, inspect the diff, restore only the child's out-of-scope changes without discarding pre-existing user changes, and do not merge that branch.
- Use worktrees for large parallel edits. Never run two builds in one workspace.
- Children must not read `.env`, key files, tokens, or credentials. Say so in every prompt.
- You cannot assume a running child can be cancelled. If one must be abandoned, discard its output and inspect the diff.

## 5. Dispatch template

Every prompt uses this shape. Fill every field; write `NONE` rather than omitting one.

```
GOAL: <one sentence>
DONE WHEN: <verifiable acceptance criteria>
SCOPE (read): <path globs>
SCOPE (write): <path globs | NONE>
NON-GOALS: <what must not change>
FORBIDDEN: git writes, build, test, format; .env, secrets, credentials
LIMITS: steps <= N; report <= 2K tokens
REPORT FORMAT:
  status: ok | partial | failed
  steps_used: <n>   errors: <n>   files_touched: <list>
  findings: <repo-relative path>:<start>-<end> -> <conclusion>
  unverified: <items and remaining risks>
```

Set `steps` in the agent configuration where possible so the limit is enforced, not requested.

## 6. Accept, verify, escalate

Report rules: repo-relative paths only, no more than 50 lines of unmodified source, no full logs or JSON dumps, no secrets.

After a child returns:

1. Read the status header. `partial` or `failed`, a hit step cap, repeated tool-argument errors, or context overflow all count as failure.
2. Check the diff against the allowlist and run static checks yourself.
3. Before acting on any key claim, open its cited `path:lines` and confirm. This is cheap and catches most bad summaries.
4. If two children conflict, read the raw evidence and decide yourself. Do not dispatch a third child to arbitrate.
5. Append the verified result to the capability ledger (Section 3).

Escalation happens once:

- Redo the work with the main model or `deep`. Treat the failed output as unverified leads only, never as facts.
- If the redo also fails, stop and report to the user what is known, what is not, and what you tried. Do not keep patching the same branch.

## 7. Session hygiene

- Consider compaction only above roughly 70% of the context window. Automatic compaction is on by default in V2; manual compaction is user-triggered.
- Tell the user when compaction is needed. Do not assume a particular command exists, and do not run a compaction command via shell mid-turn on an active session.
- Suggest a fresh session for an unrelated task.
- Compaction can drop scratch state and any "healthy/fused" channel notes. Re-verify afterward.

## 8. Known gotchas (reviewed 2026-11-18; verify before relying)

- Some free-tier models need a Global region or relaxed privacy setting in the workspace (seen with LongCat 2.5 Free).
- The free set is not just models named `*-free`; other models have been offered free at times.

## 9. Anti-patterns

- Parallelizing for its own sake while builds, git, or tests share state.
- Retrying a cheap model repeatedly on a high-failure-cost task.
- Omitting `model` and letting a child inherit the parent model.
- Passing the main model to low-risk, mechanical work.
- Scoring a role or model from one run, or from child self-reports.
- Letting a positive bias override hard no-delegate rules.
- Adopting a child's conclusion without opening its cited lines.
- Letting a child's output carry an earlier session's model, context, or price assumptions into this one.
