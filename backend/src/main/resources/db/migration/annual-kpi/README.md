# Annual KPI Foundation And KPI Plans

V27 adds only the four annual-review foundation tables and nullable role review-frequency default.
V28 adds the dedicated annual KPI review period authority and grants it to the active `superadmin` role.
It retains legacy authority records/grants and extends the inherited authority enum CHECK constraint.
V29 adds six Employee Levels, configurable Role classification and independent per-period level weights.
Old global weight columns remain as migration evidence but are no longer mapped or used at runtime.
V30 marks Super Admin as ineligible for performance reviews and replaces four active setup deadlines with one.
The old deadline columns remain as history; the single deadline is backfilled from their latest date.
V31 introduces whole KPI plans, their items, 1-5 scoring definitions and materialised assignments.
V32 adds the annual publication-time participant snapshot marker and Company plan publication metadata.
V33 adds Department plan submission/review metadata, return reasons, validation constraints and two business authorities.
V34 adds Individual plan submission routing to the immediate Superior, review constraints and a reviewer authority.
V35 requires a saved content change after every return of a Department/Individual plan or legacy appraisal.
V36 adds scoped HR authorisation and direct confirmation for Superior-assisted Individual plans.
V37 adds HR rejection with a required reason and permits fresh eligible requests while retaining rejected history.
The application's existing `ddl-auto: update` and `flyway.enabled: false` remain unchanged.

Do not start the application with the new entities before applying V27-V37: Hibernate may otherwise
create untracked tables without the migration's constraints. Do not enable the legacy Flyway location.

## Local migration

Run from the backend directory with Java 17 or newer and the project's `.env`:

```powershell
.\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/phase1-classpath.txt'
$classpath = (Get-Content -LiteralPath target/phase1-classpath.txt -Raw).Trim()
java --class-path $classpath ApplyAnnualReviewPeriodFoundation.java --apply 'C:\Program Files\PostgreSQL\18\bin\pg_dump.exe'
```

The runner checks the inherited IDs, migration history and absence of partial Phase 1 structures,
backs up the local database under the user's `.apex/database-backups` directory, explicitly records the inherited schema as baseline 26
when history is empty, and discovers **only** this directory's V27-V37. Baseline 26 does not claim that
V2-V26 were executed. Preserve applied migration files and the generated backup.
Already-applied migrations are validated, not replayed. A backup is required before any pending migration.
V30 refuses existing Super Admin participants or missing/invalid published setup deadline backfills rather than altering historical data.
It can reconcile a verified nullable DATE setup-deadline column already created by development Hibernate,
preserving any saved value. Incompatible or other partial untracked structures still stop the rollout.
V28 refuses missing/ambiguous active Super Admin roles instead of guessing permission recipients.
V29 copies actual old global weights into all six period-level rows, including incomplete Draft values.
It leaves inherited Role classifications unset. It refuses existing participants or published Role configuration
because their historical level associations need an explicit, reviewed mapping. Do not guess using Role names
or current Role assignments; resolve that backfill in a separately reviewed rollout before applying V29 there.
Flyway cannot baseline an existing empty history table. After backup, the runner locks and
reinitialises only that zero-row metadata table. It never removes a nonempty migration history.

## Verification

```powershell
$env:APEX_PHASE1_POSTGRES_TEST = 'true'
$env:APEX_PHASE2_POSTGRES_TEST = 'true'
.\mvnw.cmd '-Dtest=AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest,AnnualReviewPeriodPermissionPostgresTest,EmployeeLevelMigrationPostgresTest,ReviewPeriodRefinementMigrationPostgresTest' test
```

The PostgreSQL test runs the migration and JPA repositories in a temporary transactional schema,
then rolls back. It does not start Spring or run legacy migrations. Normal test runs skip this opt-in test.
Run `KpiPlanPostgresTest` for the Company plan round-trip, real participant cascade, duplicate-assignment
and cross-period foreign-key checks. Both opt-in test schemas are rolled back without changing live records.

## Slice 2 Rollout

