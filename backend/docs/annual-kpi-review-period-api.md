# Annual KPI Review Period Management

Scope: UC-01 / UC-02 and Employee Level selection in the existing Role drawers. Includes the annual-period frontend; no KPI, assessment, attitude scoring, result or appraisal implementation.

## Access and APIs

All routes require authentication and `CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD`.
V28 grants this dedicated permission only to the active `superadmin` role. The legacy
`CAN_MANAGE_EVALUATION_CYCLE` no longer authorises any annual KPI review period route.
Existing tokens must be refreshed or obtained through a new sign-in to include the new grant.

Base: `/api/annual-kpi-review-periods`

| Method | Route | Behaviour |
| --- | --- | --- |
| POST | base | Save an incomplete Draft; returns 201 |
| POST | base + `?publish=true` | Validate, create and publish; returns 201 |
| POST | `/{id}/publish` | Publish a saved Draft |
| PUT | `/{id}` | Replace editable configuration; Draft remains Draft, Upcoming remains published |
| GET | base | List periods, including configuration and checkpoints |
| GET | `/{id}` | Retrieve configuration and generated schedule |
| GET | `/roles` | Non-deleted, performance-review-eligible roles with configured default frequency, or ANNUALLY |
| GET | `/creation-defaults?startDate=2027-01-01` | Employee Level weights from the latest published period and each Role's latest saved frequency, or initial defaults |
| POST | `/preview` | Validate full configuration and return an unsaved configuration/schedule preview |
| DELETE | `/{id}` | Delete an editable Draft/Upcoming and its role/checkpoint configuration; returns 204 |

For editing previews, supply `?excludedPeriodId={id}` to exclude that editable period from name/overlap checks.
Preview results have no persisted ID, reference or checkpoint IDs.
Deletion confirmation belongs to the calling UI. There is no Close or Reopen endpoint.

## Configuration Request Example

Role and Employee Level IDs are illustrative: use `GET /roles` and `GET /api/employee-levels`.

```json
{
  "name": "2027 Annual KPI Review",
  "startDate": "2027-01-01",
  "endDate": "2027-12-31",
  "roleConfigurations": [
    { "roleId": 7, "reviewFrequency": "MONTHLY" }
  ],
  "employeeLevelConfigurations": [
    {"employeeLevelId": 1, "companyKpiWeight": 50, "departmentKpiWeight": 30, "individualKpiWeight": 20},
    {"employeeLevelId": 2, "companyKpiWeight": 35, "departmentKpiWeight": 35, "individualKpiWeight": 30},
    {"employeeLevelId": 3, "companyKpiWeight": 25, "departmentKpiWeight": 35, "individualKpiWeight": 40},
    {"employeeLevelId": 4, "companyKpiWeight": 15, "departmentKpiWeight": 25, "individualKpiWeight": 60},
    {"employeeLevelId": 5, "companyKpiWeight": 10, "departmentKpiWeight": 10, "individualKpiWeight": 80},
    {"employeeLevelId": 6, "companyKpiWeight": 5, "departmentKpiWeight": 5, "individualKpiWeight": 90}
  ],
  "kpiPerformanceWeight": 50,
  "attitudeEvaluationWeight": 50,
  "annualKpiConsolidationMethod": "FINAL_CHECKPOINT",
  "kpiSetupDeadline": "2026-12-31",
  "selfAssessmentDaysAfterCheckpoint": 5,
  "superiorAssessmentDaysAfterSelfDeadline": 5,
  "attitudeSelfAssessmentDeadline": "2027-12-05",
  "superiorAttitudeEvaluationDeadline": "2027-12-15",
  "appraisalRecommendationDeadline": "2028-01-15",
  "hrFinalisationDeadline": "2028-01-25"
}
```

Supported frequencies: `MONTHLY`, `QUARTERLY`, `ANNUALLY`.
Consolidation settings: `FINAL_CHECKPOINT`, `AVERAGE`; this phase stores the selection but does not calculate annual scores.

