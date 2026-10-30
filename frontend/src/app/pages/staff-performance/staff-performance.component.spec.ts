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
import { staffAppraisal, staffCheckpoint, staffDashboard, staffPeriod, performanceEmployee, staffRecordedAppraisal } from './staff-performance-test-fixtures';
import { performanceReturnUrl } from './staff-performance-navigation';
import { AnnualPerformanceTrendComponent } from '../annual-appraisal/annual-performance-trend.component';
import { NzSelectComponent } from 'ng-zorro-antd/select';
import { NgModel } from '@angular/forms';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

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
    const translate = TestBed.inject(TranslateService); translate.setTranslation('en', {
      ANNUAL_APPRAISAL: { PENDING: 'Pending', FREQUENCY: 'KPI Review Frequency', CONSOLIDATION: 'Annual KPI calculation', FREQUENCIES: { QUARTERLY: 'Quarterly' }, METHODS: { AVERAGE: 'Average' }, RESULT_STATUS: { REVIEWED: 'Reviewed', PENDING_REVIEW: 'Pending Superior Review', NOT_STARTED: 'Not Started' } },
      REVIEW_PERIOD: { STATUS_LABEL: 'Status' },
      STAFF_PERFORMANCE: { APPROVED_OUTCOME: 'Approved Appraisal - Selected Review Period', KPI_HELP: 'See KPI results here. Open Assessment History for detailed score calculations.', SELECTED_CHECKPOINT: 'Selected Review Checkpoint', CHECKPOINT_DATE: 'Review Checkpoint', ASSESSMENT_STATUS: 'Assessment Status', VIEW: 'View', HIDE: 'Hide' },
      KPI_SCORE_BREAKDOWN: { STATUS: { PENDING: 'Pending', NOT_APPLICABLE: 'Not Applicable' } }
    }); translate.use('en');
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
    expect(fixture.nativeElement.querySelector('#performance-kpis .checkpoint-metrics')?.textContent).toContain('Pending');
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
  it('opens recorded KPI scoring definitions in a modal instead of expanding them in the drawer', async () => {
    const details = staffCheckpoint(); const original = structuredClone(details); api.checkpoint.and.returnValue(of(details));
    const fixture = create(); fixture.componentInstance.drawer = 'history'; const body = await drawer(fixture);
    expect(body.querySelector('details.item-scoring, .answer-card details')).toBeNull();
    body.querySelector<HTMLButtonElement>('.answer-card .scoring-action')!.click();
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.componentInstance.scoringKpi?.name).toBe('Support Resolution');
    expect(document.querySelector('.ant-modal-body')?.textContent).toContain('Meets expectation');
    expect(document.querySelector('.ant-modal-body')?.textContent).toContain('4 / 5');
    fixture.componentInstance.closeScoringDefinitions(); fixture.detectChanges();
    expect(fixture.componentInstance.scoringKpi).toBeNull(); expect(details).toEqual(original); fixture.destroy();
  });
  it('keeps safe return links local and within the appraisal workspaces', () => {
    for (const value of ['https://evil.test', '//evil.test', '/\\evil.test', '/login', '/appraisals/review/1#unsafe'])
      expect(performanceReturnUrl(value)).toBe('/my-performance/dashboard');
    expect(performanceReturnUrl('/team-performance/team-appraisals/2/7')).toBe('/team-performance/team-appraisals/2/7');
  });

  async function drawer(fixture: ReturnType<typeof create>) {
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    return document.querySelector<HTMLElement>('.ant-drawer-open .ant-drawer-body')!;
  }
  function permittedAppraisal(outcome = staffRecordedAppraisal()) {
    actor = '22222222-2222-2222-2222-222222222222'; params.next(convertToParamMap({ employeeId: performanceEmployee }));
    const data = staffDashboard(); data.capabilities.canViewInternalAppraisal = true; data.approvedAppraisal = outcome;
    api.dashboard.and.returnValue(of(data)); api.appraisal.and.returnValue(of(outcome)); return { data, outcome };
  }

  it('labels only the selected period approval and never promotes older history to that outcome card', () => {
    const data = staffDashboard(); data.approvedAppraisal = staffAppraisal(); api.dashboard.and.returnValue(of(data));
    const fixture = create();
    expect(fixture.nativeElement.querySelector('#approved-outcome-title').textContent).toContain('Selected Review Period');
    expect(fixture.nativeElement.querySelector('.outcome-period').textContent).toContain(data.period.name);
    data.approvedAppraisal = null; data.appraisalHistory = [{ ...staffAppraisal(), period: staffPeriod(0) }];
    fixture.componentInstance.refreshDetails(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.approved-outcome')).toBeNull();
    expect(fixture.nativeElement.querySelector('#performance-appraisals').textContent).toContain(staffPeriod(0).name); fixture.destroy();
  });

  it('shows a single checkpoint directly without redundant selectors or View Checkpoint actions', async () => {
    const fixture = create(); fixture.componentInstance.drawer = 'history'; const body = await drawer(fixture);
    const overview = fixture.nativeElement.querySelector('#performance-kpis .checkpoint-overview') as HTMLElement;
    expect(overview.querySelector('.checkpoint-choice')).toBeNull();
    expect(overview.querySelector('.single-checkpoint')?.textContent).toContain('Review Checkpoint');
    expect(overview.querySelector('.single-checkpoint')?.textContent).toContain('31 Dec 2026');
    expect(overview.querySelectorAll('.checkpoint-metrics > div').length).toBe(4);
    expect(overview.querySelector('.calculation-method')).toBeNull();
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('0.00%');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('Assessment Status');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('Reviewed');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('KPI Review Frequency');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('Quarterly');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('Annual KPI calculation');
    expect(overview.querySelector('.checkpoint-metrics')?.textContent).toContain('Average');
    expect(fixture.nativeElement.querySelector('#performance-kpis .card-head .help').textContent).toContain('Assessment History for detailed score calculations');
    expect(body.querySelector('nz-select')).toBeNull(); expect(body.textContent).not.toContain('STAFF_PERFORMANCE.VIEW_CHECKPOINT');
    expect(body.querySelector('.selected-checkpoint')?.textContent).toContain('31 Dec 2026');
    expect(body.querySelector('.checkpoint-total')?.textContent).toContain('0.00%');
    expect(body.querySelectorAll('.breakdown-summary th').length).toBe(4); fixture.destroy();
  });

  it('uses a compact selector for multiple checkpoints and keeps page/history selection synchronised', async () => {
    const data = staffDashboard(); data.checkpoints.push({ ...data.checkpoints[0], id: 102, sequenceNumber: 2, startDate: '2026-07-01', endDate: '2026-09-30' });
    api.dashboard.and.returnValue(of(data)); api.checkpoint.and.callFake((_, id) => of({ ...staffCheckpoint(), checkpoint: data.checkpoints.find(c => c.id === id)! }));
    const fixture = create(); fixture.componentInstance.drawer = 'history'; await drawer(fixture);
    expect(fixture.nativeElement.querySelector('label[for="performance-checkpoint"]')?.textContent).toContain('Selected Review Checkpoint');
    expect(fixture.nativeElement.querySelector('#performance-kpis .single-checkpoint')).toBeNull();
    const select = fixture.debugElement.queryAll(By.directive(NzSelectComponent)).find(el => el.componentInstance.nzId === 'history-checkpoint')!;
    select.componentInstance.updateListOfValue([102]); const body = await drawer(fixture);
    expect(fixture.componentInstance.checkpointId).toBe(102); expect(api.checkpoint).toHaveBeenCalledWith(11, 102);
    expect(body.querySelector('#history-checkpoint')?.closest('nz-select')?.textContent).toContain('30 Sep 2026');
    expect(fixture.debugElement.queryAll(By.directive(NzSelectComponent)).find(el => el.componentInstance.nzId === 'performance-checkpoint')?.injector.get(NgModel).model).toBe(102);
    expect(fixture.nativeElement.querySelector('#performance-kpis .checkpoint-metrics')?.textContent).toContain('0.00%');
    expect(body.textContent).not.toContain('STAFF_PERFORMANCE.VIEW_CHECKPOINT'); fixture.destroy();
  });

  it('keeps the checkpoint summary full-width on desktop and stacks it without horizontal overflow on mobile', () => {
    const fixture = create(); const overview = fixture.nativeElement.querySelector('#performance-kpis .checkpoint-overview') as HTMLElement;
    for (const [width, columns, metricColumns] of [[1440, 2, 4], [768, 1, 4], [360, 1, 1]]) {
      const layout = reviewLayout(overview, width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(layout.window.getComputedStyle(layout.root).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(columns);
        expect(layout.window.getComputedStyle(layout.root.querySelector('.checkpoint-metrics')!).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(metricColumns);
      } finally { layout.remove(); }
    }
    fixture.destroy();
  });

  it('clears stale checkpoint content during selection and retains a retry after errors', async () => {
    const fixture = create(); const page = fixture.componentInstance; page.drawer = 'history';
    const response = new Subject<ReturnType<typeof staffCheckpoint>>(); api.checkpoint.and.returnValue(response);
    page.selectCheckpoint(101); let body = await drawer(fixture);
    expect(body.querySelector('.answer-card')).toBeNull(); expect(body.querySelector('.checkpoint-total')?.textContent).toContain('Pending');
    expect(fixture.nativeElement.querySelector('#performance-kpis .score-metric')?.textContent).toContain('Pending');
    response.error({ status: 403 }); body = await drawer(fixture);
    expect(body.textContent).toContain('STAFF_PERFORMANCE.DETAIL_UNAVAILABLE'); expect(body.textContent).not.toContain('Submitted feedback');
    expect(fixture.nativeElement.querySelector('#performance-kpis [role="alert"] button')).not.toBeNull(); fixture.destroy();
  });

  it('shows level contributions only in Assessment History without modifying returned scores', async () => {
    const details = staffCheckpoint(); const original = structuredClone(details); api.checkpoint.and.returnValue(of(details));
    const fixture = create(); expect(fixture.nativeElement.querySelector('#performance-kpis .breakdown-summary')).toBeNull();
    expect(fixture.nativeElement.querySelector('#performance-kpis .kpi-table').textContent).toContain('Support Resolution');
    fixture.componentInstance.drawer = 'history'; const body = await drawer(fixture);
    expect(body.querySelector('.breakdown-summary')?.textContent).toContain('56.00%');
    expect(body.querySelector('.calculation-help')?.textContent).toContain('STAFF_PERFORMANCE.CONTRIBUTION_HELP');
    expect(body.querySelector('.self-answers')).not.toBeNull(); expect(body.querySelector('.superior-answers')).not.toBeNull();
    expect(body.querySelector('.self-answers .rating-label')?.textContent).toContain('KPI_SCORING_GUIDE.RATING.3.LABEL');
    expect(details).toEqual(original); fixture.destroy();
  });

  it('shows the actual Attitude format, score and status in shared compact cards and preserves configured rating labels', async () => {
    const data = staffDashboard(); data.attitude.format = 'SALES'; data.attitude.score = 0; data.attitude.status = 'REVIEWED';
    data.attitude.ratingDefinitions = [{ point: 4, label: 'Configured Attitude label', description: 'Guide detail' }];
    api.dashboard.and.returnValue(of(data)); const fixture = create();
    const cards = fixture.nativeElement.querySelector('#performance-attitude .attitude-summary');
    expect(cards.querySelectorAll('dt').length).toBe(3); expect(cards.textContent).toContain('ATTITUDE_SETUP.FORMAT.SALES');
    expect(cards.querySelectorAll('dt')[2].textContent).toBe('Status');
    expect(cards.textContent).not.toContain('Appraisal status');
    expect(cards.textContent).not.toContain('Annual Values'); expect(cards.textContent).toContain('0.00%');
    fixture.componentInstance.drawer = 'attitude'; const body = await drawer(fixture);
    expect(body.querySelector('.attitude-summary')?.textContent).toContain('ATTITUDE_SETUP.FORMAT.SALES');
    expect(body.querySelector('.self-answers .rating-label')?.textContent).toBe('Configured Attitude label');
    expect(body.querySelector('.superior-answers .rating-label')).toBeNull();
    expect(body.querySelector('textarea, input[type=radio]')).toBeNull(); fixture.destroy();
  });

  it('renders recorded scores once and snapshot-backed result tables with optional version references', async () => {
    const { outcome } = permittedAppraisal(); const original = structuredClone(outcome);
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.querySelectorAll('app-annual-appraisal-performance').length).toBe(1);
    expect([...body.querySelectorAll('.score-grid dd')].map(n => n.textContent?.trim())).toEqual(['71.00', '80.00', '75.50']);
    const details = body.querySelector<HTMLDetailsElement>('.recorded-performance')!;
    expect(details.open).toBeFalse(); details.querySelector('summary')!.click(); fixture.detectChanges(); expect(details.open).toBeTrue();
    expect(details.querySelector('.recorded-metadata')?.textContent).toContain('ANNUAL_APPRAISAL.METHODS.FINAL_CHECKPOINT');
    expect(details.querySelector('.recorded-metadata')?.textContent).not.toContain('ANNUAL_APPRAISAL.METHODS.AVERAGE');
    expect(body.querySelectorAll('.recorded-level .level-results-table').length).toBe(3);
    expect(details.textContent).toContain('Recorded Company achievement'); expect(details.textContent).toContain('Recorded Department achievement');
    const metadata = body.querySelector<HTMLDetailsElement>('.result-metadata')!;
    expect(metadata.open).toBeFalse(); expect(metadata.textContent).toContain('903'); expect(metadata.textContent).toContain('01 Jul 2026');
    expect(body.querySelector('.item-scoring')).toBeNull();
    expect(body.querySelectorAll('.recorded-level .scoring-action').length).toBe(3);
    expect(body.textContent).toContain('Recorded recommendation justification'); expect(body.textContent).toContain('Recorded HR override');
    expect(body.textContent).toContain('STAFF_PERFORMANCE.CORRECTED_HELP'); expect(outcome).toEqual(original); fixture.destroy();
  });

  it('opens scoring definitions from the recorded appraisal item without loading current replacements', async () => {
    const { outcome } = permittedAppraisal(); const original = structuredClone(outcome);
    const fixture = create(true); const currentCalls = api.checkpoint.calls.count();
    fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    body.querySelector<HTMLDetailsElement>('.recorded-performance')!.open = true;
    body.querySelector<HTMLButtonElement>('.recorded-level .scoring-action')!.click();
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.componentInstance.scoringKpi?.name).toBe('Recorded Revenue');
    expect(document.querySelector('.ant-modal-body')?.textContent).toContain('Meets expectation');
    expect(api.checkpoint.calls.count()).toBe(currentCalls); expect(outcome).toEqual(original);
    fixture.componentInstance.closeDrawer(); expect(fixture.componentInstance.scoringKpi).toBeNull(); fixture.destroy();
  });

  it('keeps historical checkpoint selection local to the recorded snapshot and never loads current replacements', async () => {
    const { outcome } = permittedAppraisal(staffRecordedAppraisal(staffPeriod(0)));
    const snapshot = outcome.internalDetails!.recordedPerformance!;
    snapshot.checkpoints.push({ ...snapshot.checkpoints[0], checkpointId: 302, sequenceNumber: 2, score: 60 });
    const fixture = create(true); const page = fixture.componentInstance; const currentCalls = api.checkpoint.calls.count();
    page.openAppraisal(outcome); await drawer(fixture); expect(page.recordedCheckpointId).toBe(302);
    const select = fixture.debugElement.queryAll(By.directive(NzSelectComponent)).find(el => el.componentInstance.nzId === 'recorded-checkpoint')!;
    select.componentInstance.updateListOfValue([301]); const body = await drawer(fixture);
    expect(page.recordedCheckpointId).toBe(301); expect(page.checkpointId).toBe(101);
    expect(body.textContent).toContain(staffPeriod(0).name); expect(api.checkpoint.calls.count()).toBe(currentCalls);
    page.selectRecordedCheckpoint(999); expect(page.recordedCheckpointId).toBe(301);
    page.closeDrawer(); expect(page.recordedCheckpointId).toBeNull(); fixture.destroy();
  });

  it('shows missing recorded details without substituting current results and retains version references', async () => {
    const { outcome } = permittedAppraisal(); outcome.internalDetails!.recordedPerformance!.checkpoints[0].breakdown = null;
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.textContent).toContain('STAFF_PERFORMANCE.RECORDED_DETAILS_UNAVAILABLE'); expect(body.querySelector('.level-results-table')).toBeNull();
    expect(body.querySelector('.result-metadata')?.textContent).toContain('903');
    fixture.destroy();
  });

  it('does not replace missing recorded scores with selected-period scores', async () => {
    const { data, outcome } = permittedAppraisal(); data.scores.finalScore = 99; outcome.recordedScores = null;
    outcome.comparisonStatus = 'CURRENT_RESULTS_UNAVAILABLE';
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.querySelector('.score-grid')).toBeNull(); expect(body.textContent).toContain('STAFF_PERFORMANCE.RECORDED_SCORES_UNAVAILABLE');
    expect(body.textContent).toContain('STAFF_PERFORMANCE.CURRENT_UNAVAILABLE'); expect(body.textContent).not.toContain('99.00'); fixture.destroy();
  });

  it('handles older snapshots with absent checkpoint arrays without repairing or replacing them', async () => {
    const { outcome } = permittedAppraisal(); const snapshot = outcome.internalDetails!.recordedPerformance!;
    Object.assign(snapshot, { checkpoints: null });
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.textContent).toContain('STAFF_PERFORMANCE.RECORDED_DETAILS_UNAVAILABLE');
    expect(fixture.componentInstance.recordedCheckpointId).toBeNull(); expect(snapshot.checkpoints).toBeNull();
    expect(body.querySelector('.score-grid')?.textContent).toContain('71.00'); fixture.destroy();
  });

  it('retains available recorded results when older snapshots omit version binding arrays', async () => {
    const { outcome } = permittedAppraisal(); const snapshot = outcome.internalDetails!.recordedPerformance!;
    Object.assign(snapshot.checkpoints[0], { versions: null });
    const fixture = create(true); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.querySelectorAll('.level-results-table').length).toBe(3);
    expect(body.textContent).toContain('Recorded Company achievement'); expect(snapshot.checkpoints[0].versions).toBeNull(); fixture.destroy();
  });

  it('keeps internal snapshot details hidden even when an employee response unexpectedly includes them', async () => {
    const outcome = staffRecordedAppraisal(); const data = staffDashboard(); data.capabilities.canViewInternalAppraisal = true;
    api.dashboard.and.returnValue(of(data)); api.appraisal.and.returnValue(of(outcome));
    const fixture = create(); fixture.componentInstance.openAppraisal(outcome); const body = await drawer(fixture);
    expect(body.querySelector('.recorded-performance')).toBeNull(); expect(body.textContent).not.toContain('Recorded Company achievement');
    expect(body.textContent).not.toContain('Recorded recommendation justification'); expect(body.textContent).not.toContain('Recorded HR override');
    expect(body.querySelectorAll('app-annual-appraisal-performance').length).toBe(1); fixture.destroy();
  });

  it('contains tables, wraps long feedback and stacks read-only panels at narrow viewports', async () => {
    const data = staffDashboard(); data.attitude.criteria[0].selfComment = 'Long feedback\n' + 'word'.repeat(180);
    api.dashboard.and.returnValue(of(data)); const fixture = create(); fixture.componentInstance.drawer = 'attitude'; const body = await drawer(fixture);
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(body, width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        const panel = layout.root.querySelector('.self-answers')!;
        expect(layout.window.getComputedStyle(panel.querySelector('.saved-comment')!).whiteSpace).toBe('pre-wrap');
        expect(layout.window.getComputedStyle(layout.root.querySelector('.superior-answers')!).borderLeftWidth).toBe(width < 600 ? '0px' : '1px');
      } finally { layout.remove(); }
    }
    fixture.destroy();
  });

  it('keeps recorded disclosures keyboard-focusable and wide result tables inside their own scrolling containers', async () => {
    const { outcome } = permittedAppraisal(); const fixture = create(true); fixture.componentInstance.openAppraisal(outcome);
    const body = await drawer(fixture); const disclosure = body.querySelector<HTMLDetailsElement>('.recorded-performance')!;
    const summary = disclosure.querySelector('summary')!; summary.focus(); expect(document.activeElement).toBe(summary);
    expect(summary.querySelector('.view-label')?.textContent).toContain('View');
    expect(getComputedStyle(summary.querySelector('.hide-label')!).display).toBe('none');
    expect(getComputedStyle(summary).listStyleType).toBe('none');
    summary.click(); fixture.detectChanges(); expect(disclosure.open).toBeTrue();
    expect(getComputedStyle(summary.querySelector('.view-label')!).display).toBe('none');
    expect(summary.querySelector('.hide-label')?.textContent).toContain('Hide');
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(body, width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        for (const table of layout.root.querySelectorAll('.level-results-table, .breakdown-summary'))
          expect(layout.window.getComputedStyle(table.closest('.table-scroll')!).overflowX).toBe('auto');
      } finally { layout.remove(); }
    }
    expect(body.querySelector('textarea, input[type=radio]')).toBeNull(); fixture.destroy();
  });
});
