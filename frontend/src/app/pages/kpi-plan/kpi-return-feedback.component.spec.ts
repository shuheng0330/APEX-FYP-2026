import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { KpiReturnFeedbackComponent } from './kpi-return-feedback.component';
import { KpiPlan } from '../../models/kpi-plan.model';

describe('Latest KPI return feedback', () => {
  const plan: KpiPlan = { id: 1, reviewPeriodId: 1, reviewPeriodName: 'Annual Review', reviewPeriodStatus: 'OPEN',
    level: 'DEPARTMENT', status: 'PENDING_APPROVAL', items: [], kpiSetupDeadline: null, totalWeightage: 100,
    overdue: false, createdAt: '', updatedAt: '', latestReturnReason: 'Clarify targets', latestReturnedByName: 'Reviewer' };
  beforeEach(() => TestBed.configureTestingModule({ imports: [KpiReturnFeedbackComponent, TranslateModule.forRoot()] }));
  it('shows retained feedback after resubmission and explains legacy missing comparisons', () => {
    const fixture = TestBed.createComponent(KpiReturnFeedbackComponent); fixture.componentInstance.plan = plan; fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Clarify targets');
    expect(fixture.nativeElement.textContent).toContain('REVIEW_FEEDBACK.UNAVAILABLE');
  });
  it('shows added, removed and edited items with before and after values', () => {
    const fixture = TestBed.createComponent(KpiReturnFeedbackComponent);
    fixture.componentInstance.plan = { ...plan, returnComparisonAvailable: true, changesSinceReturn: [
      { type: 'ADDED', itemId: 1, itemName: 'New KPI', fields: [{ field: 'target', previousValue: null, currentValue: '100 units' }] },
      { type: 'REMOVED', itemId: 2, itemName: 'Old KPI', fields: [] },
      { type: 'EDITED', itemId: 3, itemName: 'Changed KPI', fields: [{ field: 'point5', previousValue: '100', currentValue: '200' }] }
    ] }; fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    for (const value of ['REVIEW_FEEDBACK.ADDED', 'REVIEW_FEEDBACK.REMOVED', 'REVIEW_FEEDBACK.EDITED', '100 units', '200']) expect(text).toContain(value);
    expect(fixture.nativeElement.querySelectorAll('details').length).toBe(3);
  });
  it('does not show return feedback for first submissions', () => {
    const fixture = TestBed.createComponent(KpiReturnFeedbackComponent);
    fixture.componentInstance.plan = { ...plan, latestReturnReason: null }; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });
});
