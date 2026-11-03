import { canResubmit } from './submission-revision.model';
import { AppraisalRecordDto, appraisalEditableContent } from './appraisal-record.model';

describe('Submission revision validation', () => {
  it('does not block first submissions or ordinary Drafts', () => {
    expect(canResubmit(undefined)).toBeTrue();
    expect(canResubmit({ revisionRequired: false })).toBeTrue();
  });
  it('requires a content change while a revision remains outstanding', () => {
    expect(canResubmit({ revisionRequired: true })).toBeFalse();
    expect(canResubmit({ revisionRequired: true }, true)).toBeTrue();
  });
  it('ignores appraisal metadata, outer whitespace and recalculated scores', () => {
    const record: AppraisalRecordDto = { staffId: 'employee', evaluationCycleId: 1, reviewPeriodYears: 1,
      decisionType: 'PROMOTION', managerComment: 'Clarified recommendation',
      promotionSystemCategory: 'READY', promotionManagerCategory: 'READY' };
    expect(appraisalEditableContent({ ...record, managerComment: ' Clarified recommendation ', updatedAt: 'later',
      promotionReadinessScore: 90, promotionSystemCategory: 'BORDERLINE', promotionManagerCategory: 'BORDERLINE' }))
      .toEqual(appraisalEditableContent(record));
    expect(appraisalEditableContent({ ...record, managerComment: 'A different recommendation' }))
      .not.toEqual(appraisalEditableContent(record));
  });
});
