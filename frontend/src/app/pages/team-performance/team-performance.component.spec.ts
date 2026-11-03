import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { DownOutline, CheckOutline, SearchOutline, LeftOutline, RightOutline, DoubleLeftOutline, DoubleRightOutline } from '@ant-design/icons-angular/icons';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { TeamPerformanceComponent } from './team-performance.component';
import { TeamPerformanceService } from '../../services/team-performance.service';
import { AuthService } from '../../services/auth.service';
import { TeamPerformance } from '../../models/team-performance.model';
import { teamDashboard, teamScope, departmentScope, teamPeriod } from './team-performance-test-fixtures';
import { performanceReturnUrl } from '../staff-performance/staff-performance-navigation';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

describe('Team Performance dashboard', () => {
  let api: jasmine.SpyObj<TeamPerformanceService>; let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>; let permissions: string[];
  beforeEach(() => {
    api = jasmine.createSpyObj('TeamPerformanceService', ['scopes', 'periods', 'dashboard']);
    api.scopes.and.returnValue(of([teamScope, departmentScope])); api.periods.and.returnValue(of([teamPeriod()])); api.dashboard.and.returnValue(of(teamDashboard()));
    query = new BehaviorSubject(convertToParamMap({})); permissions = ['CAN_VIEW_TEAM_PERFORMANCE'];
  });
  function create() {
    TestBed.configureTestingModule({ imports: [TeamPerformanceComponent, TranslateModule.forRoot()], providers: [provideRouter([]), provideNoopAnimations(),
      { provide: TeamPerformanceService, useValue: api }, { provide: ActivatedRoute, useValue: { queryParamMap: query, snapshot: { get queryParamMap() { return query.value; } } } },
      { provide: AuthService, useValue: { hasRole: (role: string) => permissions.includes(role) } },
      { provide: NZ_ICONS, useValue: [DownOutline, CheckOutline, SearchOutline, LeftOutline, RightOutline, DoubleLeftOutline, DoubleRightOutline] } ] });
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', { ANNUAL_APPRAISAL: { PENDING: 'Pending' },
      TEAM_PERFORMANCE: { CONTRIBUTORS: 'Based on {{count}} employees with results', OUT_OF: 'of {{count}} employees', NO_ACTIONS: 'No actions',
        DISTRIBUTION_HELP: '{{count}} with results; {{missing}} awaiting results', NOT_COMPARABLE: 'Not comparable', POINTS: 'pp' } }); translate.use('en');
    const fixture = TestBed.createComponent(TeamPerformanceComponent); fixture.detectChanges(); return fixture;
  }
  it('uses real independent contributor counts and preserves zero, Pending and distribution boundaries', () => {
    const fixture = create(); const page = fixture.componentInstance;
    expect(api.dashboard).toHaveBeenCalledOnceWith(teamScope, 1, 5, 'FINAL');
    const cards = fixture.nativeElement.querySelectorAll('.summary-card');
    expect(cards[1].textContent).toContain('0.00%'); expect(cards[1].textContent).toContain('Based on 1 employees');
    expect(cards[2].textContent).toContain('Based on 2 employees');
    expect(fixture.nativeElement.querySelector('.employee-table').textContent).toContain('Pending');
    expect(fixture.nativeElement.querySelector('.employee-table').textContent).toContain('0.00 pp');
    expect(fixture.nativeElement.querySelector('.employee-table').textContent).toContain('Not comparable');
    expect(page.binPercent(1)).toBe(100); expect(fixture.nativeElement.querySelector('.distribution-caption').textContent).toContain('1 with results; 1 awaiting results');
    expect(fixture.nativeElement.querySelector('.distribution-bin:last-of-type').textContent).toContain('100%'); fixture.destroy();
  });
  it('keeps team progress separate from authority to review', () => {
    const fixture = create(); const page = fixture.componentInstance;
    expect(page.actions).toEqual([]); expect(fixture.nativeElement.querySelector('.action-link')).toBeNull();
    expect(fixture.nativeElement.querySelector('#team-progress')).toBeNull();
    fixture.nativeElement.querySelector('.progress-section button').click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.progress-counts dd').length).toBe(5);
    expect(fixture.nativeElement.querySelector('#team-progress a')).toBeNull(); fixture.destroy();
  });
  it('only links permitted backend tasks with the expected destination and selected period', () => {
    permissions.push('CAN_REVIEW_KPI_ASSESSMENT'); const data = teamDashboard();
    data.attention.yourActions.links.push({ task: 'KPI_REVIEWS', path: 'https://invalid.test', parameters: { tab: 'assessments', reviewPeriodId: '1' } });
    api.dashboard.and.returnValue(of(data)); const fixture = create();
    expect(fixture.componentInstance.actions.length).toBe(1);
    expect(fixture.nativeElement.querySelector('.action-link').getAttribute('href')).toContain('tab=assessments');
    expect(fixture.nativeElement.querySelector('.overdue-pill')).not.toBeNull(); fixture.destroy();
  });
  it('retains Closed-period results while showing no backend actions', () => {
    const data = teamDashboard(); data.period.status = 'CLOSED'; data.attention.yourActions.links = [];
    api.dashboard.and.returnValue(of(data)); permissions.push('CAN_REVIEW_KPI_ASSESSMENT'); const fixture = create();
    expect(fixture.componentInstance.data?.summary.finalScore.score).toBe(40); expect(fixture.componentInstance.actions).toEqual([]); fixture.destroy();
  });
  it('loads authorised Department direct links and selected chart parameters', () => {
    query.next(convertToParamMap({ scope: 'DEPARTMENT', departmentId: '8', reviewPeriodId: '1', historyYears: '3', metric: 'KPI' }));
    const data = teamDashboard(departmentScope); data.distribution.metric = 'KPI'; api.dashboard.and.returnValue(of(data));
    const fixture = create(); expect(api.dashboard).toHaveBeenCalledOnceWith(departmentScope, 1, 3, 'KPI'); fixture.destroy();
  });
  it('rejects inaccessible and malformed selections without fetching a different employee population', () => {
    const fixture = create(); api.dashboard.calls.reset();
    for (const params of [{ scope: 'DEPARTMENT', departmentId: '99' }, { scope: '' }, { scope: 'TEAM', departmentId: '8' }, { metric: 'OTHER' },
      { historyYears: '4' }, { reviewPeriodId: '0' }, { reviewPeriodId: ['1', '2'] }, { scope: ['TEAM', 'DEPARTMENT'] }]) {
      query.next(convertToParamMap(params)); fixture.detectChanges(); expect(fixture.componentInstance.error).toBeTrue(); expect(fixture.componentInstance.data).toBeNull();
    }
    expect(api.dashboard).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('clears results on scope errors and offers a retry after access or network failure', () => {
    const fixture = create(); api.dashboard.and.returnValue(throwError(() => ({ status: 403 })));
    fixture.componentInstance.refresh(); fixture.detectChanges(); expect(fixture.componentInstance.data).toBeNull();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    api.dashboard.and.returnValue(of(teamDashboard())); fixture.componentInstance.refresh(); fixture.detectChanges(); expect(fixture.componentInstance.error).toBeFalse(); fixture.destroy();
  });
  it('ignores late responses after the selected period changes', () => {
    const old = new Subject<TeamPerformance>(), latest = new Subject<TeamPerformance>();
    api.periods.and.returnValue(of([teamPeriod(), teamPeriod(2)])); api.dashboard.and.callFake((_, id) => id === 1 ? old : latest);
    query.next(convertToParamMap({ reviewPeriodId: '1' })); const fixture = create();
    query.next(convertToParamMap({ reviewPeriodId: '2' })); old.next(teamDashboard()); expect(fixture.componentInstance.data).toBeNull();
    latest.next(teamDashboard(teamScope, teamPeriod(2))); latest.complete(); fixture.detectChanges(); expect(fixture.componentInstance.data?.period.id).toBe(2); fixture.destroy();
  });
  it('rejects a mismatched response and does not display it', () => {
    api.dashboard.and.returnValue(of(teamDashboard(departmentScope))); const fixture = create(); expect(fixture.componentInstance.error).toBeTrue(); expect(fixture.componentInstance.data).toBeNull(); fixture.destroy();
  });
  it('shows clear empty states for missing scope, periods and employees', () => {
    api.scopes.and.returnValue(of([])); const fixture = create(); const page = fixture.componentInstance;
    expect(page.empty).toBe('scopes'); expect(api.periods).not.toHaveBeenCalled();
    api.scopes.and.returnValue(of([teamScope])); api.periods.and.returnValue(of([])); page.refresh(); expect(page.empty).toBe('periods'); expect(api.dashboard).not.toHaveBeenCalled();
    const data = teamDashboard(); data.employees = []; api.periods.and.returnValue(of([teamPeriod()])); api.dashboard.and.returnValue(of(data)); page.refresh(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('TEAM_PERFORMANCE.NO_EMPLOYEES'); fixture.destroy();
  });
  it('restores filters and pagination from the return URL without reloading on local filter changes', () => {
    const fixture = create(); const page = fixture.componentInstance;
    query.next(convertToParamMap({ search: 'Carol', role: 'Analyst', result: 'PENDING', page: '99', sort: 'final', direction: 'desc' })); fixture.detectChanges();
    expect(api.dashboard).toHaveBeenCalledTimes(1); expect(page.rows.map(e => e.name)).toEqual(['Carol']); expect(page.currentPage).toBe(1);
    const link = page.employeeLink(page.rows[0]); expect(link.queryParams['reviewPeriodId']).toBe('1'); expect(link.queryParams['participantId']).toBe('12');
    const back = link.queryParams['returnUrl']; expect(performanceReturnUrl(back)).toBe(back); expect(back).toContain('search=Carol'); expect(back).toContain('scope=TEAM');
    query.next(convertToParamMap({})); fixture.detectChanges(); expect(page.rows.length).toBe(2); fixture.destroy();
  });
  it('keeps pending values last in numeric sorting and does not filter team summary totals', () => {
    const fixture = create(); const page = fixture.componentInstance; page.sort = 'kpi'; page.descending = true;
    expect(page.rows.map(e => e.name)).toEqual(['Bob', 'Carol']); page.result = 'AVAILABLE';
    expect(page.rows.length).toBe(1); expect(page.data?.summary.totalEmployees).toBe(2); fixture.destroy();
  });
  it('retains the exact Organisation return context through Department and Staff drill-down', () => {
    const back = '/appraisals/organisation-performance?reviewPeriodId=1&metric=KPI&search=IT';
    query.next(convertToParamMap({ scope: 'DEPARTMENT', departmentId: '8', returnUrl: back })); api.dashboard.and.returnValue(of(teamDashboard(departmentScope)));
    const fixture = create(); const page = fixture.componentInstance;
    expect(fixture.nativeElement.querySelector('.back-link').getAttribute('href')).toBe(back);
    expect(page.context.returnUrl).toBe(back); const staff = page.employeeLink(page.rows[0]);
    const router = TestBed.inject(Router); const team = router.parseUrl(staff.queryParams['returnUrl']); expect(team.queryParams['returnUrl']).toBe(back);
    const nav = spyOn(router, 'navigate').and.resolveTo(true); page.changeYears(3); expect(nav.calls.mostRecent().args[1]?.queryParams?.['returnUrl']).toBe(back);
    query.next(convertToParamMap({ scope: 'DEPARTMENT', departmentId: '8', returnUrl: 'https://evil.test' })); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('.back-link')).toBeNull(); fixture.destroy();
  });
  it('navigates scope, range and metric changes through validated URL selections', () => {
    const fixture = create(); const nav = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true); const page = fixture.componentInstance;
    page.changeScope('DEPARTMENT:8'); expect(nav.calls.mostRecent().args[1]?.queryParams).toEqual(jasmine.objectContaining({ scope: 'DEPARTMENT', departmentId: 8, reviewPeriodId: null }));
    page.changeYears(3); expect(nav.calls.mostRecent().args[1]?.queryParams?.['historyYears']).toBe(3);
    page.changeMetric('ATTITUDE'); expect(nav.calls.mostRecent().args[1]?.queryParams?.['metric']).toBe('ATTITUDE'); fixture.destroy();
  });
  it('contains wide employee tables and wraps dashboard controls at desktop, tablet and mobile sizes', () => {
    const fixture = create();
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(fixture.nativeElement.querySelector('main'), width);
      try { expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(layout.window.getComputedStyle(layout.root.querySelector('.table-scroll')!).overflowX).toBe('auto');
      } finally { layout.remove(); }
    }
    fixture.destroy();
  });
});