PUT is a full configuration replacement, not a PATCH. Submit all configuration fields to retain them.
IDs, status, reference and audit fields are not accepted as writable configuration.
Omitted performance/attitude weights default to 50/50; explicitly null values are incomplete and cannot be published.
An omitted role frequency retains that period's saved frequency on modification; for a newly selected role it resolves to the role default, then ANNUALLY.
Period overrides do not modify global role defaults.

### Employee Levels and Configuration Reuse

`GET /api/employee-levels` is read-only and requires `CAN_MANAGE_ROLE` or
`CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD`. It returns the six levels with IDs, names, codes and initial defaults.
The existing Role create/edit requests accept `employeeLevelId`; overview/details responses include level metadata.
New Roles require a level. Editing an inherited unclassified Role requires classification.
Omitting a level on an already-classified Role preserves it. Role updates and bulk operations preserve default review frequency.
Role exports append `Employee Level Code` as column nine. Older eight-column imports preserve existing mappings;
new Roles require the additional level code. No Role-name matching is performed.

Creation defaults select Upcoming/Open/Closed periods, regardless of overlap with the intended Start Date,
ordered by End Date, Start Date and ID descending. Drafts are excluded. Weights come from the latest published
period. Each Role's frequency comes from the latest published period containing that Role's saved configuration,
even when that Role is absent from the newest period. Without saved history, use `Role.default_review_frequency`,
then ANNUALLY. Explicit creation frequencies override these defaults. All copies are independent.
`sourceReviewPeriodId` and `sourceReviewPeriodName` identify the weightage source, not a shared source for all Role frequencies. Current Role-to-Level
mappings are used for the new period, not inherited historical Role classifications.
No deadlines, checkpoints, participants or statuses are copied.

Omitting `employeeLevelConfigurations` on dated creation loads those defaults. An explicit array overrides them,
including an empty Draft array. A dateless Draft remains uninitialised. On updates, omitted configurations retain
that period's stored weights and never automatically reload creation defaults; an explicit array replaces them.
Non-null obsolete top-level Company/Department/Individual weights are rejected.
Publication freezes each Role's reference to that period's level configuration. Later global Role reclassification
does not change it, including when an unrelated Upcoming-period field is edited. Draft publication resolves current classifications.

## Rules

- Drafts can be incomplete, including provisional weight totals; supplied values must still be valid.
- Publishing requires name, dates, at least one unique non-deleted eligible role, all deadline settings, all weights and a consolidation method.
- Names are trimmed, limited to 255 characters and unique using the inherited database's case-sensitive uniqueness convention.
- Start Date must precede End Date.
- The single KPI Setup Deadline must be on/before Start Date. It covers completion of Company, Department and Individual KPI setup, including any required submission/review/approval. Non-null obsolete separate deadline inputs are rejected.
- Super Admin is a system role, excluded from available/configured review roles, validation and participant creation. Management permissions are unchanged. Eligibility is persisted independently of the Role name, permissions and Employee Level; Role edits/imports preserve it.
- Weights are 0-100, with at most two decimal places. Each of all six Employee Level configurations must total 100 at publication; performance/attitude weights separately total 100. Every selected Role must be classified.
- Assessment offsets are positive calendar days. Self-assessment follows checkpoint end; superior assessment follows self-assessment deadline.
- Attitude self-assessment cannot precede Start Date; superior attitude deadline follows self-assessment deadline.
- Recommendation deadline cannot precede final checkpoint superior assessment or superior attitude deadlines; HR deadline follows recommendation deadline.
  These are date-dependency validations, not review-period closure/completion prerequisites.
