# Attitude Self-Assessment Opening Date

## Backend contract

The existing Annual Review Period create/update/detail APIs accept and return
`attitudeSelfAssessmentStartDate` as an ISO calendar date. Drafts may omit it;
publication requires it. The date must be on or after the period Start Date and
on or before the employee Attitude Self-Assessment Deadline. Opening on the
deadline, or after the measurement End Date, is allowed.

The date is editable only in Draft and editable Upcoming periods under the
existing period-administration permission. A null/omitted field in an Upcoming
update preserves its saved value for compatibility with the previous frontend.

Attitude assessment responses add `availableFrom` and `openingPending`.
`openingPending` is true only when the scheduled opening date is the primary
availability blocker; missing configuration and non-Open periods keep their
existing messages. Existing `available`, `canSaveDraft`, `canSubmit`, and
submission blockers remain authoritative.

Employee create/update/submit requests are rejected before opening. Reads and
virtual Draft projections never persist an assessment. All employees share the
same opening date, independently of their KPI review frequency. Date checks use
the existing `annualKpiReviewClock`; there is no scheduler or new API endpoint.

The employee deadline still allows late submission while the period is Open.
Superior review starts after employee submission under the existing routing and
permissions. Attitude Configuration binding still happens at period opening.

## Migration and compatibility

V47 adds a nullable DATE column and backfills existing Upcoming/Open/Closed
periods from their Start Date. Drafts are not backfilled. Existing dates, bindings,
assessment answers, scores and audit timestamps are preserved.

The isolated migration runner validates the new column/constraints, makes its
normal backup, and checks all existing application data apart from the new
column. It refuses incompatible ORM-created columns or conflicting populated
published schedules. Run migrations before starting the updated application;
do not let Hibernate stand in for the migration.

The backend review checkpoint does not include frontend changes. The new field,
employee opening notice and date validation will be added to the frontend after
separate approval. No October date is automatically assigned to new periods.

## Verification

Focused unit/controller tests cover configuration, inclusive opening, direct API
enforcement, status/configuration blockers, late submission and existing reviews.
`AttitudeOpeningDateMigrationPostgresTest` and `AttitudeAssessmentPostgresTest`
use rollback-only temporary schemas when `APEX_PHASE3_POSTGRES_TEST=true`.
