import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { CompanyReportingSourceComponent } from './company-reporting-source.component';

describe('Company reporting source', () => {
  const source = { id: 3, reviewFrequency: 'MONTHLY' as const, startDate: '2028-03-01', endDate: '2028-03-31' };
  let api: jasmine.SpyObj<KpiAssessmentService>;
  beforeEach(() => {
    api = jasmine.createSpyObj('api', ['scoreBreakdown']);
    TestBed.configureTestingModule({ imports: [CompanyReportingSourceComponent, TranslateModule.forRoot()], providers: [
      { provide: KpiAssessmentService, useValue: api }
    ] });
  });
  it('displays the server-provided March source without guessing or fetching another result', () => {
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.componentRef.setInput('source', source); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('31 Mar 2028'); expect(api.scoreBreakdown).not.toHaveBeenCalled();
  });
  it('clears the previous source when the context no longer provides one', () => {
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.componentRef.setInput('source', source); fixture.detectChanges();
    fixture.componentRef.setInput('source', null); fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });
  it('does not fabricate source metadata for older responses', () => {
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(api.scoreBreakdown).not.toHaveBeenCalled();
  });
  it('shows YTD guidance only for an explicitly Department-based policy', () => {
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.componentRef.setInput('source', source); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('KPI_SCORE_BREAKDOWN.COMPANY_YTD');
    fixture.componentRef.setInput('departmentBased', true); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('KPI_SCORE_BREAKDOWN.COMPANY_YTD');
  });
  it('shows a compact reporting period without YTD guidance for the Superior drawer', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { KPI_SCORE_BREAKDOWN: { COMPANY_REPORTING_PERIOD: 'Company Reporting Period' },
      REVIEW_PERIOD: { FREQUENCY: { MONTHLY: 'Monthly' } } }); translate.use('en');
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.componentRef.setInput('source', { ...source, startDate: '2025-06-01', endDate: '2025-06-30' });
    fixture.componentRef.setInput('compact', true); fixture.componentRef.setInput('departmentBased', true); fixture.detectChanges();
    expect(fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim()).toBe('Company Reporting Period: 1–30 Jun 2025 (Monthly)');
    expect(fixture.nativeElement.textContent).not.toContain('KPI_SCORE_BREAKDOWN.COMPANY_YTD');
  });
  it('keeps both months and years when a compact reporting period crosses a month boundary', () => {
    const fixture = TestBed.createComponent(CompanyReportingSourceComponent);
    fixture.componentRef.setInput('compact', true);
    fixture.componentRef.setInput('source', { ...source, startDate: '2027-12-01', endDate: '2028-01-31' }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('1 Dec 2027–31 Jan 2028');
    fixture.componentRef.setInput('source', null); fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });
});
