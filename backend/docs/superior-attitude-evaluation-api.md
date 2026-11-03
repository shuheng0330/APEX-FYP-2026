# UC-11 Superior Attitude Evaluation (Phase 3 Slice 5 backend)

## Access and lifecycle

Review endpoints require `CAN_REVIEW_ATTITUDE_EVALUATION`, an active account, the
employee's current `Staff.manager` relationship and the Superior recorded at Self
submission. Permission alone never grants access to arbitrary employees. No Role
grants are guessed. Existing employee endpoints retain `ROLE_USER` and ownership.

The parent remains `PENDING_REVIEW` while the Superior saves incomplete progress.
`superiorDraftSaved` distinguishes a saved Superior Draft, including an empty save,
from an untouched Pending Review. It is derived from the existing routed Superior
and `updatedBy`; opening or reading an assessment does not write or start a Draft.
Completion changes the parent to `REVIEWED`. There is no return/approval workflow.

## Endpoints

All routes are under `/api/attitude-assessments`:

- `GET /reviews?reviewPeriodId=...&status=PENDING_REVIEW`: optional period/status
  filters; omitted status includes Pending Review and Reviewed, ordered by submission
  date then ID. Employee Drafts are excluded; `status=DRAFT` is rejected. Saved
  Superior Drafts remain in Pending Review with `superiorDraftSaved=true`.
- `GET /{id}`: existing owner details also accessible to the routed immediate
  Superior with review authority. Returns bound rating definitions and frozen criteria,
  Self answers, Superior answers, deadlines, progress flags and review blockers.
- `PUT /{id}/superior-draft`: save incomplete Superior answers. Omitted items retain
  existing answers; a supplied null point clears that answer. Supplied points must be
  JSON integers 1-5. Comments are optional, trimmed and limited to 10,000 characters.
- `POST /{id}/complete-review`: completes the **stored** answers after all applicable
  criteria have Superior points. Save changed answers before completing.

Example Draft request:

```json
{"items":[{"itemId":123,"superiorPoint":4,"superiorComment":"Observed consistently"}]}
```

Queue rows include employee/recorded Role/department, period, format, parent status,
submission/completion dates and lateness, `superiorDraftSaved`, `superiorOverdue`,
`canReview` and the completed `attitudeScore`. Details additionally provide
`canSaveSuperiorDraft`, `canCompleteReview` and `reviewBlockers`.

## Scoring, history and safety

Official score = sum of Superior points / (criterion count x 5) x 100, using decimal
arithmetic, rounded HALF_UP to four decimal places. All applicable shared and
format-specific criteria have equal weight. Self points and Employee-Level KPI
allocations never enter this calculation. The period's original configuration and
criteria remain in use, not the latest global configuration.

Employees cannot see unfinished Superior answers. After completion, their existing
details response includes final Superior points/comments, `reviewedAt`, `reviewedBy`,
`reviewedLate` and `attitudeScore`. Reviewed answers/results are immutable.

An Open period permits late review, including after its annual End Date. Completion
on the Superior deadline is on time; afterwards `reviewedLate=true` is stored and
does not change when deadlines change. Current `superiorOverdue` is separate.
Closed periods are readable but reject Draft saves and completion.

Mutations acquire the existing period configuration lock and assessment row lock,
preventing competing completion/saves. Notifications use the existing async email
service after successful commit; failed persistence sends no completion email.

## Migration and boundaries

V42 adds only the new authority and extends the authority CHECK constraint. It adds
no tables/columns, rewrites no assessment data and grants no Roles. Apply using the
backed-up isolated runner through V42, without replaying legacy migrations or changing
global Flyway/Hibernate configuration.

Tests use fixed clocks and rollback-only PostgreSQL schemas. Existing local periods,
configuration bindings and KPI data are preserved. Frontend, reporting-change
rerouting, period closure prerequisites, participant refresh, KPI Revision and later
Appraisal/Annual Result finalisation remain outside this slice.
