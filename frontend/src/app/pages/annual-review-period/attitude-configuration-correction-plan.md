# Attitude Configuration Coverage and Open-Period Correction

## Implementation Status - 9 November 2026

The controlled reassignment mechanism was subsequently approved and implemented.
The proposal below is retained as the reviewed design/history, not a claim that
the action is still pending. V56 was applied only to backed-up isolated UAT.
No existing assignments, assessments, scores, permissions or fixtures were
changed during deployment; no missing-Role mapping supplement was implemented.

The new action is in Attitude Evaluation Setup. Its read-only state and explicit
POST share `/api/attitude-configurations/periods/{id}/assignment`. It reuses Setup
authority, active-account checks, Published coverage checks and the existing
assessment/closure lock. It checks both configured Roles and the frozen roster,
any saved Attitude record, appraisals and direct/historical AI fixture references.
V56 adds an append-only audit table with request-UUID uniqueness, old/new edition
FKs, actor, time and reason. A rejected/failed audit rolls back the assignment.

Verified: 272 backend tests (including eight disposable-PostgreSQL checks), 165
focused frontend tests, both builds and spec type-check. Twenty matching parallel
requests produced one correction/audit and 19 read-only replays. Assessment-first,
correction-first and closure-first races passed. UAT smoke checks were read-only.
See `attitude-configuration-manual-uat.md` for the end-to-end stakeholder test guide.

## Findings and Implemented Slice

- Every participating performance-review Job Role currently requires Attitude
  Evaluation. There is no configured opt-out, including when its score weight is
  zero. Missing mappings block evaluation; they do not exempt the employee.
- Reusable Attitude configurations intentionally allow unmapped unrelated Roles
  at publication. Published editions remain immutable and reusable. Those rules
  have not been changed.
- `annual_kpi_review_period.attitude_configuration_id` already exists (V40).
  Drafts can store their explicit Published choice there without a migration.
  New publication now requires coverage for every participating Role, including
  applicable active criteria. Draft saves permit incomplete coverage or no choice.
- Both direct publication and saved-Draft publication validate on the server
  under the existing configuration lock. Preview validates without saving.
  The exact configuration is pinned through Upcoming and opening; publishing a
  newer reusable edition does not silently replace it.
- Existing Upcoming periods without a selection retain their legacy opening
  fallback. This slice does not repair, re-enrol or reassign historical periods.
  Upcoming configuration changes remain frozen; Open/Closed edits remain denied.
- A small annual-management read API lists Published configuration IDs, names,
  publication dates and covered Role IDs. It does not grant setup/edit authority
  or expose Drafts. Existing period DTO ID/name fields are reused.
- The frontend selects this choice, reports affected Job Roles, allows Draft
  saving, and blocks preview/publication when coverage is incomplete or unknown.
  Saved settings/preview also identify the selected configuration.
- Closure warnings with the same configuration cause share a heading/message,
  retaining every original member/context. Distinct causes are not combined.
  Role names come from saved participation and backend coverage, not employee
  names or guesses from message text. Counts and closure blockers are untouched.
- Setup links require the existing setup permission. They carry the selected
  period and assigned configuration; the authorised period response determines
  the actual edition to open, not a conflicting query value. There is no write,
  Draft copy, initial binding or automatic reassignment on navigation.
- Setup distinguishes Configuration Assigned from Role Coverage Complete. Its
  editor/history notice distinguishes a global Draft from the assigned edition.

## Read-Only UAT Check, 9 November 2026

The marker-guarded check of `apex_central_results_uat` found:

- Period 5: UAT Central Results 2025-2026, assigned configuration 2.
- Missing Roles reported by the existing authorised API: UAT Central MONTHLY,
  UAT Central QUARTERLY and UAT Central ANNUALLY.
- Zero persisted Attitude assessment records for this period, including Drafts.
- Schema at V55. No records, assignments, permissions or provider calls changed.

This is an observation, not permission to correct it. Recheck at the moment of
any future update because assessments may start after this inspection.

## Proposed Update Attitude Configuration Action, Not Implemented

### Readiness and Confirmation

Extend the existing authorised Attitude period context with a read-only
assignment-readiness response, rather than building a second setup workspace.
Return current assignment, Published choices with full Role coverage, status,
counts by assessment status, and affected assessment/participant/Role references.
Do not return comments or change assessment-viewing permissions.

