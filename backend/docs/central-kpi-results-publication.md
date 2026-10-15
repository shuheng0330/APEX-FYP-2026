# Central KPI Results - Slice 2 Backend

## Boundary

Adds publication, audited correction editions, scoped history and optional evidence to the
Slice 1 APIs. Uses the existing V46 schema unchanged; no new migration or Role grant.
Existing periods, assessments, approved definitions and scores are not converted or recalculated.
Individual-only assessment/scoring integration is Slice 3; Attitude opening dates are Slice 4.
There is no frontend change in this backend checkpoint.

## Workflows

1. Save an incomplete Draft using the existing version/checkpoint-scoped APIs.
2. Complete Actual Achievement and a Result Point (integer 1-5) for every confirmed KPI item.
3. Publish with the latest `expectedLockVersion`. Publication records the authenticated actor and
   business-clock timestamp. The set becomes immutable and is the effective central publication.
4. Correct the current publication with a mandatory trimmed reason (maximum 10,000 characters).
   A fresh Draft edition copies the answers, comments and evidence; the published edition remains
   effective. There can be only one correction Draft for that version/checkpoint.
5. Publish the correction. The previous edition becomes `SUPERSEDED` and the correction becomes
   `PUBLISHED` in the same transaction. History preserves both contents and publication audit.

No new approver, return workflow, cancellation action, meaningful-change requirement or automatic
achievement-to-rating conversion is introduced. Unchanged complete corrections remain publishable
with a reason. Result corrections do not edit KPI definitions or perform UC-05 plan revision.

Company owners need `CAN_MANAGE_COMPANY_KPI`; delegates need both
`CAN_ENTER_COMPANY_KPI_RESULTS` and an active period assignment. HOD actions use existing
`CAN_MANAGE_DEPARTMENT_KPI` plus resolved Department scope. Revocation, account status, period
status, checkpoint availability and participant/version eligibility are rechecked on writes.
Upcoming/Closed periods cannot mutate results or evidence. An ended Open period remains writable.

## New API contract

Paths are relative to `/api/kpi-central-results`.

| Method/path | Body/query | Response |
| --- | --- | --- |
| POST `/{id}/publish` | `{ "expectedLockVersion": 2 }` | Published result details |
| POST `/{id}/corrections` | `{ "expectedLockVersion": 3, "reason": "Correct the reported revenue" }` | 201, new correction Draft |
| GET `/{id}/history` | None | Same version/checkpoint editions, newest first |
| POST `/items/{itemId}/evidence` | Multipart `file`, `expectedLockVersion` | 201, evidence metadata and new `resultLockVersion` |
| GET `/items/{itemId}/evidence` | None | Scoped evidence metadata |
| GET `/evidence/{id}` | None | Attachment download, verified type, nosniff, private/no-store |
| DELETE `/evidence/{id}` | `expectedLockVersion` query parameter | Removed metadata and new `resultLockVersion` |

Evidence mutation responses are `{ "evidence": { ... }, "resultLockVersion": 4 }`.
The UI must retain its answers and advance its optimistic-lock token after evidence changes.
Unknown/foreign item IDs, stale or absent tokens, duplicate correction requests, incomplete
publication and attempts to modify published evidence fail without replacing effective results.

Details include item `evidence`, `publicationBlockers`, `allowedActions` and `workingCorrectionId`.
Possible actions are `UPDATE_DRAFT`, `UPLOAD_EVIDENCE`, `PUBLISH`, `CREATE_CORRECTION`,
or `CONTINUE_CORRECTION`. Publication appears only when complete; Closed and superseded sets
have no mutation actions. The existing Draft update route still accepts partial answer updates.
GET endpoints never create records. Employee and Superior summary access remains Slice 3, not
an expansion of central result entry permissions.

## Evidence security and preservation

- Reuses the existing private assessment storage, UUID keys and verified PDF/PNG/JPEG contents.
  The configurable 10 MiB default and bounded image decoding are implementation defaults, not
  a newly confirmed stakeholder upload policy. Evidence and comments remain optional.
- Every read/mutation checks the central result's Company delegation or HOD scope. Metadata
  responses never expose physical paths or storage keys. Generic file routes cannot bypass this.
- Correction evidence receives fresh metadata IDs and independent file keys. It retains the
  original uploader and upload timestamp; correction creation audit records who copied the set.
- Failed uploads/copies and transaction rollback clean up newly created files. Draft removals
  delete the physical file only after commit. Published originals are never overwritten/deleted.
- Missing inherited files fail correction creation explicitly rather than silently dropping evidence.
- Generic upload/delete now reuse the same private/outside/symlink path guard as generic reads;
  ordinary learning/SOP file operations remain unchanged.

## Concurrency and verification

Writes use the existing transaction-scoped configuration lock, result row locks and optimistic
tokens. V46 partial unique indexes enforce one working Draft and one effective publication.
Supersession changes only status and the operational lock counter, preserving the earlier
publication's immutable timestamps and contents. A failed finalisation rolls back both editions.

Focused tests cover publication completeness, both KPI levels, repeated corrections, history,
duplicate/stale writes, permission combinations, revoked delegates, scope/Closed restrictions,
safe evidence copies, mutation/read isolation and rollback cleanup. Real PostgreSQL tests use
isolated test schemas, never live fixture records; existing Phase 1-3 regressions are retained.

The opt-in PostgreSQL run uses `APEX_PHASE1_POSTGRES_TEST=true`,
`APEX_PHASE2_POSTGRES_TEST=true` and `APEX_PHASE3_POSTGRES_TEST=true`:

```powershell
.\mvnw.cmd '-Dtest=*Kpi*Test,*Attitude*Test,*AnnualReview*Test,*ReviewCheckpoint*Test,*ReviewPeriod*Test,*EmployeeLevel*Test,*LearningMaterial*Test' package
```

Earlier migration fixtures exclude the later `CAN_ENTER_COMPANY_KPI_RESULTS` permission when
reconstructing their historical schema. No applied migration is changed. The learning-material
video metadata fixture now uses a temporary file instead of an absent ignored repository file.
Private JPA fixtures use independent sequences as well as rollback-only schemas.

Verified on 13 October 2026: 694 tests discovered, 692 passed, no failures/errors, and two
Windows symlink-creation skips; backend packaging succeeded. The isolated migration runner
validated V46 with zero pending migrations and preserved business-row digests. Slice 2 needs
no schema change. Existing periods remain on their legacy assessment model.

Stop after backend tests/build and review. Frontend and Slice 3 require separate approval;
no commit or push is authorised by this checkpoint.
