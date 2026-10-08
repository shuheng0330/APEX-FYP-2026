# Phase 3 Slice 1: KPI Self-Assessment Backend

This implements UC-08 only. The frontend, Superior completion/scoring, attitude
evaluation and annual result/appraisal finalisation are not implemented here.

## Availability and ownership

- The authenticated employee must be active, non-deleted and enrolled in the
  selected Annual Review Period. The checkpoint must match their recorded frequency.
- The checkpoint End Date is the measurement cut-off. Assessment opens the next
  calendar day, while the Annual Review Period is Open.
- The Self-Assessment deadline is inclusive. Later submission is accepted and
  records `submittedLate=true`; the separate `overdue` flag describes current Draft
  readiness. The Superior deadline does not extend the employee deadline.
- Annual End Date does not close assessment access. Closed periods are read-only.
- Drafts can be empty and can have missing points, assignments or an immediate
  Superior. Submission requires all current applicable points and a valid active
  immediate Superior, determined from the inherited `Staff.manager` relationship.
- Every non-zero recorded Employee-Level allocation requires its complete
  confirmed plan and assignments. Missing Company, Department or Individual KPIs
  appear in `missingLevels` and readable `submissionBlockers`.
- Confirmed assignments are projected into unsubmitted checkpoints on reads and
  reconciled on saves/submission, preserving existing answers and evidence. No
  missing KPI definitions are fabricated. Submitted item collections are frozen.

## API contract

Base path: `/api/kpi-assessments`. JSON responses are DTOs, never JPA entities.
Employee context and mutation endpoints require `ROLE_USER`. All ownership checks
are server-side; client requests do not select another employee.

| Method and path | Purpose |
|---|---|
| `GET /periods` | Employee's enrolled periods and recorded KPI allocations |
| `GET /checkpoints?reviewPeriodId=...` | Recorded-frequency checkpoints, opening dates, deadlines and existing assessment state |
| `GET /mine?checkpointId=...` | Existing assessment or virtual Draft with available KPIs/readiness; creates no records |
| `GET /{id}` | Owner or authorised, currently assigned and routed Superior |
| `POST /` | Save an incomplete Draft; returns 201 |
| `PUT /{id}` | Update that Draft; checkpoint cannot change |
| `POST /{id}/submit` | Submit saved answers, returning Pending Review |
| `POST /items/{itemId}/evidence` | Multipart `file` upload into a saved Draft item; returns 201 |
| `GET /items/{itemId}/evidence` | List accessible item evidence |
| `GET /evidence/{id}` | Authorised attachment download |
| `DELETE /evidence/{id}` | Owner removes evidence from a writable Draft; returns 204 |

Example Draft request:

```json
{
  "checkpointId": 12,
  "items": [
    { "assignmentId": 45, "selfPoint": 4, "selfComment": "Exceeded the monthly target." },
    { "assignmentId": 46, "selfPoint": null, "selfComment": null }
  ]
}
```

IDs above are illustrative. `items: []` saves an incomplete Draft. A supplied
answer replaces that assignment's Self point/comment; omitted assignments retain
existing answers. Duplicate or foreign assignment IDs are rejected. Points must
be JSON integers 1-5, or null in Drafts. Comments are limited to 10,000 characters.

Response items contain both `assignmentId` and a saved assessment-item `id`.
Newly projected items have no item ID yet: save the Draft before uploading their
evidence. The response includes checkpoint context, recorded allocations,
`canSaveDraft`, `canSubmit`, missing levels and submission blockers. A virtual Draft
has no ID or saved creation/update dates. Reads perform no reconciliation writes.

Submission takes no request body and rechecks the saved Draft against current
confirmed assignments under the shared KPI transaction lock. A newly assigned
unanswered KPI blocks submission with an actionable message. Duplicate submission,
post-submission edits and writes in Closed/not-yet-available periods are rejected.
The state becomes `PENDING_REVIEW`; notification to the routed Superior is queued
through the existing asynchronous email service only after transaction commit.
Email failure does not roll back a successful submission.

