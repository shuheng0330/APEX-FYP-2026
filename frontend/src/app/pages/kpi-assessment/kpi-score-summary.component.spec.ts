import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { KpiScoreBreakdown } from '../../models/kpi-assessment.model';
import { breakdownLevelScore, KpiScoreSummaryComponent } from './kpi-score-summary.component';

describe('Shared KPI score summary', () => {
  const level = (changes: Partial<KpiScoreBreakdown['levels'][number]> = {}): KpiScoreBreakdown['levels'][number] => ({
    level: 'COMPANY', allocation: 15, versionNumber: 2, resultEditionNumber: 3, publishedAt: null,
    status: 'AVAILABLE', contribution: 6, items: [], ...changes
  });
  function create(levels: KpiScoreBreakdown['levels']) {
    TestBed.configureTestingModule({ imports: [KpiScoreSummaryComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { KPI_SCORE_BREAKDOWN: { STATUS: { PENDING: 'Pending', NOT_APPLICABLE: 'Not Applicable' } } }); translate.use('en');
    const fixture = TestBed.createComponent(KpiScoreSummaryComponent);
    fixture.componentRef.setInput('levels', levels); fixture.detectChanges(); return fixture;
  }
  it('keeps the four established columns and derives only the displayed unweighted score', () => {
    const levels = [level(), level({ level: 'DEPARTMENT', contribution: 9 }), level({ level: 'INDIVIDUAL', allocation: 70, contribution: 56 })];
    const before = structuredClone(levels); const fixture = create(levels);
    expect([...fixture.nativeElement.querySelectorAll('th')].map((n: HTMLElement) => n.textContent?.trim())).toEqual([
      'MY_ASSESSMENTS.LEVEL', 'KPI_SCORE_BREAKDOWN.ALLOCATION_LABEL', 'KPI_SCORE_BREAKDOWN.LEVEL_SCORE', 'KPI_SCORE_BREAKDOWN.CONTRIBUTION_LABEL'
    ]);
    expect([...fixture.nativeElement.querySelectorAll('tbody tr')].map((row: HTMLElement) =>
      [...row.querySelectorAll('td')].slice(1).map(n => n.textContent?.trim()))).toEqual([
      ['15%', '40.00%', '6.00%'], ['15%', '60.00%', '9.00%'], ['70%', '80.00%', '56.00%']
    ]);
    expect(levels).toEqual(before); fixture.destroy();
  });
  it('retains zeroes, Pending and Not Applicable without division by zero', () => {
    const fixture = create([level({ contribution: 0 }), level({ level: 'DEPARTMENT', contribution: null, status: 'PENDING' }),
      level({ level: 'INDIVIDUAL', allocation: 0, contribution: 0, status: 'NOT_APPLICABLE' })]);
    const rows: HTMLElement[] = [...fixture.nativeElement.querySelectorAll('tbody tr')];
    expect(rows[0].textContent).toContain('0.00%'); expect(rows[1].textContent).toContain('Pending');
    expect(rows[2].textContent).toContain('Not Applicable'); expect(rows[2].textContent).toContain('0.00%'); fixture.destroy();
  });
  it('does not round source values or create level scores where the source is unavailable', () => {
    const source = level({ contribution: 6.0009 });
    expect(breakdownLevelScore(source)).toBeCloseTo(40.006, 8); expect(source.contribution).toBe(6.0009);
    expect(breakdownLevelScore(level({ contribution: null }))).toBeNull();
    expect(breakdownLevelScore(level({ status: 'PENDING' }))).toBeNull();
    expect(breakdownLevelScore(level({ allocation: 0 }))).toBeNull();
  });
});
