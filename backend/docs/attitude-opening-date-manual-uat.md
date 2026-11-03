# Attitude opening-date manual UAT

Prepared on 15 October 2026 in the existing isolated `apex_central_results_uat`
database. No database switch, reset, application rebuild, commit or push.

## Accounts and access

- `uat.central.setup@example.test`: existing Review Period administration plus
  `CAN_MANAGE_ATTITUDE_CONFIGURATION`, explicitly granted for this UAT exercise.
  Sign out and back in to refresh the navigation permissions.
- Employees: `uat.central.monthly@example.test`,
  `uat.central.quarterly@example.test`, `uat.central.annually@example.test`.
- Passwords are unchanged. No employee, MD or Superior permissions were added.
  Superior Attitude review still requires its existing review authority.

The grant is limited to the dedicated, unshared `UAT Central Setup` Role.
`tools/CentralKpiResultsUat.java enable-attitude-setup http://localhost:8081`
verifies the marked UAT database, takes a backup, grants only the existing setup
authority, and checks the original development-data fingerprints. Its backup
location and grant record are under `target/central-kpi-uat/attitude-setup-grant.json`.
This command does not create configurations or periods and is not loaded by Spring.

## Prepared test data

- Select **UAT Attitude Opening Ready**, period ID 7.
- Assigned published configuration: **UAT Attitude Evaluation - Test Roles**, ID 3.
- Copied the existing published configuration through the supported API, preserving
  its criteria, rating definitions and existing Role mappings. Added the three UAT
  employee Roles with the **Others / Non-Sales** format.
- Opening date: **16 October 2026**. Employee deadline: **20 October 2026**.
- Measurement dates: **1-30 September 2025**, intentionally using an unused range
  so no existing published period needs to be edited or closed. The period remains
  **Open**; an Attitude opening after the measurement End Date is supported.
- The period reuses the first test period's role selection, including the UAT MD
  and Superior Roles. Those two actors are not mapped for their own Attitude
  Self-Assessments in this exercise; the three employee Roles are mapped.
- Existing periods, configuration bindings, KPI Plans and assessments were not
  updated. In particular, **UAT Attitude Opening Test**, ID 6, remains bound to its
  original configuration and is not the period to use for this successful scenario.

## Employee checks

1. Sign in as a UAT employee and open **My Performance -> My Assessments**.
2. Select **UAT Attitude Opening Ready** and **Annual Attitude Self-Assessment**.
3. Before 16 October, expect the scheduled-opening notice and no editable form,
   Save Draft or Submit action. The Role-mapping error must not appear.
4. On or after 16 October, use **Refresh Assessment**. The form and scoring guide
   should become available without logout, provided the period remains Open.
5. Save incomplete answers as a Draft, reload, and verify persistence. Complete all
   required ratings and submit; expect read-only **Pending Review**.
6. Check another employee frequency: all use the same annual opening date.

Do not change the computer clock, rewrite Open-period dates, or replace preserved
configuration bindings to simulate opening. Do not submit answers merely to
prepare the fixture.

## Preparation verification

Authenticated GET checks confirmed that all three employees have `openingPending`
true, `availableFrom` 2026-10-16, no persisted Draft, and both write actions disabled.
The setup account's configuration-options request succeeds. The development
period/KPI/assessment fingerprints still match the pre-clone baseline. No manual
answers were saved or submitted during preparation. Calendar-boundary and write
enforcement tests were completed during the feature implementation.
