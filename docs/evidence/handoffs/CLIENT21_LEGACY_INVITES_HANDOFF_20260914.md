# Akashic Voice - CLIENT21 legacy invite hardening

Checkpoint: AV-LEGACY-GROUP-INVITES-20260914-21.
Candidate: audit/group-integration-20260913-04/integrated-client21.
Base: fully gated CLIENT20. Active source/dist were not promoted or deployed.

## Scope
CLIENT21 changes exactly three production files plus one QA smoke versus CLIENT20:
- CommonEvents.java
- GroupRegistry.java
- new LegacyGroupInviteBook.java
- LegacyGroupInviteBookSmoke.java

The legacy Forge authority now uses bounded ephemeral invite grants instead of an unbounded Map<UUID,Set<UUID>>.
Default policy matches the federated gateway direction: TTL 300 s, 64 pending per group,
8 pending per recipient and 4096 pending globally. Duplicate invites do not refresh lifetime.
Targets must be online and self-invites are rejected.
Groups are capped at 64 members for new accepts.
Accept revalidates that the original inviter is still a member and that the responder did not join another group meanwhile.
Logout/leave purge grants involving that player; group dissolution purges the whole group's grants.
A server-tick maintenance hook expires grants every 100 ticks, avoiding a 20-Hz full scan.

## Automated evidence
- Final Forge build: PASS, 27 executed tasks on the final snapshot.
- Actual JDK 8u411: 151 production/QA sources compile; 35 smoke mains + one explicit Java backend execution PASS.
- LegacyGroupInviteBookSmoke: 6 contract blocks plus 20,000 deterministic operations PASS.
- Platform: 124 JUnit tests / 27 suites, zero failures/errors/skips.
- Static integration audit: 11/11 invariants PASS.
- Mutation sensitivity: 8/8 deliberate invite defects detected.
- Reobfuscated distribution inspection: 287 Java 8 classes, 5 JNI, 7 natives, notices/inventory PASS.
- DEV-JAR origin test: CommonEvents, GroupRegistry, LegacyGroupInviteBook, VoiceServer and WireProtocol load from the JAR.
- New invite smoke passes again with LegacyGroupInviteBook loaded from the DEV JAR.

An earlier mutation run detected 7/8 because the self-invite assertion shared a saturated global-limit fixture.
That test defect was corrected by isolating self-invite in an unsaturated book; the final 8/8 result supersedes it.
An earlier Java8 argfile attempt failed before compilation because Windows backslashes were interpreted as escapes;
paths were normalized to forward slashes and the final complete matrix passed.
## Artifact identity
Reobfuscated mod SHA256:
6747b71538728a3a56595f6c33f0731c159d339306b7615ca4f304fbc88311dd
DEV JAR SHA256:
08345b504bdc533799f6cf08d37056c6cd5c657571a90a960603cf28dd295508
No integration21 lock remains.

## Remaining gates
This is automated/source approval, not live gameplay approval.
Real group invitations, UI clicks, transfer behavior and live server permissions still need homologation.
Explicit session identity/generation remains the next protocol-hardening target.
