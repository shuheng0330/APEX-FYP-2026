import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { DownOutline, CheckOutline, CloseOutline, LoadingOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { OrganisationPerformanceComponent } from './organisation-performance.component';
import { OrganisationPerformanceService } from '../../services/organisation-performance.service';
import { OrganisationPerformance } from '../../models/organisation-performance.model';
import { organisationDashboard } from './organisation-performance-test-fixtures';
import { teamPeriod } from '../team-performance/team-performance-test-fixtures';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';
import { organisationReturnUrl } from './organisation-performance-navigation';
import { OrganisationScoreDistributionComponent } from './organisation-score-distribution.component';

describe('Organisation Performance dashboard', () => {
  let api: jasmine.SpyObj<OrganisationPerformanceService>; let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const workbook = () => new Blob(['workbook'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  beforeEach(() => {
    api = jasmine.createSpyObj('OrganisationPerformanceService', ['periods', 'dashboard', 'export']);
    api.periods.and.returnValue(of([teamPeriod()])); api.dashboard.and.returnValue(of(organisationDashboard())); api.export.and.returnValue(of(workbook()));
    query = new BehaviorSubject(convertToParamMap({}));
  });
  function create() {
    TestBed.configureTestingModule({ imports: [OrganisationPerformanceComponent, TranslateModule.forRoot()], providers: [provideRouter([]), provideNoopAnimations(),
      { provide: OrganisationPerformanceService, useValue: api }, { provide: ActivatedRoute, useValue: { queryParamMap: query, snapshot: { get queryParamMap() { return query.value; } } } },
      { provide: NZ_ICONS, useValue: [DownOutline, CheckOutline, CloseOutline, LoadingOutline, SearchOutline] } ] });
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', {
      ANNUAL_APPRAISAL: { PENDING: 'Pending', RECOMMENDATIONS: { PROMOTION: 'Promotion', SALARY_INCREMENT: 'Salary Increment', BOTH: 'Promotion and Salary Increment', NO_RECOMMENDATION: 'No Recommendation' } },
      TEAM_PERFORMANCE: { CONTRIBUTORS: 'Based on {{count}} employees with results', NOT_COMPARABLE: 'Not comparable', POINTS: 'pp', DISTRIBUTION_HELP: '{{count}} results; {{missing}} awaiting results' },
      ORG_PERFORMANCE: { RESULT_COUNT: 'Employees with results: {{count}}', PRIVACY: 'Not enough data to show appraisal statistics safely', NO_DECISIONS: 'No appraisal data available',
        SAMPLES: { kpi_ONE: 'Based on {{count}} employee with available KPI results.', kpi_OTHER: 'Based on {{count}} employees with available KPI results.', attitude_ONE: 'Based on {{count}} employee with available Attitude results.', attitude_OTHER: 'Based on {{count}} employees with available Attitude results.', finalScore_ONE: 'Based on {{count}} employee with available Final Scores.', finalScore_OTHER: 'Based on {{count}} employees with available Final Scores.' },
        HISTOGRAM: { CAPTION: '{{count}} results; {{missing}} awaiting results', BELOW: 'Below {{upper}}%', BAND: '{{lower}}%–{{upper}}%', RANGE: '{{lower}}% to less than {{upper}}%', INCLUSIVE_RANGE: '{{lower}}% to {{upper}}%', TO: 'to', LESS_THAN: 'less than' },
        DECISION_BASE: { superiorRecommendations: '{{count}} recorded submissions', hrFinalDecisions: '{{count}} approved HR decisions' } }
    }); translate.use('en');
    const fixture = TestBed.createComponent(OrganisationPerformanceComponent); fixture.detectChanges(); return fixture;
  }
  it('uses independent contributors, preserves zero and Pending, and displays backend score bands', () => {
    const fixture = create(); expect(api.dashboard).toHaveBeenCalledOnceWith(1, 5, 'FINAL');
    const cards = fixture.nativeElement.querySelectorAll('.summary-card');
    expect(cards[1].textContent).toContain('0.00%'); expect(cards[1].textContent).toContain('Employees with results: 1'); expect(cards[2].textContent).toContain('Employees with results: 2');
    const table = fixture.nativeElement.querySelector('.department-table'); expect(table.textContent).toContain('Pending'); expect(table.textContent).toContain('0.00 pp'); expect(table.textContent).toContain('Not comparable');
    expect(fixture.nativeElement.querySelector('.distribution-caption').textContent).toContain('1 results; 1 awaiting results');
    expect(fixture.nativeElement.querySelector('.score-distribution').textContent).toContain('Below 60%');
    expect(fixture.nativeElement.querySelector('.score-distribution').textContent).toContain('90% to 100%');
    expect(fixture.nativeElement.querySelector('.score-distribution canvas')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.score-distribution .bar-track')).toBeNull(); fixture.destroy();
  });
  it('keeps KPI and Attitude progress distinct, without granting task actions', () => {
    const fixture = create(); const row = fixture.nativeElement.querySelector('.progress-table tbody tr');
    expect([...row.querySelectorAll('td')].map(cell => (cell as HTMLElement).textContent?.trim())).toEqual(['2', '3', '4', '1', '2', '1', '2']);
    expect(row.querySelector('a,button')).toBeNull(); expect(fixture.nativeElement.querySelectorAll('.department-table a').length).toBe(2); fixture.destroy();
  });
  it('shows only disclosed outcome counts and translates recommendation names', () => {
    const fixture = create(); const section = fixture.nativeElement.querySelector('.decision-distribution');
    expect(section.querySelectorAll('.decision-bin').length).toBe(4); expect(section.textContent).toContain('33.3%');
    expect(section.textContent).toContain('Promotion'); expect(section.textContent).not.toContain('SALARY_INCREMENT'); expect(section.textContent).toContain('15 recorded submissions');
    query.next(convertToParamMap({ decisions: 'hrFinalDecisions' })); fixture.detectChanges();
    expect(api.dashboard).toHaveBeenCalledTimes(1); expect(section.textContent).toContain('Not enough data to show appraisal statistics safely'); expect(section.querySelector('.decision-bin')).toBeNull();
    expect(section.textContent).not.toContain('15 recorded submissions'); expect(section.textContent).not.toContain('approved HR decisions'); fixture.destroy();
  });
  it('never reconstructs suppressed counts, including an inconsistent payload', () => {
    const data = organisationDashboard(); data.appraisalDistribution.superiorRecommendations = { disclosure: 'SUPPRESSED', total: 12345, counts: [{ decision: 'PROMOTION', count: 12345 }] };
    api.dashboard.and.returnValue(of(data)); const fixture = create(); const section = fixture.nativeElement.querySelector('.decision-distribution');
    expect(section.textContent).toContain('Not enough data to show appraisal statistics safely'); expect(section.textContent).not.toContain('12345'); expect(section.querySelector('.bar-fill')).toBeNull(); expect(fixture.componentInstance.decisionPercent(12345)).toBe(0); fixture.destroy();
  });
  it('distinguishes no recorded decisions from privacy suppression', () => {
    const data = organisationDashboard(); data.appraisalDistribution.superiorRecommendations = { disclosure: 'NO_DATA', total: 0, counts: [] };
    api.dashboard.and.returnValue(of(data)); const fixture = create(); expect(fixture.nativeElement.querySelector('.decision-distribution').textContent).toContain('No appraisal data available');
    expect(fixture.nativeElement.querySelector('.privacy-notice')).toBeNull(); fixture.destroy();
  });
  it('loads a selected Closed period and metric on refresh without selecting another period', () => {
    const data = organisationDashboard(teamPeriod(2)); data.period.status = 'CLOSED'; data.historyYears = 3; data.distribution.metric = 'KPI';
    query.next(convertToParamMap({ reviewPeriodId: '2', historyYears: '3', metric: 'KPI' })); api.periods.and.returnValue(of([teamPeriod(), data.period])); api.dashboard.and.returnValue(of(data));
    const fixture = create(); expect(api.dashboard).toHaveBeenCalledOnceWith(2, 3, 'KPI'); expect(fixture.componentInstance.data?.period.status).toBe('CLOSED');
    fixture.componentInstance.refresh(); expect(api.dashboard).toHaveBeenCalledTimes(2); fixture.destroy();
  });
  it('rejects malformed, duplicate and unavailable selections without silently substituting data', () => {
    const fixture = create(); api.dashboard.calls.reset();
    for (const params of [{ reviewPeriodId: '0' }, { reviewPeriodId: '999' }, { reviewPeriodId: ['1', '2'] }, { metric: 'OTHER' }, { metric: ['KPI', 'FINAL'] }, { historyYears: '4' }]) {
      query.next(convertToParamMap(params)); fixture.detectChanges(); expect(fixture.componentInstance.error).toBeTrue(); expect(fixture.componentInstance.data).toBeNull();
    }
    expect(api.dashboard).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('ignores stale dashboard responses and clears old content on access/network failures', () => {
    const old = new Subject<OrganisationPerformance>(), current = new Subject<OrganisationPerformance>();
    api.periods.and.returnValue(of([teamPeriod(), teamPeriod(2)])); api.dashboard.and.callFake(id => id === 1 ? old : current);
    query.next(convertToParamMap({ reviewPeriodId: '1' })); const fixture = create();
    query.next(convertToParamMap({ reviewPeriodId: '2' })); old.next(organisationDashboard()); expect(fixture.componentInstance.data).toBeNull();
    current.next(organisationDashboard(teamPeriod(2))); current.complete(); fixture.detectChanges(); expect(fixture.componentInstance.data?.period.id).toBe(2);
    api.periods.and.returnValue(throwError(() => ({ status: 403 }))); fixture.componentInstance.refresh(); fixture.detectChanges();
    expect(fixture.componentInstance.data).toBeNull(); expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull(); fixture.destroy();
  });
  it('rejects a mismatched period, range or score response', () => {
    const fixture = create();
    for (const data of [organisationDashboard(teamPeriod(2)), { ...organisationDashboard(), historyYears: 3 as const }, { ...organisationDashboard(), distribution: { ...organisationDashboard().distribution, metric: 'KPI' as const } }]) {
      api.dashboard.and.returnValue(of(data)); fixture.componentInstance.refresh(); expect(fixture.componentInstance.error).toBeTrue(); expect(fixture.componentInstance.data).toBeNull();
    }
    fixture.destroy();
  });
  it('offers no-period and empty-department states without creating records', () => {
    api.periods.and.returnValue(of([])); const fixture = create(); const page = fixture.componentInstance;
    expect(page.empty).toBeTrue(); expect(api.dashboard).not.toHaveBeenCalled(); expect(fixture.nativeElement.querySelector('.export-button').disabled).toBeTrue();
    const data = organisationDashboard(); data.departments = []; data.departmentTrends = []; api.periods.and.returnValue(of([teamPeriod()])); api.dashboard.and.returnValue(of(data)); page.refresh(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.department-table').textContent).toContain('ORG_PERFORMANCE.NO_DEPARTMENTS'); fixture.destroy();
  });
  it('restores local search, sorting and chart selection without refetching or filtering aggregate totals', () => {
    const fixture = create(); const page = fixture.componentInstance;
    const selection = page.selectedDepartments; fixture.detectChanges(); expect(page.selectedDepartments).toBe(selection);
    query.next(convertToParamMap({ search: 'IT', sort: 'final', direction: 'desc', compare: ['8'] })); fixture.detectChanges();
    expect(api.dashboard).toHaveBeenCalledTimes(1); expect(page.rows.map(d => d.name)).toEqual(['IT']); expect(page.comparisonSeries.map(d => d.name)).toEqual(['IT']); expect(page.data?.departments.length).toBe(2);
    query.next(convertToParamMap({ compare: [''] })); expect(page.comparisonSeries).toEqual([]);
    query.next(convertToParamMap({ compare: ['unassigned', '99'] })); expect(page.comparisonSeries.map(d => d.name)).toEqual(['Unassigned Department']);
    page.sort = 'final'; page.descending = true; expect(page.rows.map(d => d.name)).toEqual(['IT', 'Unassigned Department']); fixture.destroy();
  });
  it('links authorised Department views including unassigned records and retains the organisation context', () => {
    query.next(convertToParamMap({ metric: 'KPI', historyYears: '3', search: 'IT', compare: ['8'] })); const data = organisationDashboard(); data.historyYears = 3; data.distribution.metric = 'KPI'; api.dashboard.and.returnValue(of(data));
    const fixture = create(); const page = fixture.componentInstance; const link = page.teamLink(data.departments[0]);
    expect(link.queryParams['scope']).toBe('DEPARTMENT'); expect(link.queryParams['departmentId']).toBe('8'); expect(link.queryParams['reviewPeriodId']).toBe('1');
    expect(organisationReturnUrl(link.queryParams['returnUrl'])).toBe(link.queryParams['returnUrl']); expect(link.queryParams['returnUrl']).toContain('search=IT'); expect(link.queryParams['returnUrl']).toContain('compare=8');
    expect(page.teamLink(data.departments[1]).queryParams['departmentId']).toBeUndefined(); fixture.destroy();
  });
  it('sends one export for the current selection and uses the .xlsx download', () => {
    const response = new Subject<Blob>(); api.export.and.returnValue(response); const fixture = create(); const page = fixture.componentInstance; const download = spyOn(page, 'download');
    page.exportExcel(); page.exportExcel(); expect(api.export).toHaveBeenCalledOnceWith(1, 5, 'FINAL'); expect(page.exporting).toBeTrue();
    const blob = workbook(); response.next(blob); response.complete(); expect(download).toHaveBeenCalledOnceWith(blob, 'organisation-performance-period-1.xlsx'); expect(page.exporting).toBeFalse(); fixture.destroy();
  });
  it('preserves the dashboard on export failure and rejects error or empty blobs', () => {
    const fixture = create(); const page = fixture.componentInstance; const download = spyOn(page, 'download');
    for (const response of [throwError(() => ({ status: 403 })), of(new Blob()), of(new Blob(['error'], { type: 'application/json' }))]) {
      api.export.and.returnValue(response); page.exportExcel(); fixture.detectChanges(); expect(page.exportError).toBeTrue(); expect(page.data).not.toBeNull(); expect(page.exporting).toBeFalse();
    }
    expect(download).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('cancels stale exports on selection change, refresh and destruction', () => {
    const fixture = create(); const page = fixture.componentInstance; const download = spyOn(page, 'download');
    for (const cancel of [() => query.next(convertToParamMap({ reviewPeriodId: '1' })), () => page.refresh(), () => fixture.destroy()]) {
      const response = new Subject<Blob>(); api.export.and.returnValue(response); page.exportExcel(); cancel(); response.next(workbook()); response.complete(); expect(page.exporting).toBeFalse();
    }
    expect(download).not.toHaveBeenCalled();
  });
  it('keeps the two chart descriptions distinct and wide tables inside their scroll containers', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelectorAll('#org-trend-help').length).toBe(1); expect(fixture.nativeElement.querySelectorAll('#org-department-trend-help').length).toBe(1);
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(fixture.nativeElement.querySelector('main'), width);
      try { expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width); for (const table of layout.root.querySelectorAll('.table-scroll')) expect(layout.window.getComputedStyle(table).overflowX).toBe('auto'); }
      finally { layout.remove(); }
    }
    fixture.destroy();
  });
  it('shows independent metric samples only in accessible hover/focus/tap details', fakeAsync(() => {
    const fixture = create(); const cells = fixture.nativeElement.querySelectorAll('.department-table tbody tr:first-child .department-score');
    expect(cells[0].querySelector('button').textContent.trim()).toBe('0.00%'); expect(cells[0].querySelector('p.help')).toBeNull();
    expect(cells[0].querySelector('.sr-only').textContent).toContain('1 employee with available KPI results');
    expect(cells[1].querySelector('.sr-only').textContent).toContain('2 employees with available Attitude results');
    const button = cells[0].querySelector('button') as HTMLButtonElement;
    button.dispatchEvent(new Event('focus')); fixture.detectChanges(); tick(200); fixture.detectChanges();
    expect(document.querySelector('.ant-tooltip-inner')?.textContent).toContain('1 employee with available KPI results');
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); tick(200); fixture.detectChanges();
    button.click(); tick(200); fixture.detectChanges(); expect(document.querySelector('.ant-tooltip-inner')?.textContent).toContain('1 employee with available KPI results');
    button.dispatchEvent(new Event('blur')); tick(200); fixture.destroy();
  }));
  it('updates the histogram from the selected metric without changing bin counts or roster inclusion', () => {
    const fixture = create(); const data = organisationDashboard(); data.distribution.metric = 'ATTITUDE'; data.distribution.employeesWithResults = 2; data.distribution.missingResults = 0;
    data.distribution.bins[0].employees = 0; data.distribution.bins[3].employees = 2; api.dashboard.and.returnValue(of(data));
    query.next(convertToParamMap({ metric: 'ATTITUDE' })); fixture.detectChanges();
    const chart = fixture.debugElement.query(By.directive(OrganisationScoreDistributionComponent)).componentInstance as OrganisationScoreDistributionComponent;
    expect(api.dashboard).toHaveBeenCalledWith(1, 5, 'ATTITUDE'); expect(chart.distribution).toBe(data.distribution); expect(chart.data.datasets[0].data).toEqual([0, 0, 0, 2, 0]); fixture.destroy();
  });
  it('keeps the Excel contract and selection unchanged despite visual Department filters', () => {
    const data = organisationDashboard(); data.historyYears = 3; data.distribution.metric = 'KPI'; api.dashboard.and.returnValue(of(data));
    query.next(convertToParamMap({ historyYears: '3', metric: 'KPI', search: 'IT', compare: ['8'] }));
    const fixture = create(); spyOn(fixture.componentInstance, 'download'); fixture.componentInstance.exportExcel();
    expect(api.export).toHaveBeenCalledOnceWith(1, 3, 'KPI'); fixture.destroy();
  });
});
