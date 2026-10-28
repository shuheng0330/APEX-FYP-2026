import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, CheckOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { StaffPerformanceComponent } from './staff-performance.component';
import { StaffPerformanceService } from '../../services/staff-performance.service';
import { AuthService } from '../../services/auth.service';
import { PerformanceApprovedAppraisal, StaffPerformance } from '../../models/staff-performance.model';
import { staffAppraisal, staffCheckpoint, staffDashboard, staffPeriod, performanceEmployee } from './staff-performance-test-fixtures';
import { performanceReturnUrl } from './staff-performance-navigation';
import { AnnualPerformanceTrendComponent } from '../annual-appraisal/annual-performance-trend.component';

describe('Staff / My Performance dashboard', () => {
  let api: jasmine.SpyObj<StaffPerformanceService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let actor: string;
  beforeEach(() => {
    actor = performanceEmployee;
    api = jasmine.createSpyObj<StaffPerformanceService>('StaffPerformanceService', ['periods', 'dashboard', 'checkpoint', 'appraisal']);
    api.periods.and.returnValue(of([staffPeriod()])); api.dashboard.and.returnValue(of(staffDashboard()));
    api.checkpoint.and.returnValue(of(staffCheckpoint())); api.appraisal.and.returnValue(of(staffAppraisal()));
    params = new BehaviorSubject(convertToParamMap({})); query = new BehaviorSubject(convertToParamMap({}));
  });
  function create(staff = false) {
    const route = { snapshot: { data: { staffPerformance: staff }, get paramMap() { return params.value; }, get queryParamMap() { return query.value; } },
      paramMap: params, queryParamMap: query };
    TestBed.configureTestingModule({ imports: [StaffPerformanceComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), provideNoopAnimations(), { provide: StaffPerformanceService, useValue: api },
      { provide: ActivatedRoute, useValue: route }, { provide: AuthService, useValue: { get userId() { return actor; }, hasRole: () => false } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, CheckOutline, SearchOutline] }
    ] });
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', { ANNUAL_APPRAISAL: { PENDING: 'Pending' } }); translate.use('en');
    const fixture = TestBed.createComponent(StaffPerformanceComponent); fixture.detectChanges(); return fixture;
  }
  it('loads the signed-in employee and reuses score cards and the trend with zero distinct from Pending', () => {
    const fixture = create();
    expect(api.periods).toHaveBeenCalledOnceWith(performanceEmployee); expect(api.dashboard).toHaveBeenCalledOnceWith(performanceEmployee, 1);
    const values = [...fixture.nativeElement.querySelectorAll('.score-grid dd')].map((element: HTMLElement) => element.textContent?.trim());
    expect(values).toEqual(['0.00', 'Pending', 'Pending']);
    expect(fixture.nativeElement.querySelector('app-annual-performance-trend')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.kpi-table').textContent).toContain('Support Resolution');
    expect(fixture.nativeElement.querySelector('.kpi-table').textContent).toContain('4 / 5'); fixture.destroy();
  });
  it('keeps first-year employees and unstarted results understandable without inventing history', () => {
    const data = staffDashboard(); data.trend = []; data.checkpoints = []; data.defaultCheckpointId = null;
    data.scores = { ...data.scores, annualKpiScore: null }; api.dashboard.and.returnValue(of(data));
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('STAFF_PERFORMANCE.PENDING_HELP');
    expect(fixture.nativeElement.textContent).toContain('STAFF_PERFORMANCE.NO_HISTORY');
    expect(fixture.nativeElement.textContent).toContain('STAFF_PERFORMANCE.NO_KPI_RESULTS'); expect(api.checkpoint).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('preserves chart keyboard selection through ordinary page change detection', () => {
    const fixture = create(); const chart = fixture.debugElement.query(By.directive(AnnualPerformanceTrendComponent)).componentInstance;
    chart.selectPoint(0); const summary = chart.selectedSummary;
    fixture.detectChanges(); expect(chart.selectedSummary).toBe(summary); expect(summary).toContain('0.00%'); fixture.destroy();
  });
  it('shows a neutral no-period empty state without requesting or creating assessments', () => {
    api.periods.and.returnValue(of([])); const fixture = create();
    expect(fixture.componentInstance.empty).toBeTrue(); expect(api.dashboard).not.toHaveBeenCalled(); expect(api.checkpoint).not.toHaveBeenCalled(); fixture.destroy();
  });
  it('uses authorised direct-link employee, period and participant context', () => {
    actor = '22222222-2222-2222-2222-222222222222';
    params.next(convertToParamMap({ employeeId: performanceEmployee }));
    query.next(convertToParamMap({ reviewPeriodId: '1', participantId: '11', returnUrl: '/appraisals/review/30?notice=71' }));
    const fixture = create(true); expect(fixture.componentInstance.returnUrl).toBe('/appraisals/review/30?notice=71');
    expect(fixture.componentInstance.returnLink.queryParams['notice']).toBe('71');
    expect(api.dashboard).toHaveBeenCalledOnceWith(performanceEmployee, 1); expect(fixture.componentInstance.isSelf).toBeFalse(); fixture.destroy();
  });
  it('rejects malformed identifiers, missing period context and mismatched participants instead of selecting another period', () => {
    query.next(convertToParamMap({ reviewPeriodId: '1', participantId: '99' }));
    const fixture = create(); expect(fixture.componentInstance.error).toBeTrue(); expect(api.dashboard).not.toHaveBeenCalled();
    query.next(convertToParamMap({ reviewPeriodId: '1.5' })); fixture.detectChanges(); expect(fixture.componentInstance.error).toBeTrue();
    query.next(convertToParamMap({ participantId: '11' })); fixture.detectChanges(); expect(fixture.componentInstance.error).toBeTrue(); fixture.destroy();
  });
  it('clears previous employee data when the backend denies access or returns mismatched data', () => {
    const fixture = create(); const page = fixture.componentInstance;
    api.dashboard.and.returnValue(throwError(() => ({ status: 403 }))); page.refreshDetails(); fixture.detectChanges();
    expect(page.data).toBeNull(); expect(fixture.nativeElement.textContent).not.toContain('Bob Smith');
    api.dashboard.and.returnValue(of({ ...staffDashboard(), employee: { ...staffDashboard().employee, id: 'someone-else' } }));
    page.refreshDetails(); fixture.detectChanges(); expect(page.data).toBeNull(); expect(page.error).toBeTrue(); fixture.destroy();
  });
  it('refreshes current server results and prevents an older period response from replacing the selection', () => {
    const first = new Subject<StaffPerformance>(); const second = new Subject<StaffPerformance>();
    api.periods.and.returnValue(of([staffPeriod(), staffPeriod(2)]));
    api.dashboard.and.callFake((_, id) => id === 1 ? first : second);
    const fixture = create(); query.next(convertToParamMap({ reviewPeriodId: '2' })); fixture.detectChanges();
    first.next(staffDashboard()); expect(fixture.componentInstance.data).toBeNull();
    second.next(staffDashboard(staffPeriod(2))); second.complete(); fixture.detectChanges();
    expect(fixture.componentInstance.data?.period.id).toBe(2);
    const fresh = staffDashboard(staffPeriod(2)); fresh.scores.finalScore = 81;
    api.dashboard.and.returnValue(of(fresh)); fixture.componentInstance.refreshDetails(); fixture.detectChanges();
    expect(fixture.componentInstance.data?.scores.finalScore).toBe(81); fixture.destroy();
  });
  it('changes review period through router state and clears stale participant context', () => {
    const fixture = create(); const router = TestBed.inject(Router); const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    fixture.componentInstance.changePeriod(2);
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { reviewPeriodId: 2, participantId: null }, queryParamsHandling: 'merge' })); fixture.destroy();
  });
  it('opens approved outcomes with recorded scores and omits confidential fields from self views', async () => {
    const outcome = staffAppraisal(); outcome.internalDetails = { recommendation: 'PROMOTION', justification: 'PRIVATE JUSTIFICATION', overrideReason: 'PRIVATE REASON', reviewedLate: false, recordedPerformance: null };
    const data = staffDashboard(); data.approvedAppraisal = outcome; api.dashboard.and.returnValue(of(data)); api.appraisal.and.returnValue(of(outcome));
    const fixture = create(); fixture.componentInstance.openAppraisal(outcome); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(api.appraisal).toHaveBeenCalledOnceWith(11, 31); expect(fixture.componentInstance.internalAppraisal).toBeNull();
    const drawer = document.querySelector('.ant-drawer-body');
    expect(drawer?.textContent).not.toContain('PRIVATE JUSTIFICATION'); expect(drawer?.textContent).not.toContain('PRIVATE REASON');
    expect(drawer?.textContent).toContain('75.00'); fixture.destroy();
  });
  it('limits internal recorded appraisal details to permitted other-employee views', () => {
    actor = '22222222-2222-2222-2222-222222222222'; params.next(convertToParamMap({ employeeId: performanceEmployee }));
    const data = staffDashboard(); data.capabilities.canViewInternalAppraisal = true;
    const outcome = staffAppraisal(); outcome.internalDetails = { recommendation: 'PROMOTION', justification: 'Recorded justification', overrideReason: 'Recorded override', reviewedLate: false, recordedPerformance: null };
    api.dashboard.and.returnValue(of(data)); api.appraisal.and.returnValue(of(outcome));
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome);
    expect(fixture.componentInstance.internalAppraisal?.justification).toBe('Recorded justification'); fixture.destroy();
  });
  it('closes and cancels pending historical detail requests without stale content', () => {
    const pending = new Subject<PerformanceApprovedAppraisal>(); api.appraisal.and.returnValue(pending);
    const fixture = create(); const page = fixture.componentInstance; page.openAppraisal(staffAppraisal()); page.closeDrawer();
    pending.next(staffAppraisal()); expect(page.drawer).toBeNull(); expect(page.appraisal).toBeNull(); fixture.destroy();
  });
  it('shows assessment history and completed feedback in read-only drawers', async () => {
    const fixture = create(); const page = fixture.componentInstance; page.drawer = 'history'; fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    let content = document.querySelector('.ant-drawer-body'); expect(content?.textContent).toContain('Submitted self comment');
    expect(content?.textContent).toContain('Submitted feedback'); expect(content?.querySelector('textarea')).toBeNull();
    page.closeDrawer(); page.drawer = 'attitude'; fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    content = document.querySelector('.ant-drawer-body'); expect(content?.textContent).toContain('Integrity');
    expect(content?.textContent).toContain('Self reflection'); expect(content?.textContent).toContain('Pending'); fixture.destroy();
  });
  it('keeps safe return links local and within the appraisal workspaces', () => {
    for (const value of ['https://evil.test', '//evil.test', '/\\evil.test', '/login', '/appraisals/review/1#unsafe'])
      expect(performanceReturnUrl(value)).toBe('/my-performance/dashboard');
    expect(performanceReturnUrl('/team-performance/team-appraisals/2/7')).toBe('/team-performance/team-appraisals/2/7');
  });
});
