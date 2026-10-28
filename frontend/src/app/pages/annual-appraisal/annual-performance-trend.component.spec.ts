import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AnnualPerformanceTrendComponent } from './annual-performance-trend.component';
import { AnnualAppraisalSnapshot, AnnualPerformanceHistory } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';

describe('Annual performance trend', () => {
  function snapshot(score: number | null = 80): AnnualAppraisalSnapshot {
    return { participantId: 6, reviewPeriodId: 6, employeeId: 'bob', reviewFrequency: 'QUARTERLY', consolidationMethod: 'AVERAGE',
      kpiWeight: 70, attitudeWeight: 30, annualKpiScore: score, attitudeScore: score, finalScore: score, attitude: null,
      checkpoints: [{ checkpointId: 6, sequenceNumber: 1, startDate: '2025-01-01', endDate: '2025-12-31',
        assessmentId: 6, status: 'REVIEWED', score, reviewedAt: null, versions: [], breakdown: null }] };
  }
  function history(id: number, year: number, score = 80): AnnualPerformanceHistory {
    return { employeeId: 'bob', participantId: id, reviewPeriodId: id, periodName: '[UAT SIMULATED] ' + year,
      startDate: year + '-01-01', endDate: year + '-12-31', annualKpiScore: score, attitudeScore: score, finalScore: score,
      kpiWeight: 50, attitudeWeight: 50, consolidationMethod: 'AVERAGE', sourceType: 'CALCULATED_RESULTS', comparisonStatus: 'NOT_APPLICABLE' };
  }
  const period: KpiPeriodContext = { id: 6, name: '2026 Annual Review', startDate: '2026-01-01', endDate: '2026-12-31',
    status: 'OPEN', kpiSetupDeadline: null };
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AnnualPerformanceTrendComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { ANNUAL_TREND: { CURRENT: 'Current-period results', HISTORICAL: 'Historical performance results',
      SIMULATED: 'Simulated UAT data', COMPOSITION: '{{kpi}}% KPI + {{attitude}}% Attitude',
      UPDATED: 'Updated results available; showing recorded scores.', RECORDED: 'Showing recorded scores; current results are unavailable.' } });
    translate.use('en');
  });
  function create() {
    const fixture = TestBed.createComponent(AnnualPerformanceTrendComponent);
    fixture.componentRef.setInput('current', snapshot()); fixture.componentRef.setInput('currentPeriod', period);
    fixture.componentRef.setInput('employeeId', 'bob');
    return fixture;
  }
  it('reuses current-result inputs with independent partial scores and no submission-snapshot precedence', () => {
    const fixture = create();
    fixture.componentRef.setInput('results', [{ employeeId: 'bob', periodId: 6, periodName: period.name!,
      startDate: period.startDate!, endDate: period.endDate!, annualKpiScore: 0, attitudeScore: null, finalScore: null,
      kpiWeight: 70, attitudeWeight: 30 }]);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.points.length).toBe(1); expect(page.data.datasets[0].data).toEqual([0]);
    expect(page.data.datasets[1].data).toEqual([null]); expect(page.data.datasets[2].data).toEqual([null]);
    expect(page.tooltipFooter(0).join(' ')).not.toContain('showing recorded scores');
    expect(page.tooltipFooter(0).join(' ')).toContain('ANNUAL_APPRAISAL.PENDING'); fixture.destroy();
  });
  it('plots chronological completed results, preserves gaps and zeroes, and removes the duplicate table and disclaimer', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [history(5, 2025, 0), history(3, 2023, 70)]); fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.data.labels).toEqual(['2023', '2024', '2025', '2026']);
    expect(page.data.datasets[0].data).toEqual([70, null, 0, 80]);
    expect(page.data.datasets.every(series => series.spanGaps === false)).toBeTrue();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('not HR-approved');
    expect(fixture.nativeElement.textContent).not.toContain('ANNUAL_TREND.SOURCE_HELP'); fixture.destroy();
  });
  it('uses actual period dates rather than checkpoint dates or names and distinguishes current and historical results', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [{ ...history(3, 2023), periodName: 'Misleading 2026 name' }]); fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.points.map(p => p.year)).toEqual([2023, 2026]);
    expect(page.tooltipTitle(0)).toContain('01 Jan 2023 - 31 Dec 2023');
    page.selectPoint(0); expect(page.selectedSummary).toContain('Historical performance results');
    page.selectPoint(3); expect(page.selectedSummary).toContain('Current-period results');
    expect(page.selectedSummary).toContain('70% KPI + 30% Attitude'); fixture.destroy();
  });
  it('does not mix employees, future periods or duplicate selected-period records, regardless of appraisal approval', () => {
    const fixture = create();
    fixture.componentRef.setInput('history', [{ ...history(2, 2022), employeeId: 'carol' }, history(3, 2023), history(7, 2027),
      { ...history(6, 2026, 90), sourceType: 'SUBMISSION_SNAPSHOT', comparisonStatus: 'UNCHANGED' }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.points.map(point => point.periodId)).toEqual([3, 6]);
    expect(fixture.componentInstance.points[1].final).toBe(80); fixture.destroy();
  });
  it('supports three/five calendar years and distinct same-year periods with full names and date ranges', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [history(1, 2022), history(2, 2023), history(3, 2024),
      { ...history(4, 2025), periodName: 'January to June review', endDate: '2025-06-30' }, history(5, 2025)]);
    fixture.detectChanges(); const page = fixture.componentInstance; expect(page.points.length).toBe(6);
    page.setRange(3); fixture.detectChanges();
    expect(page.points.map(p => p.year)).toEqual([2024, 2025, 2025, 2026]);
    expect(page.data.labels).toEqual(['2024', '2025 (1)', '2025 (2)', '2026']);
    expect(page.tooltipTitle(1)).toContain('January to June review');
    expect(page.tooltipTitle(1)).toContain('01 Jan 2025 - 30 Jun 2025');
    expect(page.tooltipTitle(2)).toContain('[UAT SIMULATED] 2025'); fixture.destroy();
  });
  it('keeps recorded scores with concise correction metadata and explicitly labels simulated history', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [{ ...history(3, 2023, 70), sourceType: 'SUBMISSION_SNAPSHOT', comparisonStatus: 'DIFFERENT' }]); fixture.detectChanges();
    const page = fixture.componentInstance;
    page.selectPoint(0);
    expect(page.selectedSummary).toContain('70.00%'); expect(page.selectedSummary).toContain('Simulated UAT data');
    expect(page.tooltipFooter(0).join(' ')).toContain('Updated results available; showing recorded scores.');
    expect(page.tooltipFooter(0)[0]).toBe('50% KPI + 50% Attitude');
    fixture.componentRef.setInput('history', [{ ...history(3, 2023), sourceType: 'SUBMISSION_SNAPSHOT', comparisonStatus: 'CURRENT_RESULTS_UNAVAILABLE' }]); fixture.detectChanges();
    expect(page.tooltipFooter(0).join(' ')).toContain('current results are unavailable'); fixture.destroy();
  });
  it('supports keyboard exploration across gaps and announces exact scores without a visible table', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [history(3, 2023), history(5, 2025, 0)]); fixture.detectChanges();
    const canvas: HTMLCanvasElement = fixture.nativeElement.querySelector('canvas'); const page = fixture.componentInstance;
    canvas.focus(); fixture.detectChanges(); expect(page.selectedSummary).toContain('2023');
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true })); fixture.detectChanges();
    expect(page.selectedSummary).toContain('2025'); expect(page.selectedSummary).toContain('0.00%');
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', cancelable: true })); fixture.detectChanges();
    expect(page.selectedSummary).toContain('Current-period results');
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', cancelable: true })); fixture.detectChanges();
    expect(page.selectedSummary).toContain('2023'); expect(canvas.getAttribute('aria-describedby')).toContain('annual-trend-selection');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('2023'); fixture.destroy();
  });
  it('selects all three series when hovering away from the point anywhere in the same year column', () => {
    const fixture = create(); fixture.componentRef.setInput('history', [history(4, 2024), history(5, 2025)]); fixture.detectChanges();
    const chart = fixture.componentInstance.chart!.chart!;
    chart.options.animation = false; chart.update();
    expect(chart.options.interaction).toEqual(jasmine.objectContaining({ mode: 'index', axis: 'x', intersect: false }));
    const point = chart.getDatasetMeta(0).data[1]; const bounds = chart.canvas.getBoundingClientRect();
    const event = new MouseEvent('mousemove', { clientX: bounds.left + point.x, clientY: bounds.top + chart.chartArea.bottom - 2 });
    chart.canvas.dispatchEvent(event);
    const hits = chart.getElementsAtEventForMode(event,
      'index', { axis: 'x', intersect: false }, false);
    expect(hits.length).toBe(3); expect(hits.every(hit => hit.index === 1)).toBeTrue(); fixture.destroy();
  });
  it('handles first-year, pending-current and failed-history states without fabricated values', () => {
    const fixture = create(); fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('ANNUAL_TREND.ONE_PERIOD');
    fixture.componentRef.setInput('current', snapshot(null)); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('canvas')).toBeNull(); expect(fixture.nativeElement.textContent).toContain('ANNUAL_TREND.EMPTY');
    fixture.componentRef.setInput('history', [history(5, 2025)]); fixture.detectChanges(); expect(fixture.componentInstance.points.length).toBe(1);
    fixture.componentRef.setInput('error', true); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ANNUAL_TREND.UNAVAILABLE'); fixture.destroy();
  });
});
