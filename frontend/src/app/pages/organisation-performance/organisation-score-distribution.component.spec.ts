import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { OrganisationScoreDistributionComponent } from './organisation-score-distribution.component';
import { organisationDashboard } from './organisation-performance-test-fixtures';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

describe('Organisation score histogram', () => {
  function create() {
    TestBed.configureTestingModule({ imports: [OrganisationScoreDistributionComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', {
      TEAM_PERFORMANCE: { METRICS: { KPI: 'KPI Performance Score', ATTITUDE: 'Attitude Evaluation Score', FINAL: 'Final Appraisal Score' } },
      ORG_PERFORMANCE: { HISTOGRAM: { X_AXIS: 'Score Range', Y_AXIS: 'Number of Employees', TO: 'to', LESS_THAN: 'less than',
        BELOW: 'Below {{upper}}%', BAND: '{{lower}}%–{{upper}}%',
        RANGE: '{{lower}}% to less than {{upper}}%', INCLUSIVE_RANGE: '{{lower}}% to {{upper}}%', COUNT: 'Employees: {{count}}', PERCENT: '{{percent}}% of employees with results',
        CAPTION: 'Employees with results: {{count}}; Awaiting results: {{missing}}', EMPTY: 'No completed results are available for this score yet.' } }
    }); translate.use('en');
    const fixture = TestBed.createComponent(OrganisationScoreDistributionComponent);
    fixture.componentRef.setInput('distribution', organisationDashboard().distribution); fixture.detectChanges(); return fixture;
  }
  it('renders vertical bars using all five unchanged backend bins and whole employee counts', () => {
    const fixture = create(), chart = fixture.componentInstance;
    expect(chart.data.datasets[0].data).toEqual([1, 0, 0, 0, 0]); expect(chart.options?.indexAxis).toBe('x');
    expect(chart.data.labels).toEqual(['Below 60%', '60%–69%', '70%–79%', '80%–89%', '90%–100%']);
    expect(chart.options?.scales?.['x']?.title).toEqual(jasmine.objectContaining({ display: true, text: 'Score Range' }));
    expect(chart.options?.scales?.['y']?.title).toEqual(jasmine.objectContaining({ display: true, text: 'Number of Employees' }));
    expect(chart.options?.scales?.['y']?.ticks).toEqual(jasmine.objectContaining({ precision: 0 }));
    expect(chart.rangeLabel(chart.distribution!.bins[0])).toBe('Below 60%'); expect(chart.rangeLabel(chart.distribution!.bins[4])).toBe('90% to 100%');
    expect(chart.distribution!.bins.slice(1, 4).map(bin => chart.rangeLabel(bin))).toEqual(['60% to less than 70%', '70% to less than 80%', '80% to less than 90%']);
    expect(fixture.nativeElement.querySelector('canvas').getAttribute('tabindex')).toBe('0');
    expect(fixture.nativeElement.querySelector('table tbody').rows.length).toBe(5); fixture.destroy();
  });
  it('uses completed-result employees as the percentage denominator, excluding pending results', () => {
    const fixture = create(), chart = fixture.componentInstance;
    const distribution = organisationDashboard().distribution; distribution.employeesWithResults = 4; distribution.missingResults = 7;
    distribution.bins[0].employees = 1; distribution.bins[2].employees = 0; distribution.bins[4].employees = 3;
    const before = JSON.stringify(distribution); fixture.componentRef.setInput('distribution', distribution); fixture.detectChanges();
    expect(chart.tooltipLines(0)).toEqual(['Employees: 1', '25% of employees with results']); expect(chart.tooltipLines(4)).toEqual(['Employees: 3', '75% of employees with results']);
    expect(fixture.nativeElement.querySelector('.distribution-caption').textContent).toContain('Employees with results: 4; Awaiting results: 7');
    expect(JSON.stringify(distribution)).toBe(before); fixture.destroy();
  });
  it('preserves legitimate zero scores in the first band and zero-count bars without marking them Pending', () => {
    const fixture = create(), chart = fixture.componentInstance; const distribution = organisationDashboard().distribution;
    distribution.bins[0].employees = 1; distribution.bins[2].employees = 0; fixture.componentRef.setInput('distribution', distribution); fixture.detectChanges();
    expect(chart.hasResults).toBeTrue(); expect(chart.data.datasets[0].data).toEqual([1, 0, 0, 0, 0]);
    chart.select(1); expect(chart.selectedSummary).toContain('Employees: 0'); expect(chart.selectedSummary).toContain('0% of employees with results'); expect(chart.selectedSummary).not.toContain('Pending'); fixture.destroy();
  });
  it('announces full range, selected metric and count through keyboard and tap-selection helpers', () => {
    const fixture = create(), chart = fixture.componentInstance; const canvas = fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement;
    canvas.dispatchEvent(new Event('focus')); expect(chart.selectedIndex).toBe(0);
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' })); expect(chart.selectedSummary).toContain('Final Appraisal Score. 90% to 100%');
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' })); expect(chart.selectedIndex).toBe(3);
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' })); expect(chart.selectedIndex).toBe(0);
    chart.select(0); expect(chart.selectedSummary).toContain('Below 60%'); expect(chart.selectedSummary).toContain('Employees: 1');
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('[aria-live=polite]').textContent).toContain('100% of employees with results'); fixture.destroy();
  });
  it('resets selected details on metric changes, including an all-pending or absent distribution', () => {
    const fixture = create(), chart = fixture.componentInstance; chart.select(2);
    const distribution = organisationDashboard().distribution; distribution.metric = 'ATTITUDE'; fixture.componentRef.setInput('distribution', distribution); fixture.detectChanges();
    expect(chart.selectedSummary).toBe(''); chart.select(2); expect(chart.selectedSummary).toContain('Attitude Evaluation Score');
    distribution.employeesWithResults = 0; distribution.bins.forEach(b => b.employees = 0); chart.ngOnChanges(); fixture.detectChanges();
    expect(chart.hasResults).toBeFalse(); expect(fixture.nativeElement.querySelector('canvas')).toBeNull(); expect(fixture.nativeElement.textContent).toContain('No completed results');
    expect(chart.tooltipLines(0)).toEqual(['Employees: 0']);
    fixture.componentRef.setInput('distribution', null); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('.distribution-caption')).toBeNull(); fixture.destroy();
  });
  it('handles empty bins without inventing replacement bands or counts', () => {
    const fixture = create(), chart = fixture.componentInstance;
    fixture.componentRef.setInput('distribution', { ...organisationDashboard().distribution, bins: [], employeesWithResults: 0 }); fixture.detectChanges();
    expect(chart.data.labels).toEqual([]); expect(chart.data.datasets[0].data).toEqual([]); expect(chart.tooltipLines(0)).toEqual([]); expect(chart.hasResults).toBeFalse(); fixture.destroy();
  });
  it('uses returned boundaries rather than relabelling an older backend response with the new cutoffs', () => {
    const fixture = create(), chart = fixture.componentInstance;
    const distribution = organisationDashboard().distribution;
    distribution.bins = [{ lowerInclusive: 0, upper: 20, upperInclusive: false, employees: 1 }];
    fixture.componentRef.setInput('distribution', distribution); fixture.detectChanges();
    expect(chart.data.labels).toEqual(['Below 20%']); expect(chart.rangeLabel(distribution.bins[0])).toBe('Below 20%');
    expect(chart.data.datasets[0].data).toEqual([1]); fixture.destroy();
  });
  it('contains the responsive chart at desktop, tablet and narrow-mobile widths', () => {
    const fixture = create();
    for (const width of [1440, 768, 360, 320]) {
      const layout = reviewLayout(fixture.nativeElement.querySelector('.distribution-chart'), width);
      try { expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width); expect(layout.window.getComputedStyle(layout.root).height).toBe(width <= 600 ? '265px' : '280px'); }
      finally { layout.remove(); }
    }
    fixture.destroy();
  });
});