- Annual publication (direct create-and-publish or saved-Draft publication) enrols the initial active,
  non-deleted staff roster for the selected performance-review-eligible Roles. Super Admin is excluded
  through Role eligibility, not Employee Level. Preview, Draft saving and automatic opening never re-enrol.
- Existing published periods intentionally keep a NULL snapshot marker. Do not infer historical rosters.
  Use a fresh annual period for UAT; historical reconciliation needs explicit business review.
- Published Upcoming periods permit only name/date/deadline changes. Participant and configuration IDs,
  frequencies, allocations, composition and consolidation remain frozen. Open/Closed periods are read-only.
- A complete Company plan totals 100% within its level, independent of Employee-Level allocations.
  `POST /api/company-kpi-plans/{id}/publish` publishes and assigns all items atomically to existing participants.
  A passed setup deadline is not a hard lock. Action-time publication lateness is preserved separately.
- `CAN_MANAGE_COMPANY_KPI` must be granted through existing RBAC to a verified MD/Top Management Role.
  The migration does not guess recipients or grant it to Super Admin.
- Slice 2 stops at V32. Slice 3 adds Department workflow APIs only; Individual approval and assistance are not exposed yet.

## Slice 3 Backend

- The inherited Job Role organisation node determines an HOD's Department, directly or through its
  nearest unambiguous Department ancestor. `CAN_MANAGE_DEPARTMENT_KPI` must additionally be explicitly
  granted to a verified HOD Role. Neither Employee Level nor a Manager title grants business authority.
- `CAN_APPROVE_DEPARTMENT_KPI` is granted explicitly through the existing access-control UI.
  Reviewer access follows this permission, not the account's Job Role name or performance-review eligibility.
  No Role, including Super Admin, receives the permission automatically.
- All routes are under `/api/department-kpi-plans`:
  `GET /`, `/periods`, `/departments`, `/{id}`; `POST /`; `PUT /{id}`;
  `POST /{id}/submit`; reviewer-only `GET /pending`, `POST /{id}/approve`, `POST /{id}/return`.
  Return accepts `{ "reason": "Clarify the target" }`.
- HOD reads/edits are restricted to their resolved Department and require an eligible business Role.
  Any active, non-deleted account with the review permission can read and decide Department plans.
- Draft/Returned plans can be incomplete. Submission requires complete KPI items, five scoring criteria
  per item and an exact 100% within-level total. Pending and Approved contents are immutable.
- Approval rechecks completeness and requires an Upcoming/Open period with a confirmed participant roster.
  It atomically assigns every item only to matching snapshotted Department participants; no enrolment occurs.
- Return requires a nonblank reason, retained during revision. Resubmission clears the previous decision
  and records the latest submission. No separate decision-history or workflow-engine table is introduced.
- Late setup, submission and review are allowed while the period is not Closed. Submission/review lateness
  is saved at action time, independent of current overdue indicators and later deadline changes.
- Shared transaction and plan locks serialize competing writes and prevent duplicate approval/cascade.
  V33 is forward-only, preserves existing Company plans and grants, and does not change Hibernate/Flyway settings.
  It can reconcile all seven verified nullable review columns created by development Hibernate; partial
  or incompatible columns are refused. The migration adds the required foreign keys and checks after backup.
- Department frontend is implemented; Slice 4 adds backend functionality only and stops before its frontend.

## Slice 4 Backend

- Employees manage one Individual KPI plan per Annual Review Period in which they are enrolled.
  Draft/Returned plans are editable, including after the target KPI Setup Deadline while the period remains writable.
- Submission requires a complete plan with five scoring definitions per item and exactly 100% within-level weightage.
  It routes to the employee's active immediate Superior from the inherited Staff manager relationship.
- The routed Superior also needs `CAN_REVIEW_INDIVIDUAL_KPI`, assigned through existing RBAC. This migration
  does not guess which Roles should receive it. Reviewers may approve or return the whole plan; return requires a reason.
- Approval atomically assigns all Individual KPI items only to the plan owner's existing Review Period participant.
  Neither submission nor approval creates participants. Pending/Approved plans cannot be edited.
