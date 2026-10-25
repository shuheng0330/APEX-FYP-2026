import { calendarDate, editableReviewPeriod, pickerDate, weightTotal } from '../../models/annual-review-period.model';
import { reviewLevels, savedReview, validReviewRequest } from './review-period.fixtures.spec';
import { validateReviewConfiguration } from './review-period.validation';

describe('Annual review configuration validation', () => {
  it('allows partial Department Drafts but requires mapped schedules for publication', () => {
    const request = { ...validReviewRequest(), frequencyPolicy: 'DEPARTMENT_BASED' as const, departmentConfigurations: [] };
    const roles = savedReview().roleConfigurations;
    expect(validateReviewConfiguration(request, false, reviewLevels.map(l => l.id), roles)).toEqual([]);
    expect(validateReviewConfiguration(request, true, reviewLevels.map(l => l.id), roles).some(i => i.section === 'departments')).toBeTrue();
    expect(validateReviewConfiguration({ ...request, departmentConfigurations: [{ departmentId: 10, reviewFrequency: 'QUARTERLY' }] }, true, reviewLevels.map(l => l.id), roles)).toEqual([]);
  });
  it('rejects duplicate Department configuration even when saving a Draft', () => {
    const request = { ...validReviewRequest(), frequencyPolicy: 'DEPARTMENT_BASED' as const,
      departmentConfigurations: [1, 2].map(() => ({ departmentId: 10, reviewFrequency: 'MONTHLY' as const })) };
    expect(validateReviewConfiguration(request, false, [], savedReview().roleConfigurations).some(i => i.key.endsWith('DEPARTMENT_FREQUENCY'))).toBeTrue();
  });
  const validate = (request = validReviewRequest(), publish = true) => validateReviewConfiguration(request, publish, reviewLevels.map(l => l.id), savedReview().roleConfigurations);
  it('accepts the complete contract, including January final assessment deadlines', () => expect(validate()).toEqual([]));
  it('requires an Attitude opening date for publication while allowing incomplete Drafts', () => {
    const request = validReviewRequest(); request.attitudeSelfAssessmentStartDate = null;
    expect(validate(request, false)).toEqual([]);
    expect(validate(request)).toContain(jasmine.objectContaining({ section: 'attitude', field: 'attitudeSelfAssessmentStartDate', key: 'REVIEW_PERIOD.ERROR.REQUIRED' }));
  });
  it('validates supplied opening-date boundaries even before publication', () => {
    const request = validReviewRequest(); request.attitudeSelfAssessmentStartDate = '2027-12-31';
    expect(validate(request, false)).toContain(jasmine.objectContaining({ field: 'attitudeSelfAssessmentStartDate', key: 'REVIEW_PERIOD.ERROR.ATTITUDE_OPENING_BEFORE_START' }));
    request.attitudeSelfAssessmentStartDate = '2028-12-21';
    expect(validate(request, false)).toContain(jasmine.objectContaining({ field: 'attitudeSelfAssessmentStartDate', key: 'REVIEW_PERIOD.ERROR.ATTITUDE_OPENING_AFTER_DEADLINE' }));
  });
  it('allows opening on the Start Date, on the employee deadline, and after the measurement End Date', () => {
    const request = validReviewRequest(); request.attitudeSelfAssessmentStartDate = request.startDate;
    expect(validate(request)).toEqual([]);
    request.attitudeSelfAssessmentStartDate = request.attitudeSelfAssessmentDeadline;
    expect(validate(request)).toEqual([]);
    request.endDate = '2028-09-30'; request.attitudeSelfAssessmentStartDate = '2028-10-01';
    expect(validate(request)).toEqual([]);
  });
  it('permits incomplete Drafts and provisional totals', () => {
    const request = validReviewRequest(); request.name = null; request.startDate = null; request.endDate = null;
    request.employeeLevelConfigurations = []; request.roleConfigurations = []; request.kpiPerformanceWeight = 10;
    expect(validate(request, false)).toEqual([]);
    expect(validate(request, true).length).toBeGreaterThan(0);
  });
  it('validates each Employee Level independently of Performance Composition', () => {
    const request = validReviewRequest(); request.employeeLevelConfigurations![2].companyKpiWeight = 20;
    const issues = validate(request);
    expect(issues.some(i => i.field === 'total-3')).toBeTrue();
    expect(issues.some(i => i.section === 'composition')).toBeFalse();
  });
  it('rejects invalid weight precision even in a Draft', () => {
    const request = validReviewRequest(); request.employeeLevelConfigurations![0].companyKpiWeight = 15.001;
    expect(validate(request, false).some(i => i.key.endsWith('.WEIGHT'))).toBeTrue();
  });
  it('requires Employee Level classification only for publishing', () => {
    const roles = savedReview().roleConfigurations; roles[0].employeeLevelId = null;
    expect(validateReviewConfiguration(validReviewRequest(), true, reviewLevels.map(l => l.id), roles).some(i => i.key.endsWith('.ROLE_LEVEL'))).toBeTrue();
    expect(validateReviewConfiguration(validReviewRequest(), false, reviewLevels.map(l => l.id), roles)).toEqual([]);
  });
  it('points to the setup section when a setup deadline is after Start Date', () => {
    const request = validReviewRequest(); request.kpiSetupDeadline = '2028-01-15';
    expect(validate(request).some(i => i.section === 'setup' && i.field === 'kpiSetupDeadline')).toBeTrue();
  });
  it('rejects zero and fractional assessment day offsets', () => {
    const request = validReviewRequest(); request.selfAssessmentDaysAfterCheckpoint = 0; request.superiorAssessmentDaysAfterSelfDeadline = 1.5;
    expect(validate(request, false).filter(i => i.key.endsWith('.DAYS')).length).toBe(2);
  });
  it('requires final appraisal dates to allow the final Superior Assessment to finish', () => {
    const request = validReviewRequest(); request.appraisalRecommendationDeadline = '2029-01-09';
    expect(validate(request).some(i => i.key.endsWith('.APPRAISAL_DATE'))).toBeTrue();
  });
  it('uses local calendar dates without shifting the day to UTC', () => {
    expect(calendarDate(new Date(2028, 0, 1))).toBe('2028-01-01');
    expect(calendarDate(pickerDate('2028-02-29'))).toBe('2028-02-29');
  });
  it('allows only Draft and future Upcoming modification, never Open or Closed', () => {
    expect(editableReviewPeriod({ status: 'DRAFT', startDate: null })).toBeTrue();
    expect(editableReviewPeriod({ status: 'UPCOMING', startDate: '2028-01-01' }, '2027-12-31')).toBeTrue();
    expect(editableReviewPeriod({ status: 'UPCOMING', startDate: '2028-01-01' }, '2028-01-01')).toBeFalse();
    expect(editableReviewPeriod({ status: 'OPEN', startDate: '2028-01-01' })).toBeFalse();
    expect(editableReviewPeriod({ status: 'CLOSED', startDate: '2028-01-01' })).toBeFalse();
  });
  it('shows an incomplete total rather than treating missing weightages as zero', () => {
    expect(weightTotal(15, 25, null)).toBeNull(); expect(weightTotal(15, 25, 60)).toBe(100);
  });
});
