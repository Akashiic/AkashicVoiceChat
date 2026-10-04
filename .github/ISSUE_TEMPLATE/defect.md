---
name: Defect (field or lab)
about: Report a defect with the evidence needed to attribute it to a build
title: "[defect] "
labels: ["type:defect", "needs-triage"]
---

## Build identity (required)

- JAR SHA-256 (`Get-FileHash <jar> -Algorithm SHA256`):
- Version label from the log (`Akashic Voice preInit ...`; **not** a build identity):
- Mode (`direct` / gateway):
- Platform (desktop / Android Pojav / Amethyst), JVM, LWJGL (2.9.1 / 3.x):
- Co-installed audio-related mods (ArchaicFix, Angelica, lwjgl3ify, ...):

## Observation

What happened, in the order it happened. Quote log lines **after redaction**.

## Evidence grade

- [ ] LIVE-OBSERVED (real client/server)
- [ ] AUTOMATED (test/soak, say which)
- [ ] STATIC (code reading only)

## Expected behavior

## Reproduction (if known)

## Hypothesis (optional, label it as hypothesis)

## Redaction checklist

- [ ] No session secrets, PSKs, tokens
- [ ] No production host/port or player identifiers
- [ ] No audio content attached
