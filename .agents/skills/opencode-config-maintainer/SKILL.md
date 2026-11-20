---
name: opencode-config-maintainer
description: Edit OpenCode configuration safely. Load before creating or editing opencode.json, opencode.jsonc, .opencode/agents/*.md, AGENTS.md, or any model, agent, mode, steps, or permissions setting, or when delegation fails because a role, model, or permission is missing.
compatibility: opencode
---

# OpenCode Config Maintenance

Treat configuration as code: smallest diff, validated, and approved.

## Verification status

Verified against the OpenCode V2 documentation on 2026-11-18. Re-check the model catalog at session start; re-check the schema if the OpenCode version changes.

## Schema notes

- Native V2 shape uses `agents` (the legacy form is `agent`), `model: provider/model#variant`, `steps`, and `permissions`.
- Custom subagents need `mode: subagent` (use `mode: all` only if the role should also run as primary). A new custom agent defaults to `primary` when `mode` is omitted, so it will not appear as a subagent.
- Add a `description` to every custom subagent. OpenCode shows it to the model choosing which agent to launch.
- A title model goes in `agents.title.model`. If an existing config uses `small_model`, migrate it and remove the legacy key.
- `agents.compaction.model` is documented. Do not configure hidden maintenance agents (for example `summary`) unless the current docs document the configuration field, not just the agent's existence.
- `permissions` is ordered and last-match-wins. To enforce a write allowlist, put a broad deny first, then path allows:
  `[{ "action": "edit", "resource": "*", "effect": "deny" }, { "action": "edit", "resource": "src/**", "effect": "allow" }]`.
- `edit` rules do not cover shell. A write-capable role needs shell restricted too. For a read-only role, deny `edit`, then either deny all shell or allow only read-only commands such as `git status`, `git diff`, `git log`, and `git blame`.
- Deny secret reads explicitly, for example `{ "action": "read", "resource": ".env", "effect": "deny" }`, plus equivalent rules for `.env.*`, key, and token files.
- Use the `subagent` permission action to control which agents the parent may launch, for example `{ "action": "subagent", "resource": "reader", "effect": "allow" }`.
- A `subagent` call that omits `model` inherits the parent session model, even when the agent definition has no model. The orchestrator must pass an explicit `model` on every call.
- An agent's `model` sets the role default; it does not block a per-call `model` override.
- Permissions are guardrails, not a sandbox. Shell prefixes can be chained; always verify the diff after a child returns.

## Role naming

The orchestrator skill uses `reader` and `deep`. If the config still has older names (for example `free-reasoner`, `go-specialist`), either rename them in config or update the orchestrator's role table. Keep one set only.

Keep role names and model bindings stable; the orchestrator's capability ledger keys on role+model.

## AGENTS.md check

Run this before finishing any change that adds, renames, or removes an agent, and whenever the user asks for skill wiring.

1. Check for `AGENTS.md` at the project root. For global changes only, also check `~/.config/opencode/AGENTS.md`.
2. If no project `AGENTS.md` exists, create it and insert the block below verbatim. Do not invent a differently named instruction file.
3. If `AGENTS.md` exists, read it first. Append the block only when the skill-trigger section is missing. Never overwrite, reorder, or drop existing content, and never duplicate the section.
4. Reference only skills that exist. Confirm `.agents/skills/<id>/SKILL.md` or `.opencode/skills/<id>/SKILL.md` before writing its trigger line; drop the line if the skill is absent. Keep the block otherwise unchanged.
5. `AGENTS.md` is prompt-level guidance, not an enforced hook. Do not claim it guarantees skill loading; enforcement requires a primary agent system prompt or permissions.

Block to insert:

```md
## Skills: load first (mandatory)

Before your first search, read, or edit, load the matching skill below with the `skill` tool. Skip when no listed skill applies, or for a trivial single-file edit.

- Load `smart-agent-orchestrator` for debugging, "where is X handled", refactoring, architecture, multi-file or multi-module changes, test generation, log triage, or any mention of subagents, parallel work, or model choice. Its default is "do not delegate".
- Load `opencode-config-maintainer` before editing or creating `opencode.json`, `opencode.jsonc`, `.opencode/agents/*.md`, or any model, agent, mode, steps, or permissions setting.
- After loading the orchestrator, state in one line whether you are delegating and why.

If a skill cannot be loaded, follow these minimum rules:

1. Resolve model availability and context size before dispatching. Pass an explicit `model` to every subagent call; omitting it inherits the parent model.
2. Do not delegate single-file edits, tightly coupled refactors, or work needing global consistency.
3. The parent owns git writes, builds, formatters, and tests. Parallel write tasks get disjoint file allowlists.
4. If a permission is denied, ask the user. Never work around it with shell redirection.
5. Do not edit OpenCode configuration without `opencode-config-maintainer`; if it cannot be loaded, ask the user.
```

## Procedure

1. **Confirm drift.** Name the exact mismatch: missing role, stale model ID, wrong `mode`, wrong permission, missing `steps`.
2. **Resolve model IDs.** Use the `models` tool and provider metadata. Never copy an ID from memory, an old document, or an example.
3. **Pick the target file.** Prefer project config (`opencode.json` / `opencode.jsonc`, `.opencode/agents/*.md`). Ask before editing any global file.
4. **Propose the diff first.** Show it and apply only after approval, unless the user explicitly asked for this change.
5. **Edit minimally.** Preserve unrelated settings, comments, and formatting.
6. **Validate.** Check JSONC syntax and list every changed file.
7. **Check `AGENTS.md`.** Run the AGENTS.md check above.
8. **Report.** List changed files and note that a new session may be needed before new or changed agents appear.

## Rules

- Enforce read-only roles with `permissions` (deny `edit`; deny `shell` or allow only read-only commands), not with prompt text alone.
- Every custom subagent gets `mode: subagent`, a `description`, and `steps` to bound cost.
- Name roles by capability (`reader`, `deep`), not by plan tier. Bind models in config only.
- Never rewrite config as a side effect of an unrelated task. Propose it separately.
- Never overwrite, reorder, or replace an existing `AGENTS.md`. Append the missing section, or propose the change and let the user decide.
- Never reference a skill in `AGENTS.md` that is not installed in this workspace.
- If `edit` permission is denied, ask the user. Do not bypass it with shell redirection or any other workaround.
- Do not hardcode a model ID that you have not resolved in this session.
