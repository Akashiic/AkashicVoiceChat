# CLAUDE.md

Entry point for Claude (or any coding assistant) working in this repository.

1. Read [`AGENTS.md`](AGENTS.md) — the project's engineering rules. They apply to you.
2. Read [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md), then [`docs/HANDOFF.md`](docs/HANDOFF.md).
3. Check [`docs/ISSUES_REGISTER.md`](docs/ISSUES_REGISTER.md) / GitHub Issues before starting. Pinned issues: #1 (homologation tracker), #2 (build identity), #3 (OpenAL natives after sound reload).

## Owner and language

The owner is Gabriel (GitHub `Akashiic`, Rede Akashic). Conversation with him is in Brazilian Portuguese; repository documentation, commit messages, issues and code comments are in English. [`docs/PT-BR_RESUMO.md`](docs/PT-BR_RESUMO.md) is the Portuguese summary — keep it in step with `PROJECT_STATE.md`.

## Hard rules (short form)

- No release tag, production claim or "stable" wording without the evidence a gate requires. Current state: RC7, not stable, nothing live-approved.
- Label every statement with its evidence grade (AUTHORED / STATIC / BUILD / AUTOMATED / LIVE-OBSERVED / LIVE-APPROVED). A synthetic result never substitutes for a live observation.
- Never commit secrets: session secrets, agent PSKs, tokens, production host/port, player identifiers. Redact before importing logs.
- Do not commit JARs, ZIPs, raw client logs or audio captures. Record SHA-256 hashes instead.
- Never `reset`, `clean` or `stash` changes you did not create. Preserve `docs/evidence/**` as written.
- Keep `src/` and `shared/client-protocol/` on Java 8 bytecode (class major 52); modern dependencies stay under `platform/`.
- State plainly what you did **not** run. Builds and QA have not been re-executed in this repository yet (see [`docs/BUILD_AND_QA.md`](docs/BUILD_AND_QA.md)).

## When a gate or issue changes

Update `docs/PROJECT_STATE.md`, `docs/HOMOLOGATION.md` and `docs/ISSUES_REGISTER.md` in the same commit, citing the evidence file. Add an entry to `CHANGELOG.md` under *Unreleased*.
