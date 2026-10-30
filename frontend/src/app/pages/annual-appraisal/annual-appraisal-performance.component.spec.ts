import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AnnualAppraisalSnapshot } from '../../models/annual-appraisal.model';
import { AnnualAppraisalPerformanceComponent } from './annual-appraisal-performance.component';

describe('Annual appraisal performance presentation', () => {
  const snapshot = (): AnnualAppraisalSnapshot => ({ participantId: 1, reviewPeriodId: 2, employeeId: 'bob',
    reviewFrequency: 'MONTHLY', consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 100, attitudeWeight: 0,
    annualKpiScore: 0, attitudeScore: null, finalScore: null, attitude: null,
    checkpoints: [{ checkpointId: 5, sequenceNumber: 1, startDate: '2026-01-01', endDate: '2026-01-31',
      assessmentId: 9, status: 'REVIEWED', score: 0, reviewedAt: '2026-02-01T00:00:00Z', versions: [], breakdown: null }] });
  beforeEach(() => TestBed.configureTestingModule({ imports: [AnnualAppraisalPerformanceComponent, TranslateModule.forRoot()] }));
  it('distinguishes an actual zero score from missing results and starts details collapsed', () => {
    const fixture = TestBed.createComponent(AnnualAppraisalPerformanceComponent);
    TestBed.inject(TranslateService).setTranslation('en', { ANNUAL_APPRAISAL: { PENDING: 'Pending' } });
    TestBed.inject(TranslateService).use('en'); fixture.componentRef.setInput('performance', snapshot()); fixture.detectChanges();
    const values = [...fixture.nativeElement.querySelectorAll('.score-grid dd')].map((e: HTMLElement) => e.textContent?.trim());
    expect(values).toEqual(['0.00', 'Pending', 'Pending']); expect(fixture.nativeElement.querySelector('table')).toBeNull();
    fixture.nativeElement.querySelector('.text-action').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table').textContent).toContain('0.00');
    expect(fixture.nativeElement.querySelector('.text-action').getAttribute('aria-expanded')).toBe('true'); fixture.destroy();
  });
  it('resets expanded content for another snapshot and associates controls with unique targets', () => {
    const fixture = TestBed.createComponent(AnnualAppraisalPerformanceComponent);
    fixture.componentRef.setInput('performance', snapshot()); fixture.componentRef.setInput('contextId', 'history-10'); fixture.detectChanges();
    fixture.nativeElement.querySelector('.text-action').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#performance-history-10')).not.toBeNull();
    fixture.componentRef.setInput('performance', { ...snapshot(), finalScore: 20 }); fixture.detectChanges();
    expect(fixture.componentInstance.details).toBeFalse(); expect(fixture.nativeElement.querySelector('#performance-history-10')).toBeNull(); fixture.destroy();
  });
  it('shows frequency and consolidation as neutral reusable badges', () => {
    const fixture = TestBed.createComponent(AnnualAppraisalPerformanceComponent);
    fixture.componentRef.setInput('performance', snapshot()); fixture.detectChanges();
    const badges: HTMLElement[] = [...fixture.nativeElement.querySelectorAll('.performance-metadata .status')];
    expect(badges.length).toBe(2); expect(badges[0].textContent).toContain('MONTHLY');
    expect(badges[1].textContent).toContain('FINAL_CHECKPOINT');
    expect(badges.every(badge => !badge.hasAttribute('data-status'))).toBeTrue(); fixture.destroy();
  });
});
