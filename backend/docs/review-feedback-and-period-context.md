# Review feedback and Team Reviews period context

## Latest return feedback

V43 extends `kpi_plan` with the latest return reason, reviewer, time and a JSONB item
baseline. Department/Individual return actions capture it under the existing transaction
locks. Resubmission and approval retain it; the next return replaces it. This is a single
approval comparison, not UC-05 revision/versioning.

Existing plan detail responses add:

- `latestReturnReason`, `latestReturnedAt`, `latestReturnedBy`, `latestReturnedByName`.
- `returnComparisonAvailable` and `changesSinceReturn`.
- Each change has `type` (`ADDED`, `REMOVED`, `EDITED`), `itemId`, `itemName` and `fields`.
- Each field has `field`, `previousValue`, `currentValue`. Keys are item field names or
  `point1` through `point5`; values are nullable display strings.

Comparisons ignore item ordering, whitespace-only differences and equivalent decimal
scales. Items retain their existing IDs on normal edits. Added/removed IDs are reported
as additions/removals. Existing no-change resubmission validation remains unchanged.

The migration preserves still-available legacy return feedback without fabricating a
baseline. Feedback previously cleared by old resubmissions cannot be recovered. The UI
explains when a comparison is unavailable. Compatible nullable Hibernate-created columns
are adopted only after type/default checks; partial/incompatible structures fail safely.

## Team Reviews periods

`GET /api/team-reviews/periods` returns `{ "periods": [...], "defaultPeriodId": 1 }`.
Period entries reuse `KpiPeriodContextDto` (ID/name/status/start/end/setup deadline).
Access requires any existing Individual KPI, KPI Assessment or Attitude review authority
and an active non-deleted account. Options are published periods containing participants
whose live `Staff.manager` is the actor, including periods with no submitted work yet.
This uses the same current reporting relationship as review/assistance access; it does
not grant new access to employee records or reroute historical submissions.

Default: latest-starting Open period, otherwise latest End Date strictly before the
injected clock's current date, then latest Start Date/ID for ties. If neither exists,
`defaultPeriodId` is null. Draft periods are excluded. Empty coverage returns no options.

Team Reviews has one selector shared by all four tabs. Explicit selection is retained
in per-account session storage and validated against fresh scoped options. Closed periods
are read-only; an ended Open period remains actionable under existing late-work rules.
Assistance requests use the selected period's eligible employees. Unsaved assessment
answers require discard confirmation before changing periods; in-flight operations and
open assistance editors/request dialogs must finish first.

## Late employee submission

Both KPI and Attitude Self-Assessment confirmations use backend `overdue` and their
applicable Self deadline to show a warning and **Submit Late Assessment** action.
Normal confirmation, Draft flexibility, optional evidence/comments and submission-time
backend lateness calculation remain unchanged. Cancelling writes nothing. No late
penalty, hard lock or mandatory explanation is introduced.

## Verification

Focused tests cover latest-baseline replacement/preservation, comparisons, API permissions,
scoped lookup/default selection, rollback-only PostgreSQL migration/JPA round trips,
shared period filtering, assistance eligibility, unsaved review protection and late
confirmation/cancellation. Existing Phase 1-3 regressions and backend/frontend builds
remain required. Use the backed-up isolated runner through V43; never replay legacy
migrations or alter global Flyway/Hibernate settings.