- Review checks both the stored submission route and the current reporting relationship. Automatic rerouting after
  a Manager change, assisted creation and KPI revision remain outside Slice 4.
- V34 is forward-only and refuses pre-existing non-Draft Individual plans with unknown routing. It can reconcile
  a verified nullable UUID routing column created by development Hibernate, then adds the foreign key and checks.
  Existing Company and Department plan data and permissions are preserved.

## Slice 4 Frontend

- My Performance > My KPI Plan lets an enrolled employee save Individual items using Apply to Plan,
  submit the complete plan, and view assigned Company/Department plans separately. Each level retains
  its own internal 100% total; Employee Level allocations are not mixed into that progress indicator.
- Team Performance > Team Reviews supports Individual KPI approval only. Its status filters include
  Pending Approval, Approved and Returned; the full read-only plan drawer uses the shared KPI editor.
- `GET /api/individual-kpi-plans/reviews` provides this review listing. It requires the Individual review
  permission, the stored submission route and the existing current Staff manager relationship.
  Individual plan detail access applies the same reviewer scope, and the response shows the participant's
  snapshotted Department label. No migration or new approval workflow is required for these integration changes.
- Assessment, attitude and assisted-creation interfaces remain deferred. Slice 4 stops before Slice 5.

## Returned Submission Validation

- Department and Individual KPI plans require at least one saved content change after each Return for Revision.
  The same reusable guard covers the inherited Manager-to-HR appraisal flow; no new appraisal workflow is introduced.
- V35 stores a response-only `revisionRequired` flag on each affected record. Return sets it; a meaningful
  save clears it. This remains effective after reloads and legacy appraisal Save Draft transitions.
- Unchanged saves, timestamps, item ID replacement, item reordering, outer whitespace and decimal scale
  do not count. Changing KPI information, weights or scoring criteria, adding/removing items, or changing
  editable appraisal content does count. All existing completeness and 100% checks still apply.
- First-time/ordinary Draft submission and Company publication are unchanged. Backend validation is
  authoritative; the frontend disables unchanged resubmission and shows a short explanation.
- Existing returned records require a fresh change. Legacy Draft appraisals with an HR return reason are
  conservatively marked the same way because historical content edits cannot be verified. Normal Drafts
  remain unaffected. V35 grants no permissions and changes no already-applied migrations.
- Opt-in `SubmissionRevisionMigrationPostgresTest` checks empty/populated backfills and constraints;
  `KpiPlanPostgresTest` verifies saved revision state and return/edit/resubmit across JPA reloads.
  Slice 5 uses V36; V35 is retained unchanged.

## Slice 5 Backend: Superior-Assisted Individual KPI Plans

- One case identifies an enrolled employee, annual period (through their participant) and requesting
  immediate Superior. Its state is `REQUESTED -> AUTHORIZED -> CONSUMED` or `REQUESTED -> REJECTED`.
  V37's partial unique index permits only one non-rejected case per Superior/participant while preserving
  rejected requests and permitting a fresh eligible request. Consent is not a global employee-access grant.
- Existing `CAN_REVIEW_INDIVIDUAL_KPI` enables the Superior assistance workspace. New
  `CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE` enables the HR authorisation queue/action. Grant it to
  verified HR Roles through existing RBAC; V36 assigns it to no Role and checks no hardcoded Role names.
- Request, HR authorisation, assisted creation, updates and confirmation recheck active/non-deleted
  accounts and the inherited live Staff manager relationship. Closed periods are read-only. Participants
  must already belong to a published Upcoming/Open period; no enrolment or participant refresh occurs.
- The requesting Superior creates/updates an incomplete Draft only after HR authorisation. Confirmation
  requires the existing complete five-point scoring criteria and exactly 100% within-level item weightage.
  It directly sets `APPROVED`, records the Superior's confirmation in existing review metadata, consumes
  the case and assigns every item only to its owner atomically. No employee submission or extra approval
  is fabricated. Confirmation lateness uses the existing setup deadline and review lateness field.
