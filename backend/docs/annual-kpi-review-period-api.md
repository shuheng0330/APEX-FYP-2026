# Annual KPI Review Period Management

Scope: UC-01 / UC-02. No frontend, KPI, assessment, attitude scoring, result or appraisal implementation.

## Access and APIs

All routes require authentication and the existing `CAN_MANAGE_EVALUATION_CYCLE` permission.
The verified local database grants this permission to `superadmin`; this change does not grant permissions to other roles.

Base: `/api/annual-kpi-review-periods`

| Method | Route | Behaviour |
| --- | --- | --- |
| POST | base | Save an incomplete Draft; returns 201 |
| POST | base + `?publish=true` | Validate, create and publish; returns 201 |
| POST | `/{id}/publish` | Publish a saved Draft |
| PUT | `/{id}` | Replace editable configuration; Draft remains Draft, Upcoming remains published |
| GET | base | List periods, including configuration and checkpoints |
| GET | `/{id}` | Retrieve configuration and generated schedule |
| GET | `/roles` | Non-deleted roles with configured default frequency, or ANNUALLY |
| POST | `/preview` | Validate full configuration and return an unsaved configuration/schedule preview |
| DELETE | `/{id}` | Delete an editable Draft/Upcoming and its role/checkpoint configuration; returns 204 |

For editing previews, supply `?excludedPeriodId={id}` to exclude that editable period from name/overlap checks.
Preview results have no persisted ID, reference or checkpoint IDs.
Deletion confirmation belongs to the calling UI. There is no Close or Reopen endpoint.

## Configuration Request Example

Role ID is illustrative: select a real applicable role from `GET /roles`.

```json
{
  "name": "2027 Annual KPI Review",
  "startDate": "2027-01-01",
  "endDate": "2027-12-31",
  "roleConfigurations": [
    { "roleId": 1, "reviewFrequency": "MONTHLY" }
  ],
  "companyKpiWeight": 15,
  "departmentKpiWeight": 25,
  "individualKpiWeight": 60,
  "kpiPerformanceWeight": 50,
  "attitudeEvaluationWeight": 50,
  "annualKpiConsolidationMethod": "FINAL_CHECKPOINT",
  "companyKpiCreationDeadline": "2026-12-20",
  "departmentKpiCreationDeadline": "2026-12-23",
  "individualKpiSubmissionDeadline": "2026-12-27",
  "individualKpiApprovalDeadline": "2026-12-31",
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

## Rules

- Drafts can be incomplete, including provisional weight totals; supplied values must still be valid.
- Publishing requires name, dates, at least one unique non-deleted role, all deadline settings, all weights and a consolidation method.
- Names are trimmed, limited to 255 characters and unique using the inherited database's case-sensitive uniqueness convention.
- Start Date must precede End Date.
- Each of the four KPI setup deadlines must be on/before Start Date, following the detailed UC rather than the older January setup example.
- Weights are 0-100, with at most two decimal places. KPI-level weights total 100 and performance/attitude weights total 100.
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
- Automatic participant enrolment/eligibility and handling transfers, joiners, resignations or changed superiors.
- Later-phase KPI assignments, KPI revisions, assessments, attitude scoring, annual results, appraisal and analytics.

The scheduler uses the server's existing default time zone, as does the inherited scheduler.
Offsets use calendar days, not a new holiday/business-day policy.
No Flyway migration, global Flyway baseline setting, Hibernate ddl-auto setting or legacy workflow was changed.

## Verification

Run the focused suite in PowerShell:

```powershell
$env:APEX_PHASE1_POSTGRES_TEST = 'true'
.\mvnw.cmd '-Dtest=AnnualKpiReviewPeriodServiceImplTest,AnnualKpiReviewPeriodControllerTest,AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest,RoleServiceImplTest,RoleControllerTest,RoleAuthorityServiceTest,EvaluationCycleServiceImplTest' test
.\mvnw.cmd '-DskipTests' package
```

The PostgreSQL test executes the V27 schema and service/repository scenarios in an isolated, transaction-rolled-back schema.
It does not replay legacy migrations, start Hibernate schema updates or retain test records in public.
