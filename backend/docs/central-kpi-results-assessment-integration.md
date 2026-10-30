# Central KPI Results - Individual Assessment Backend Integration

## Period boundary

New Annual Review Periods created through the application use `CENTRAL_RESULTS`.
Existing periods and their saved assessments remain `LEGACY_ALL_LEVELS`, with their
original all-level assessment and score calculation behavior. There is no conversion
API for an existing period. This backend checkpoint does not create UAT periods,
employees, plans, assignments, or central results.

## Assessment behavior

- In a central-model period, the employee and Superior answer only assigned
  Individual KPI items. Company and Department assignments still determine the
  preserved version binding, but are not answer fields.
- Individual submission and Superior completion do not wait for central results.
  Existing checkpoint opening, deadline, lateness, permissions, Draft flexibility,
  evidence and read-only behavior continue to apply.
- Responses expose `assessmentModel` and `pendingResultLevels`. A `null`
  `checkpointScore` means that a required central result or Individual review is
  pending; it is not a zero score.
- Once the Individual review and all non-zero allocated central results are
  published, the checkpoint score is calculated from Superior Individual points
  and published central points using the period's preserved allocation weights.
  Self points never contribute to the official score.
- Publishing a corrected central result recalculates affected reviewed
  checkpoints and appends a calculation-history entry. It does not reopen or
  change submitted Individual answers, evidence or Superior points.
- Started assessments retain their bound KPI versions. A late-confirmed level
  binds when first available; historical bindings do not switch to a later revision.

## Verification and next checkpoint

Focused unit tests cover the model split, submission without central results,
pending versus calculated scores, correction and zero-allocation behavior.
The opt-in PostgreSQL publication test exercises V46 constraints and real JPA
repositories in a rollback-only private schema. It verifies score changes from
pending to calculated to corrected while preserving assessment items and evidence.

Frontend integration and disposable UAT setup are separate follow-on work.
After frontend approval, use a new period in an isolated UAT database; do not
delete or convert earlier periods to make them appear on the central-results page.
