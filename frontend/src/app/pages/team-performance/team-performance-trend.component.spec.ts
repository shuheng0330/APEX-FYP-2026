import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TeamPerformanceTrendComponent } from './team-performance-trend.component';
import { teamDashboard, teamPeriod } from './team-performance-test-fixtures';

describe('Team performance trend', () => {
  function create() {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', { ANNUAL_APPRAISAL: { PENDING: 'Pending' }, TEAM_PERFORMANCE: { CONTRIBUTORS: '{{count}} contributors' } }); translate.use('en');
    return new TeamPerformanceTrendComponent(translate, 'en-US');
  }
  it('preserves same-year periods, missing-year gaps, zeroes and full period context', () => {
    const chart = create(); const data = teamDashboard(); chart.period = teamPeriod(3); chart.metric = 'KPI';
    chart.points = [{ ...data.trend[0], period: { ...teamPeriod(), id: 100, name: 'First annual period', endDate: '2026-06-30' } },
      data.trend[0], { ...data.trend[0], period: teamPeriod(3) }]; chart.ngOnChanges();
    expect(chart.data.labels).toEqual(['2026 (1)', '2026 (2)', '2027', '2028']);
    expect(chart.data.datasets[0].data).toEqual([0, 0, null, 0]); expect(chart.data.datasets[0].spanGaps).toBeFalse();
    expect(chart.title(0).join(' ')).toContain('First annual period'); expect(chart.title(0).join(' ')).toContain('30 Jun 2026');
    expect(chart.options?.interaction).toEqual({ mode: 'index', axis: 'x', intersect: false });
    expect(chart.hasResults).toBeTrue();
  });
  it('uses the selected metric average and contributor count without recalculating scores', () => {
    const chart = create(); const data = teamDashboard(); chart.period = data.period; chart.points = data.trend; chart.metric = 'ATTITUDE'; chart.ngOnChanges();
    expect(chart.data.datasets[0].data).toEqual([80]); expect(chart.footer(0)).toContain('2 contributors');
    chart.select(0); expect(chart.selectedSummary).toContain('80.00%');
  });
  it('supports keyboard exploration, date boundaries and empty results', () => {
    const chart = create(); const data = teamDashboard(); chart.period = teamPeriod(3);
    chart.points = [data.trend[0], { ...data.trend[0], period: teamPeriod(3) }, { ...data.trend[0], period: teamPeriod(4) }]; chart.ngOnChanges();
    chart.navigate(new KeyboardEvent('keydown', { key: 'End' })); expect(chart.selectedSummary).toContain('2028');
    chart.navigate(new KeyboardEvent('keydown', { key: 'Home' })); expect(chart.selectedSummary).toContain('2026');
    expect(chart.slots.some(p => p?.period.id === 4)).toBeFalse();
    chart.points = [{ ...data.trend[0], summary: { ...data.summary, finalScore: { score: null, contributingEmployees: 0 } } }]; chart.ngOnChanges();
    expect(chart.hasResults).toBeFalse(); chart.select(0); expect(chart.selectedSummary).toContain('Pending');
  });
  it('aligns Department series by period ID, keeps missing results as gaps and announces all selected scores', () => {
    const chart = create(), data = teamDashboard(); chart.period = teamPeriod(3); chart.chartId = 'org-departments';
    const second = { ...data.trend[0], period: { ...teamPeriod(), id: 99, name: 'Second same-year period' } };
    chart.series = [{ name: 'IT', points: data.trend }, { name: 'Finance', points: [second, { ...data.trend[0], period: teamPeriod(3) }] }]; chart.ngOnChanges();
    expect(chart.data.labels).toEqual(['2026 (1)', '2026 (2)', '2027', '2028']);
    expect(chart.data.datasets.map(d => d.data)).toEqual([[40, null, null, null], [null, 40, null, 40]]);
    expect(chart.footer(1)).toContain('Finance: 1 contributors'); expect(chart.title(1).join(' ')).toContain('Second same-year period');
    chart.navigate(new KeyboardEvent('keydown', { key: 'End' })); expect(chart.selectedSummary).toContain('Finance: 40.00%'); expect(chart.selectedSummary).toContain('IT: Pending');
    chart.series = []; chart.ngOnChanges(); expect(chart.hasResults).toBeFalse();
  });
  it('uses actual period end dates for Organisation axes without changing Team labels or chronology', () => {
    const chart = create(), data = teamDashboard(); chart.period = teamPeriod(3); chart.periodAxis = true;
    chart.points = [{ ...data.trend[0], period: { ...teamPeriod(), id: 100, name: 'Half-year Review', endDate: '2026-06-30' } }, data.trend[0], { ...data.trend[0], period: teamPeriod(3) }]; chart.ngOnChanges();
    expect(chart.data.labels).toEqual([['30 Jun 2026'], ['31 Dec 2026'], '2027', ['31 Dec 2028']]); expect(chart.data.datasets[0].data).toEqual([40, 40, null, 40]);
    expect(chart.title(0).join(' ')).toContain('Half-year Review'); expect(chart.title(0).join(' ')).toContain('30 Jun 2026');
    chart.periodAxis = false; chart.ngOnChanges(); expect(chart.data.labels).toEqual(['2026 (1)', '2026 (2)', '2027', '2028']);
  });
  it('retains distinct same-date periods and their full names in Organisation comparison tooltips', () => {
    const chart = create(), data = teamDashboard(); chart.period = data.period; chart.periodAxis = true;
    chart.points = [data.trend[0], { ...data.trend[0], period: { ...data.period, id: 100, name: 'Separate Annual Review with a long recorded name' } }]; chart.ngOnChanges();
    expect(chart.slots.map(p => p?.period.id)).toEqual([1, 100]); expect(chart.data.labels?.length).toBe(2);
    expect(chart.data.labels?.[0]).not.toEqual(chart.data.labels?.[1]); expect(chart.title(1).join(' ')).toContain('Separate Annual Review with a');
    expect(chart.title(1).join(' ')).toContain('long recorded name');
  });
});