Reuse `CAN_MANAGE_ATTITUDE_CONFIGURATION` and the existing active-account check,
which already govern initial assignment. Being HR or able to view a dashboard
alone is not authority. No new grants are proposed.

Offer Update only for Open periods, after checking all saved participating Roles
and the publication roster against the new Published edition. Do not infer an
exemption from a missing mapping or score weight. Closed periods always reject;
Draft selection continues through the ordinary form.

Confirmation names the period, old/new editions and the fact that this changes
only the period's Attitude assignment. Require a short correction reason. Send
the expected current configuration ID and target ID to detect stale decisions.

### Atomic Update When Nothing Has Started

In one transaction:

1. Acquire `periods.lockConfiguration()`, already shared by Attitude create/save/
   submit/review and closure. Recheck active permission, Open status and expected
   assignment after locking.
2. Require a Published target with applicable active criteria for all required
   Roles. Reject unverified/contradictory dependent source references, including
   any trusted AI fixture registry binding. Never silently rewrite fixture trust.
3. Check for **any persisted Attitude assessment in the period**. Draft,
   Pending Review and Reviewed all count as started; GET-only unsaved views do
   not. Reject if any exist and return the affected references/status counts.
4. If none exist, update only the period FK and existing updated-by/time fields,
   append the audit event, and return refreshed period/Attitude readiness.
5. Do not modify the roster, KPI plans/results, deadlines, weights, appraisals,
   scores or the old/new Published editions. Repeat-to-same-target requests must
   not create duplicate audit entries; conflicting stale requests reject.

The existing shared lock prevents first-assessment creation racing reassignment.
Stale unsaved assessment forms must be revalidated against the new criteria on
save, not silently transferred. Database foreign keys remain a final safeguard.

### Required Schema Addition, Proposal Only

Existing `updated_by`/`updated_at` retain only the latest modifier, not an audit
history. The smallest durable addition is one append-only assignment activity
table: event ID, period FK, previous configuration FK (nullable for initial
assignment if later unified), new configuration FK, actor FK, recorded timestamp
and correction reason. Index by period/event order. An operation UUID may provide
duplicate-request protection in that same table, without a second receipt table.

Use the next verified forward migration (currently V56), only after approval and
a fresh numbering/schema check. No existing migration, assessment FK or published
configuration should be edited. No migration has been created or applied here.

### When Assessments Already Exist

V41's composite FK binds each assessment to the period's exact configuration.
Items also reference that configuration and its criteria. Rebinding the period
would invalidate existing Drafts as well as submitted/completed results.

Do not delete, reset, copy or migrate those records automatically. Keep Update
blocked and show assessment ID, employee/Role context, status and original
configuration to the authorised administrator, without confidential answer text.

The smallest candidate for a separate correction design is a **period-scoped,
add-only missing-Role mapping supplement**, retaining the existing Published
configuration and all existing assessments/criteria. Only Roles with no saved
assessment could receive a previously missing mapping to an existing applicable
format. This would need its own explicit approval, audit, schema and assessment/
availability/closure tests; it is not part of reassignment or this implementation.
If criteria themselves are wrong, stop for a separate versioned correction
decision. Do not change historical scores or approved appraisal snapshots.

## Follow-Up Verification for the Proposed Action

Test permission denial, Draft/Closed rejection, Published/full-coverage checks,
all three assessment statuses, stale confirmations, duplicate/concurrent updates,
first-assessment races, rollback/audit atomicity, unsaved assessment rejection,
fixture/source dependencies, original versions, unchanged KPI activities and
readiness refresh. Use disposable PostgreSQL, not the live UAT database.

## Current Review Boundary

Publication validation and safe UI improvements only. No Open-period update
endpoint, reassignment UI, audit migration, database write, deployment, permission
grant, commit or push. Stop for manual review before the proposed correction work.

Verified implementation slice: 187 focused backend tests, 160 focused frontend
tests, isolated backend package, production-mode frontend build, spec type-check
and scoped whitespace check passed. Database mutation/concurrency checks for the
proposed action remain future work, not claimed as completed. Learning notes and
log locations are in the adjacent README.
