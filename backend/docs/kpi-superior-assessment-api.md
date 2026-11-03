# UC-09: KPI Superior Assessment (Phase 3 Slice 2 Backend)

## Scope and database readiness

Extends the existing assessment service, controller, DTOs and repositories. No frontend
changes and no attitude, appraisal, annual-result finalisation or KPI revision workflow.
The local development database and isolated Flyway history were verified through V39.
V39 already provides nullable Superior answers, completion metadata, checkpoint score,
lifecycle constraints, routing indexes and `CAN_REVIEW_KPI_ASSESSMENT`. No additional
table, migration, permission grant or Hibernate/Flyway configuration change is needed.
UAT assessments stay in `apex_manual_uat`; returning to development does not copy them.

## Access and lifecycle

All Superior actions require `CAN_REVIEW_KPI_ASSESSMENT`, an active account,
the saved submission route and the current `Staff.manager` relationship. A job-role
name or Employee Level does not grant access. No automatic rerouting is implemented.
Employees retain access to their own assessments and evidence. Superior Draft points
and comments are hidden from employees until the assessment is Reviewed.

- Only a submitted `PENDING_REVIEW` assessment in an Open period can be edited/reviewed.
- Saving Superior progress leaves the parent `PENDING_REVIEW`; it creates no official score.
- Review can start immediately after employee submission; it need not wait for the Self deadline.
- Completing all Superior points changes the parent to `REVIEWED` and freezes both answer sets.
- Comments are optional. There is no assessment Return for Revision action.
- Closed periods are read-only. Annual End Date does not independently close review access.
- Completion on the Superior deadline is on time. Later completion is allowed and stores
  `reviewedLate=true`; current `superiorOverdue` is separate from status and stored lateness.
- Completion and Draft saves use the existing transaction-level configuration lock and
  assessment row lock. Duplicate competing completion cannot overwrite an official review.
- Submitted item sets remain frozen; later KPI assignments are not added to a review.

## APIs

Base path: `/api/kpi-assessments`.

| Method and path | Behaviour |
|---|---|
| `GET /reviews?reviewPeriodId=...&status=PENDING_REVIEW` | Scoped queue; optional period/status filters. Omitted status includes Pending Review and Reviewed; Draft is invalid. |
| `GET /{id}` | Existing detail endpoint; includes Self answers, evidence, scoring definitions, recorded allocation and authorised Superior progress. |
| `PUT /{id}/superior-draft` | Save incomplete Superior progress; omitted items remain unchanged, explicit null clears a point/comment. |
| `POST /{id}/complete-review` | Validate saved progress and calculate/persist the official checkpoint score; no request body. |

Queue rows include employee, recorded role/department, review period, checkpoint,
submission/review dates and lateness, score, `canReview` and `superiorOverdue`.
Details additionally include `canSaveSuperiorDraft`, `canCompleteReview` and
actionable `reviewBlockers`. Employee `overdue`, `canSaveDraft` and `canSubmit` retain
their existing meaning. No queue GET creates or changes records.

Superior request (use saved assessment-item `id`, not assignment/KPI IDs):

```json
{
  "items": [
    { "itemId": 30, "superiorPoint": 4, "superiorComment": "Reviewed supporting evidence" }
  ]
}
```

Supplied points must be JSON integers 1-5; numeric strings, fractional values and
booleans are rejected. Empty Draft lists are allowed. Duplicate/foreign/null item
IDs and comments exceeding 10,000 characters are rejected before any answers change.
The employee's Self points/comments, assignments and evidence cannot be changed here.
Before completing a review, save the current Superior answers using the Draft endpoint.
Existing item evidence list/download endpoints retain permission and reporting checks;
Superior access does not grant evidence upload/deletion rights.

## Official checkpoint score

For each submitted KPI item:

`(Superior point / 5) * (item weight / 100) * recorded Employee-Level allocation percentage`

Sum contributions and round only the final score to four decimal places using HALF_UP,
matching V39 `NUMERIC(7,4)`. Self points never contribute. Recorded allocations must
total 100%; submitted items must internally total 100% for each included level, and
non-zero allocated levels cannot be missing. Zero allocations require no missing plan.
Use the period participant's recorded allocation, not current Role/Employee Level defaults.

For Company/Department/Individual Superior points 4/3/5 and each level internally 100%:
Top Management 78%, Middle Management 79%, Junior Management 81%, Executive 87%,
Admin 94%, General 97%. Checkpoint score is not the final annual appraisal score.

After successful transaction commit, the existing asynchronous email service notifies
the employee with a My Assessments link. Email failure does not undo a completed review;
no notification is dispatched when validation or persistence fails.

## Verification and boundaries

Focused tests cover scoped access, integer validation, incomplete Draft progress,
employee privacy, immutable Self answers, completion and duplicate protection, deadline
boundaries, post-annual-End-Date completion, Closed restrictions, frozen assignments,
all six allocations, unequal decimal weights, zero allocations and notification timing.
The PostgreSQL rollback-only fixture verifies queue queries, JPA persistence, Draft
privacy, official score round-trip and rollback without modifying live business data.

Still unresolved: reporting-line changes/rerouting, closure with pending assessments,
KPI Revision/effective dates/recalculation and mandatory comments/evidence policies.
No new workflow is invented for these cases. Frontend remains stopped for review.

Commit message: `feat(assessment): add KPI superior assessment backend`
