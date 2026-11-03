# Annual Attitude Self-Assessment (UC-10)

Phase 3 Slice 4 is database/backend only. No employee frontend or UC-11 Superior review actions are included.

## Access and availability

All routes require `ROLE_USER`, an active non-deleted account and ownership of an enrolled participant.
Setup and unrelated reviewer permissions do not grant access to another employee's assessment.
There are no hardcoded Job Role names or Employee Level inferences.

UC-10 makes the annual assessment available when the Review Period is **Open**, regardless of
Monthly/Quarterly/Annually KPI frequency. It does not wait for a checkpoint or annual End Date.
The period must have a Published Attitude Configuration bound through UC-12 and the participant's
recorded Job Role must have a format mapping in that edition. Only active Shared Core Values and
active criteria for that format apply. Rating labels/descriptions come from that bound edition.
Newer published editions do not replace it. GET requests never create assessments or bind configuration.

An unbound period returns HTTP 200 with `available: false`, empty items, null assessment/status and:

> Attitude Evaluation Not Yet Available
>
> The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.

Missing Role mappings return an actionable employee message without exposing administrative endpoints.
Upcoming periods are unavailable; Closed periods remain readable but cannot be saved/submitted.
Missing attitude setup does not affect KPI-related activities.

## APIs

Base: `/api/attitude-assessments`.

| Method | Path | Behaviour |
| --- | --- | --- |
| GET | `/periods` | Employee's enrolled period contexts, using the existing context DTO. |
| GET | `/mine?reviewPeriodId=...` | Existing assessment, virtual Draft or friendly unavailable response. |
| GET | `/{id}` | Owner-only saved details, criteria, ratings, deadlines and readiness. |
| POST | `/` | Create one annual Draft per participant (201); incomplete answers allowed. |
| PUT | `/{id}` | Save supplied Draft answers; omitted answers remain unchanged. |
| POST | `/{id}/submit` | Validate and freeze Self answers, route to immediate Superior, Pending Review. |

Draft request example:

```json
{
  "reviewPeriodId": 1,
  "items": [{ "criterionId": 11, "selfPoint": 3, "selfComment": "My reflection" }]
}
```

`items: []` saves an incomplete Draft. A null collection is invalid. Points may be null in Drafts;
supplied points must be JSON integers 1-5 (not strings, fractions or Boolean values).
Comments are optional, trimmed, limited to 10,000 characters as an implementation limit, not a TBM policy.
Duplicate IDs and foreign/inactive/other-format criteria are rejected. The service validates the full
request before changing answers. Period/configuration/format cannot be changed through assessment requests.
All applicable criteria are included automatically; clients cannot remove required criteria.

Responses include recorded employee/Role/department context, configuration ID/name, format, criteria,
rating definitions, Self answers, status, submission metadata, deadlines, `available`,
`availabilityTitle`, `availabilityMessage`, `canSaveDraft`, `canSubmit`, `submissionBlockers` and `overdue`.
They do not expose other employees' configuration mappings or unfinished Superior answers.

## Submission and history

Final submission requires a point for every applicable criterion and an active, non-deleted immediate
Superior other than the employee. Drafts do not require a Superior. The live inherited `Staff.manager`
relationship supplies the saved submission route; no second reporting hierarchy is created.
Submission on the Self deadline is on time; later submission is allowed and records `submittedLate`.
The annual End Date does not auto-close the period. Current Draft `overdue` is separate from stored
submission lateness. Submitted Self answers are read-only; duplicate submissions are rejected.
After successful commit, the existing asynchronous mail service notifies the routed Superior.

`attitude_assessment` holds one record per participant, bound configuration/format and lifecycle metadata.
`attitude_assessment_item` holds criterion references and nullable Self answers. Composite FKs enforce
matching participant/period, period/configuration binding and item/criterion configuration. Immutable
configuration criteria/ratings are reused rather than copied. Nullable Superior fields reserve the
UC-11 reviewer flow; no official score is calculated from Self points.

V41 makes no participant/configuration backfills or RBAC grants. Apply using the backed-up isolated runner
with the backend stopped before compilation. Leave applied migrations/global Flyway/Hibernate settings
unchanged. PostgreSQL tests use `APEX_PHASE3_POSTGRES_TEST=true` and rollback-only schemas, never live fixtures.

## Deliberate boundaries

- UC-11 reviewer queue, Superior Draft/completion and official scoring are now documented in
  `superior-attitude-evaluation-api.md`; V42 provisions its permission without Role grants.
- No evidence upload: UC-10 specifies points/comments, unlike UC-08's optional supporting evidence.
- No return/reopening, participant refresh, reporting-change rerouting, closure prerequisites or KPI Revision.
- The existing manual KPI UAT clone/baseline is unchanged; verify its migration baseline before attitude UAT.
