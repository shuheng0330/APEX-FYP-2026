# Manual UAT: New Review Period Coverage and Closure

## Before You Start

This guide reflects PLAN (51), deployed to isolated UAT with separate approval
on 10 November 2026. Use **http://127.0.0.1:4201/**. Save unsaved form entries
before reloading existing tabs. Do not use development port 4200 for UAT.

Use existing authorised accounts. Setup requires
`CAN_MANAGE_ATTITUDE_CONFIGURATION`; Review Period creation/closure requires the
existing Annual KPI Review Period management permission. A job title alone does
not grant either permission. Do not add permissions just to make a test pass.

Use only approved fictional/disposable records. Leave **UAT Central Results
2025-2026** and other existing Open/Closed periods unchanged. Open-period
reassignment is deferred, not part of the manual testing scope.

## A. New Review Period and Missing Formats

1. Open **KPI Administration > Annual KPI Review Periods > Create New Review
   Period**. Verify that the latest Published Attitude configuration is selected.
2. Enter a clearly disposable name and participating Job Roles. Select an older
   Published configuration that lacks formats for at least one required Role.
3. Verify the single missing-format status, actual missing Role/Department list
   and concise resolution guidance. **Save Draft** must work; **Preview &
   Publish** must remain blocked. Reopen the Draft and verify the selected
   configuration is retained.
4. Option A: choose an existing Published configuration covering all required
   Roles. Confirm **All Job Roles Covered**, then complete the other settings.
5. Option B: open **View Attitude Evaluation Setup**. The separate tab preserves
   parent-form inputs. A saved Draft shows compact period/configuration context
   and verified missing Roles, with no correction controls or period switcher.
6. If the selected configuration was changed without saving the period Draft,
   Setup must display that chosen Published edition as **Unsaved Selection**.
   It must not claim that coverage from another saved edition applies to it.
7. Select **Create Draft Copy**. Verify Draft status, the editable editor and
   focus moving to that editor. Assign formats for the missing Roles; keep valid
   criteria and all five rating definitions. Save and publish the copy.
8. Verify the publication reminder tells you to return, refresh and explicitly
   select the new Published version. A failed publication must not show this
   success reminder. Publishing must not change the period's saved selection.
9. Return to the original Review Period tab. **Refresh Configurations** must
   retain the current selection. Explicitly choose the new Published version
   and verify **All Job Roles Covered**. Save/reopen to verify persistence.
10. Complete the remaining required settings, preview and publish. The chosen
    Published version must stay pinned. A later publication must not replace it.

Reusable configuration publication still does not require mappings for every
unrelated Job Role. The Review Period's own coverage validation decides whether
that configuration is suitable for its participating Roles.

## B. Deferred Correction and Access Checks

- Update Attitude Configuration, Check Update Readiness and Assignment History
  must be absent, including when viewing an existing Open or Closed period.
- The page must not issue correction readiness or reassignment requests.
- Authorised correction API/service calls are rejected while the feature flag
  is missing or false. Automated tests cover this; no manual correction of an
  existing UAT record is needed.
- Unauthorised users remain denied Setup and protected APIs. New-tab navigation
  must retain authorised sessions and display the correct Published edition,
  never an unrelated global configuration Draft.
- Historical assignments, saved assessments, audit records and KPI activities
  remain unchanged. Do not test reassignment by enabling the flag in UAT.

## C. From Blocked to Closed

Use a fresh disposable period from flow A for a positive closure test. Select an
unused short interval whose End Date has already passed, subject to normal
overlap validation. Verify the preview and publication roster contain only the
intended fictional participants. Do not assume an old suggested date remains
available, alter existing period dates, or remove roster members to reduce work.

1. Open Details and select **Check Readiness**. Unfinished work must show **Not
   ready to close**, the three readiness counts and the complete outstanding
   task total, including rows beyond the first page.
2. On a separate ongoing disposable period, verify **Review period is still
   ongoing** and the earliest closure date. Do not change the system clock.
3. Check grouped configuration warnings, separate period requirements and
   employee tasks, and pagination at 10/20/50 rows. Paging must not change counts.
4. Complete/approve required KPI plans through their existing workflows. Publish
   required Company/Department results for the exact applicable checkpoint end
   dates. Employees submit Individual KPI Self-Assessments; authorised Superiors
   complete the reviews for every required checkpoint.
5. Complete each participating employee's Attitude Self-Assessment and Superior
   Attitude Evaluation. Include the entire publication roster.
6. **Check Readiness** again. All required results/configuration must be valid,
   every readiness count complete and no blockers unresolved. Appraisals do not
   need to be finalised before closure.
7. Select **Close Review Period**, cancel once and confirm the status stays Open.
   Then confirm closure. Server-side revalidation must succeed before Closed
   status and success feedback appear.
8. Refresh. Saved settings/results remain viewable but read-only. No reopening
   or correction action is introduced. Existing authorised appraisal actions can
   continue after closure; AI still requires its separate fictional-UAT trust.

If readiness checking fails, the UI must not treat it as Ready. If new blockers
appear before confirmation, the server must reject closure and refresh them.
Never bypass validation through database edits or substitute missing results.

## Evidence to Capture

Capture the disposable period and configuration versions, missing Roles, Draft
selection persistence, editable Draft Copy, publication reminder, explicit new
selection, publication block/success, complete readiness counts, confirmation
cancellation and final Closed state. Confirm existing UAT records are unchanged.
