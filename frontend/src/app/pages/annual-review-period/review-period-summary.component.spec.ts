import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { savedReview } from './review-period.fixtures.spec';
import { ReviewPeriodSummaryComponent } from './review-period-summary.component';

describe('Department schedule summary', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [ReviewPeriodSummaryComponent, TranslateModule.forRoot()] }));
  it('shows monthly Company reporting without implying monthly employee deadlines for quarterly-only Departments', () => {
    const fixture = TestBed.createComponent(ReviewPeriodSummaryComponent);
    const period = savedReview(); period.frequencyPolicy = 'DEPARTMENT_BASED';
    period.roleConfigurations[0].reviewFrequency = 'QUARTERLY';
    period.departmentConfigurations = [{ departmentId: 10, departmentName: 'Recorded Sales', reviewFrequency: 'QUARTERLY' }];
    fixture.componentRef.setInput('period', period); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Recorded Sales');
    expect(fixture.nativeElement.textContent).toContain('REVIEW_PERIOD.DEPARTMENT_SCHEDULE.COMPANY_MONTHLY');
    const lastTable = fixture.nativeElement.querySelectorAll('table')[3];
    expect(lastTable.querySelectorAll('th').length).toBe(2);
  });
  it('retains historical Role-based schedules and assessment deadlines', () => {
    const fixture = TestBed.createComponent(ReviewPeriodSummaryComponent);
    fixture.componentRef.setInput('period', savedReview()); fixture.detectChanges();
    expect(fixture.componentInstance.employeeSchedule).toBeTrue();
    expect(fixture.nativeElement.textContent).not.toContain('REVIEW_PERIOD.DEPARTMENT_SCHEDULE.COMPANY_MONTHLY');
  });
  it('keeps recorded dates grouped in preview with the same optional Deadline Guide', () => {
    const fixture = TestBed.createComponent(ReviewPeriodSummaryComponent);
    const period = savedReview(); const before = structuredClone(period);
    fixture.componentRef.setInput('period', period); fixture.detectChanges();
    const groups: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.schedule-subsection'));
    expect(groups[0].querySelectorAll('dd').length).toBe(3);
    expect(groups[1].querySelectorAll('dd').length).toBe(2);
    expect(groups[0].textContent).toContain('01 Oct 2028'); expect(groups[1].textContent).toContain('10 Jan 2029');
    const guide: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    expect(guide.open).toBeFalse(); guide.querySelector('summary')!.click(); fixture.detectChanges();
    expect(guide.querySelectorAll('ol').length).toBe(3); expect(period).toEqual(before);
  });
});