## Evidence defaults and security

Evidence is optional under UC-08. PDF/PNG/JPEG and the 10 MB prototype guidance
are **implementation defaults, not a confirmed TBM upload policy**.

- Default size limit: 10 MiB (10,485,760 bytes), configurable through
  `assessment.evidence.max-bytes` / `ASSESSMENT_EVIDENCE_MAX_BYTES`.
- The inherited global multipart transport limit remains unchanged. The stricter
  assessment-specific limit is checked before storage.
- Contents are parsed/decoded; declared MIME type and file extension are not trusted.
  PDFs must contain a page. Images have a 25-million-pixel decoding safety bound.
- Generated UUID storage keys are kept in the private `.kpi-assessment-evidence`
  folder under the existing upload directory. Keys are not exposed in DTOs.
  Original names are sanitised and never determine a storage path.
- Generic `/api/files/...` download access to this folder is denied. Assessment
  downloads enforce ownership or scoped Superior access, use attachment disposition,
  `nosniff` and `private, no-store` headers.
- Upload/removal requires an owner Draft in an available Open period. Storage is
  cleaned on upload transaction failure; removal deletes the physical file after
  commit. No mandatory-evidence configuration is introduced.

The new `CAN_REVIEW_KPI_ASSESSMENT` authority enables scoped submitted-assessment
and evidence reading, not employee editing. Access additionally requires both the
saved submission route and current immediate-Superior relationship. Drafts remain
private. No permissions are inferred from Role names or Employee Levels, and the
migration grants the new authority to no Role automatically.

## Database and rollout

V39 adds `kpi_assessment`, `kpi_assessment_item` and `kpi_assessment_evidence` plus
scope/uniqueness constraints and the reviewer authority. Composite FKs enforce
matching participant, period and checkpoint frequency, and prevent foreign-owner
assignments. One assessment exists per participant/checkpoint; one item per
assessment/assignment. Existing immutable KPI definitions and annual allocations
are reused rather than duplicated.

Nullable Superior fields reserve the approved Slice 2 structure only. No Superior
write/completion API exists here. `checkpointScore` stays null for Draft/Pending
Review, and Self points never produce an official score.

The isolated Flyway runner now verifies through V39. Use its existing backup/apply
procedure; do not replay legacy migrations, edit applied files or globally change
Flyway/Hibernate settings. No existing participants/assessments are backfilled.

Tests include service/MVC/ownership checks, evidence validation and cleanup, the
V39 rollback-only PostgreSQL constraint fixture, and a real JPA round-trip in the
existing Phase 2 integration fixture. Opt-in database checks use
`APEX_PHASE3_POSTGRES_TEST=true` and `APEX_PHASE2_POSTGRES_TEST=true`, respectively.
Fixtures are restricted to a local database and isolated transactional schemas.

## Remaining slices and TBC

The approved order remains KPI Superior Assessment, Attitude Configuration,
Attitude Self-Assessment and Superior Attitude Evaluation. Each follows backend
and tests, STOP for review, then separately approved frontend and tests.

Slice 2 official KPI calculation will use decimal arithmetic:
`(Superior point / 5) * (item weight / 100) * recorded level allocation`.
For level points 4/3/5 and full within-level plans, expected scores include Top
Management 78%, Middle Management 79%, Executive 87% and General 97%. Self points
must never affect those results. Unequal item weights and zero allocations require
separate scoring coverage when Slice 2 is implemented.

Attitude configuration must remain bound to each period without automatic
replacement by a newer edition. That binding and explicit initial binding for
existing Open periods belong to the later approved configuration slice.

Still unresolved/unimplemented: Superior changes/rerouting, closure with pending
assessments, KPI Revision/effective dates/recalculation, achievement integration,
mandatory comments/evidence and TBM's final file policy. No Return for Revision,
reopening, participant refresh or new recovery workflow is introduced.

Planned commit message: `feat(assessment): add KPI self-assessment backend`.