- The target and period are derived from the saved case, not plan-request employee IDs. A composite FK
  enforces that the plan belongs to the case's participant and requesting Superior; the existing participant
  FK enforces the period. Unique plan/case and employee/period constraints prevent reuse and duplicates.
- An existing employee plan cannot be taken over, replaced or merged. Owner normal edit/submit endpoints
  cannot bypass an assisted Draft. Reporting-line changes stop assistance actions rather than silently
  rerouting or revoking consent; those additional policies remain TBC.
- API base: `/api/individual-kpi-assistance`:

| Method / Path | Purpose / Authority |
|---|---|
| `GET /employees` | Current enrolled subordinate options; Individual review authority |
| `GET /` / `GET /{id}` | Scoped Superior cases or HR-wide case summaries |
| `POST /` | Request consent with `{ "ownerParticipantId": 7 }`; Superior |
| `POST /{id}/authorize` | HR pre-creation authorisation; assistance HR authority |
| `POST /{id}/reject` | HR rejection with `{ "reason": "..." }`; same HR authority |
| `GET /{id}/plan` | Read the case's existing plan; requesting Superior |
| `POST /{id}/plan` | Create Draft with `{ "items": [...] }`; authorised requesting Superior |
| `PUT /{id}/plan` | Update that Draft with `{ "items": [...] }`; same Superior and case |
| `POST /{id}/confirm` | Complete directly to Approved and consume consent; same Superior and case |

- HR-only authorisation access does not grant KPI content editing or final confirmation. An employee
  continues to view their plan/assigned items through existing Individual endpoints. Company, Department
  and normal Individual submission/return/resubmission remain unchanged.
- `IndividualKpiAssistanceServiceTest` and controller tests cover consent, subordinate/authority boundaries,
  changed reporting lines, incomplete plans, no takeover, direct confirmation and consumed-case reuse.
  Opt-in `IndividualKpiAssistanceMigrationPostgresTest` checks empty/populated migration preservation,
  unchanged Role grants and safe refusals. `KpiPlanPostgresTest` includes real case/plan reloads, scope FKs,
  owner-only assignments and confirmation/case rollback on cascade failure in its rollback-only schema.
- V37 records the rejecting HR user, decision time and trimmed nonblank reason (maximum 10,000 characters).
  Only Pending requests permit either HR decision; transaction locks serialize competing decisions. Rejected
  cases cannot authorise plan creation. A new request rechecks current subordinate, participant, period,
  existing plan and active-case eligibility without resetting the historical rejection.
- Frontend reuses Team Reviews (KPI Assistance), KPI Review (Assistance Requests) and My KPI Plan.
  Request labels are Pending/Approved/Rejected/Completed, separate from the plan's Draft/Approved status.
  Employees may view but cannot edit assisted Drafts. HR-only access does not grant Department review or
  Superior creation actions. Apply to Plan saves the working collection; Confirm finalises without another approval.
- Focused Angular tests cover HR decisions, permission combinations, new requests after rejection, Draft
  persistence and employee read-only views. `KpiAssistanceRejectionMigrationPostgresTest` and the extended
  JPA round-trip test verify historical preservation, constraints and fresh request IDs.
- Cancellation, expiry, revocation, takeover policies, UC-05 revision, participant refresh and later
  assessments/attitude/appraisals/analytics remain unimplemented.

## Deliberate boundaries

- Review Period management and Company plan Draft/publication REST/UI workflows are implemented.
- Partial drafts are stored; published configurations require complete dates, scoring settings and valid totals.
- Role frequencies and Employee Level configuration references are annual snapshots; participant role/department/superior labels remain directly snapshotted.
- Date-only deadlines have no invented time-of-day or holiday rules; generated offsets use calendar days.
- No automatic closure, additional eligibility inference, participant refresh or transfer/resignation handling is added.
- KPI revisions and later assessment, attitude, appraisal and analytics workflows remain outside these slices.
- The consolidation method is stored but no annual scores are calculated in Phase 1.
- Publication and overlap rules are enforced by the Phase 1 application service; closure prerequisites remain unresolved.
