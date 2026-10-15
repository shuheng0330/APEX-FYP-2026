# Central KPI Results - Slice 1 Backend Foundation

This document records the Slice 1 checkpoint. Slice 2 publication/correction/evidence additions
are documented in `central-kpi-results-publication.md`; the V46 migration remains unchanged.

## Delivery boundary

V46 is forward-only. It adds central result editions/items, protected evidence metadata,
period-scoped Company delegates and a participant/checkpoint aggregate foundation.
Existing period settings, plan versions, assignments, answers, evidence and scores are preserved.
All existing periods and assessments are marked `LEGACY_ALL_LEVELS`; none are converted.

This checkpoint exposes scoped context/read APIs, incomplete central Draft creation/update,
and Company delegate assignment/revocation. It deliberately exposes no publication,
correction, evidence-upload or aggregate-calculation actions. Those are Slice 2/3 work.
No frontend or Attitude opening-date changes are included (Attitude timing is Slice 4).

The SQL default for a future period is `CENTRAL_RESULTS`, but the existing Java creation
path explicitly remains `LEGACY_ALL_LEVELS` until the Individual-only integration in Slice 3
is approved and implemented. This prevents an incomplete rollout from changing today's
employee assessment workflow. No production API to convert an existing period is provided.

## Permissions

- `CAN_MANAGE_COMPANY_KPI`: Company result owner and period delegate administration.
- `CAN_ENTER_COMPANY_KPI_RESULTS`: delegate result access **only together with** an active
  assignment to that period. No implicit Company plan edit/revision or delegation right.
- `CAN_MANAGE_DEPARTMENT_KPI`: active, eligible business account restricted to the existing
  resolved HOD Department. Department approval permission alone does not grant result entry.
- No Role grants are guessed or added. Assign the new authority explicitly using existing RBAC.
- Every write rechecks account, authority, scope, period, state and checkpoint availability.

Revocation retains the assignment row, assigning actor/time and revoking actor/time. A new
assignment creates a fresh row. Neither revocation nor reassignment changes prior results.
Closed periods are read-only, including delegate administration.

## API contract

All paths below are relative to `/api/kpi-central-results`.

| Method/path | Purpose |
| --- | --- |
| GET `/periods` | Accessible published central-model periods; no administration permission needed. |
| GET `/options?reviewPeriodId=...` | Authorised confirmed Company/Department versions required by participants. |
| GET `/checkpoints?planVersionId=...` | Matching participant-frequency checkpoints, including future checkpoints. |
| GET `/?reviewPeriodId=...` | Scoped Draft/publication history list. |
| GET `/{id}` | Scoped result details, immutable KPI definitions and readiness blockers. |
| POST `/` | Create first Draft for a version/checkpoint; no records created by GET. |
| PUT `/{id}` | Partial answer update; expected optimistic-lock token required. |
| GET `/periods/{periodId}/delegates` | Company owner: assignment/revocation history. |
| POST `/periods/{periodId}/delegates` | Company owner: assign `{ "staffId": "uuid" }`. |
| POST `/periods/{periodId}/delegates/{delegateId}/revoke` | Company owner: revoke the period assignment. |

Draft requests:

```json
{
  "planVersionId": 12,
  "checkpointId": 35,
  "expectedLockVersion": 0,
  "items": [
    { "kpiId": 72, "actualAchievement": "", "point": null, "comment": null }
  ]
}
```

An empty answer collection is permitted. The response supplies actual items from the confirmed
version with unanswered values, never fabricated KPIs. Omitted answers on update retain saved
values; an explicitly null value clears that answer. Foreign or duplicate KPI IDs are rejected.
Supplied points must be JSON integers 1-5. Achievement/comment text is limited to 10,000 characters.
Missing achievement/points appear in `publicationBlockers`, but do not prevent Draft saving.
Only `UPDATE_DRAFT` is returned in `allowedActions`; publication is not implemented in this slice.

Entry opens on checkpoint End Date + one calendar day using `annualKpiReviewClock`. Open periods
remain writable after deadlines or Annual End Date. Upcoming/Closed periods cannot save results.
All frequencies use existing generated checkpoints, not the actor's own review frequency.
A replaced version is offered only when a started assessment for that checkpoint requires it.

## Schema safeguards

Composite keys enforce period, checkpoint frequency, KPI level and version consistency.
Partial unique indexes prevent competing Drafts/current publications and duplicate active
delegation. Published edition contents/items/evidence are protected from mutation in PostgreSQL.
Nullable aggregate scores represent pending results, never zero. The aggregate schema records
source publications and calculation audit without altering Individual review metadata.

## Verification and local migration

Stop the backend before compiling entities or applying schema changes: the inherited development
profile enables Hibernate schema updates. Do not change global Flyway/Hibernate configuration.
Use `ApplyAnnualReviewPeriodFoundation.java` with the existing classpath and `pg_dump` procedure
documented in `src/main/resources/db/migration/annual-kpi/README.md`. It backs up before migration,
rejects untracked partial structures and verifies existing application row digests afterwards.

Focused tests: `KpiCentralResultServiceTest`, `KpiCentralResultControllerTest` and opt-in
`CentralKpiResultMigrationPostgresTest` (`APEX_PHASE2_POSTGRES_TEST=true`). The PostgreSQL test
uses a random transactional schema and rollback; it never installs fixtures into live tables.
Regressions cover legacy assessment scoring, period publication, KPI revisions and Attitude workflows.

Stop after backend tests/build for review. Do not implement Slice 2 or any frontend, create live
test records, reset a database, commit or push at this checkpoint.

### Verified local delivery (13 October 2026)

- V46 applied to the existing local development database through the isolated runner; a second
  run validated all 21 migration-history entries and applied zero migrations.
- Backup: `C:/Users/LENOVO/.apex/database-backups/before-annual-kpi-V46-20261013-122352-619.dump`.
- The runner verified exact empty Hibernate-created foundation structures before adoption and
  checked existing application-table row digests after migration. No data or permissions grants
  were backfilled beyond the explicit legacy model marker and new unassigned authority.
- 530 focused controller/service/Phase 1-3 regression tests passed in the packaging run;
  35 opt-in PostgreSQL methods were skipped there. Separately, 20 PostgreSQL migration/JPA and
  legacy workflow checks passed in rollback-only schemas, including 10 new migration tests.
- Backend JAR packaging passed. `clean` could not remove an existing running UAT mail helper's
  log, so that helper was preserved and packaging ran without the clean phase.