- Upcoming/Open measurement date ranges cannot overlap, including a shared boundary date. Draft/Closed ranges do not block publication.
- Future-start publications are Upcoming; a reached Start Date produces Open.
- A startup check and minute-based scheduler open due Upcoming periods, including after a restart. End Date NEVER automatically closes them.
- Draft and not-yet-started Upcoming periods can be updated/deleted. Open/Closed periods are read-only.
- Published edits regenerate checkpoints for distinct configured frequencies only. Different roles with the same frequency share that period's schedule.
- Monthly/quarterly boundaries follow calendar months/quarters; partial first/last checkpoints are bounded by the configured measurement dates.
- Final checkpoint deadlines can extend beyond End Date; no checkpoints are generated beyond End Date.
- A PostgreSQL transaction advisory lock serialises configuration writes, preventing concurrent publish/update overlap races.
- Participant-backed periods cannot be deleted or reconfigured by these APIs: snapshots are not silently erased or replaced.

## Deliberately Deferred

- Manual closure prerequisites, incomplete/overdue assessment exceptions and any closure override.
- Automatic participant enrolment and other eligibility rules and handling transfers, joiners, resignations or changed superiors.
- Later-phase KPI assignments, KPI revisions, assessments, attitude scoring, annual results, appraisal and analytics.

The scheduler uses the server's existing default time zone, as does the inherited scheduler.
Offsets use calendar days, not a new holiday/business-day policy.
V28 adds the permission, V29 adds Employee Level configuration and V30 adds system-role eligibility and the single setup deadline using the isolated annual-kpi Flyway location.
V30 retains the four old deadline columns as historical evidence and backfills the single deadline from their latest value. It removes Super Admin configuration and only newly unused schedules; historical participants require manual review rather than deletion. Global Flyway baseline settings,
Hibernate ddl-auto settings and legacy endpoint security remain unchanged.

## Legacy Permission Boundaries

The old authority remains in the enum and RBAC data solely for inherited compatibility:

- `EvaluationCycleController`: legacy cycle creation/modification.
- `AppraisalRecordController`: legacy competency/cycle-based HR review actions.
- `OrgWideEvaluationController`: legacy competency/cycle-based statistics.
- Frontend evaluation routes, menus, cycle drawer/header and legacy HR appraisal controls.
- V16/V21 and the original `seed_superadmin.sql`: historical legacy setup, not edited.

These are not implementations of the refined annual KPI appraisal/analytics workflow. They must not
be granted the new annual-period permission by a global rename. New appraisal/analytics access
will be addressed when those phases are implemented.

The shared access-control UI discovers authorities dynamically. Its export includes a new
`Manage Annual KPI Review Period` column after the legacy column. Imports with that new column
manage the new grant explicitly; older templates without it preserve existing new grants.

## Verification

Run the focused suite in PowerShell:

```powershell
$env:APEX_PHASE1_POSTGRES_TEST = 'true'
.\mvnw.cmd '-Dtest=AnnualKpiReviewPeriodServiceImplTest,AnnualKpiReviewPeriodControllerTest,AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest,AnnualReviewPeriodPermissionPostgresTest,EmployeeLevelMigrationPostgresTest,RoleEmployeeLevelTest,ReviewPeriodRefinementMigrationPostgresTest,AuthorityControllerTest,AuthorityServiceTest,RoleServiceImplTest,RoleControllerTest,RoleCompetenciesControllerTest,RoleAuthorityServiceTest,EvaluationCycleServiceImplTest,EvaluationCycleControllerTest,TokenServiceTest' test
.\mvnw.cmd '-DskipTests' package
```

The PostgreSQL test executes V27/V29/V30 and service/repository scenarios in an isolated, transaction-rolled-back schema.
It does not replay legacy migrations, start Hibernate schema updates or retain test records in public.
The V28 PostgreSQL tests use rolled-back copies of inherited RBAC tables and check preserved legacy data,
new Super Admin-only grants, retry safety, enum constraints and rejection of ambiguous/missing Super Admin roles.
The V29 tests check example defaults, preserved historical/partial weights, same-period composite foreign keys,
protected referenced configuration and safe refusal of ambiguous historical classifications.

The V30 tests verify deadline backfill/history, Super Admin exclusion, shared checkpoint preservation and safe refusal of ambiguous participant or incomplete published deadline data.
