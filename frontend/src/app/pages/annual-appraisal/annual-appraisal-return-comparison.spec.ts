import { AnnualAppraisal, AnnualAppraisalActivity } from '../../models/annual-appraisal.model';
import { appraisal } from './annual-appraisal-test-fixtures';
import { appraisalReturnComparison } from './annual-appraisal-return-comparison';

describe('Appraisal changes since the last HR return', () => {
  function event(id: number, action: AnnualAppraisalActivity['action'], values: Partial<AnnualAppraisalActivity> = {}): AnnualAppraisalActivity {
    return { id, action, actorId: 'actor', occurredAt: '2026-10-20T10:00:00Z', reason: null,
      recommendation: 'SALARY_INCREMENT', justification: 'Original justification', finalDecision: null, snapshot: null, ...values };
  }
  function resubmitted(values: Partial<AnnualAppraisal> = {}): AnnualAppraisal {
    return appraisal({ status: 'PENDING_REVIEW', recommendation: 'PROMOTION', justification: 'Updated justification',
      latestReturnReason: 'Do not use this field as history', activity: [
        event(1, 'SUBMITTED'), event(2, 'RETURNED', { reason: 'Explain the recommendation' }),
        event(3, 'DRAFT_SAVED', { recommendation: 'BOTH', justification: 'An unfinished edit' }),
        event(4, 'RESUBMITTED', { recommendation: 'PROMOTION', justification: 'Updated justification' })
      ], ...values });
  }
  it('does not infer resubmission from Drafts or the current return-reason field', () => {
    expect(appraisalReturnComparison(null)).toBeNull();
    for (const status of ['DRAFT', 'RETURNED', 'APPROVED'] as const) {
      expect(appraisalReturnComparison(resubmitted({ status }))).toBeNull();
    }
    expect(appraisalReturnComparison(resubmitted({ activity: [event(1, 'SUBMITTED')] }))).toBeNull();
    expect(appraisalReturnComparison(resubmitted({ activity: [] }))).toBeNull();
  });
  for (const changes of ['recommendation', 'justification', 'both', 'neither']) {
    it(`identifies ${changes} changes from recorded return and resubmission values`, () => {
      const value = resubmitted();
      if (changes === 'justification' || changes === 'neither') value.recommendation = 'SALARY_INCREMENT';
      if (changes === 'recommendation' || changes === 'neither') value.justification = 'Original justification';
      value.activity[3] = { ...value.activity[3], recommendation: value.recommendation, justification: value.justification };
      const comparison = appraisalReturnComparison(value);
      expect(comparison?.available).toBeTrue();
      if (!comparison?.available) return;
      expect(comparison.reason).toBe('Explain the recommendation');
      expect(comparison.before).toEqual({ recommendation: 'SALARY_INCREMENT', justification: 'Original justification' });
      expect(comparison.after).toEqual({ recommendation: value.recommendation!, justification: value.justification! });
      expect(comparison.recommendationChanged).toBe(changes === 'recommendation' || changes === 'both');
      expect(comparison.justificationChanged).toBe(changes === 'justification' || changes === 'both');
    });
  }
  it('uses only the latest cycle in ID order despite Draft saves, identical or misleading timestamps and array order', () => {
    const value = resubmitted();
    value.activity.push(event(5, 'RETURNED', { reason: 'Latest HR reason', recommendation: 'BOTH', justification: 'Latest returned text', occurredAt: '2099-01-01' }),
      event(6, 'DRAFT_SAVED', { justification: 'Do not use this Draft' }),
      event(7, 'RESUBMITTED', { recommendation: value.recommendation, justification: value.justification, occurredAt: '2000-01-01' }));
    value.activity.reverse(); const recorded = structuredClone(value);
    Object.freeze(value.activity); value.activity.forEach(Object.freeze);
    const comparison = appraisalReturnComparison(value);
    expect(comparison?.available).toBeTrue();
    if (!comparison?.available) return;
    expect(comparison.reason).toBe('Latest HR reason'); expect(comparison.before.justification).toBe('Latest returned text');
    expect(comparison.before.recommendation).toBe('BOTH'); expect(value).toEqual(recorded);
  });
  for (const missing of ['return', 'reason', 'before recommendation', 'before justification', 'after recommendation', 'after justification']) {
    it(`reports unavailable ${missing} data without substituting other saved values`, () => {
      const value = resubmitted();
      if (missing === 'return') value.activity.splice(1, 1);
      if (missing === 'reason') value.activity[1].reason = '  ';
      if (missing === 'before recommendation') value.activity[1].recommendation = null;
      if (missing === 'before justification') value.activity[1].justification = null;
      if (missing === 'after recommendation') value.activity[3].recommendation = null;
      if (missing === 'after justification') value.activity[3].justification = null;
      expect(appraisalReturnComparison(value)).toEqual({ available: false });
    });
  }
  it('never falls back to an older complete cycle when the latest return is incomplete', () => {
    const value = resubmitted();
    value.activity.push(event(5, 'RETURNED', { reason: null }),
      event(6, 'RESUBMITTED', { recommendation: value.recommendation, justification: value.justification }));
    expect(appraisalReturnComparison(value)).toEqual({ available: false });
  });
  it('requires the latest submitted values to match the current record exactly', () => {
    expect(appraisalReturnComparison(resubmitted({ recommendation: 'BOTH' }))).toEqual({ available: false });
    expect(appraisalReturnComparison(resubmitted({ justification: 'Another current edit' }))).toEqual({ available: false });
  });
  it('preserves complete text and line breaks rather than normalising away changes', () => {
    const value = resubmitted({ justification: 'Original justification\n' });
    value.activity[3].justification = value.justification;
    const comparison = appraisalReturnComparison(value);
    expect(comparison?.available).toBeTrue();
    if (comparison?.available) { expect(comparison.justificationChanged).toBeTrue(); expect(comparison.after.justification).toBe('Original justification\n'); }
  });
  it('rejects ambiguous event IDs or a transition inconsistent with the pending resubmission', () => {
    for (const id of [0, NaN, 2, 1.5]) {
      const value = resubmitted(); value.activity[3].id = id;
      expect(appraisalReturnComparison(value)).toEqual({ available: false });
    }
    const value = resubmitted(); value.activity.push(event(5, 'RETURNED', { reason: 'Not resubmitted yet' }));
    expect(appraisalReturnComparison(value)).toEqual({ available: false });
  });
  it('does not compare a later initial submission against an older resubmission', () => {
    const value = resubmitted(); value.activity.push(event(5, 'SUBMITTED'));
    expect(appraisalReturnComparison(value)).toBeNull();
  });
});
