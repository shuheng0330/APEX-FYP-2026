import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, throwError } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TeamReviewsComponent } from './team-reviews.component';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { AuthService } from '../../services/auth.service';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiItem, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';
import { TeamReviewPeriodService } from '../../services/team-review-period.service';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { revisionFixture } from './kpi-revision.test-data';
import { reviewLayout } from './review-navigation.test-layout';

describe('Team Reviews', () => {
  let fixture: ComponentFixture<TeamReviewsComponent>;
  let component: TeamReviewsComponent;
  let api: jasmine.SpyObj<IndividualKpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  let auth: jasmine.SpyObj<AuthService>;
  let assessments: jasmine.SpyObj<KpiAssessmentService>;
  let attitudes: jasmine.SpyObj<AttitudeAssessmentService>;
  let periods: jasmine.SpyObj<TeamReviewPeriodService>;
  let revisions: jasmine.SpyObj<KpiRevisionService>;
  const item: KpiItem = { ...emptyKpiItem(), name: 'Customers', perspective: 'Customer', kra: 'Customer growth',
    target: '10 new customers', weightage: 100, scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } };
  const plan = (status: KpiPlan['status'] = 'PENDING_APPROVAL', id = 10): KpiPlan => ({
    id, reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', level: 'INDIVIDUAL',
    ownerParticipantId: 7, employeeName: 'Amir', departmentName: 'Retail Sales', status, items: [item],
    kpiSetupDeadline: '2028-01-31', totalWeightage: 100, overdue: false, createdAt: '2028-01-01', updatedAt: '2028-01-01'
  });
  beforeEach(async () => {
    api = jasmine.createSpyObj<IndividualKpiPlanService>('api', ['reviews', 'get', 'approve', 'returnForRevision']);
    api.reviews.and.returnValue(of([plan(), plan('APPROVED', 11), plan('RETURNED', 12)]));
    api.get.and.callFake(id => of(plan(id === 10 ? 'PENDING_APPROVAL' : id === 11 ? 'APPROVED' : 'RETURNED', id)));
    api.approve.and.returnValue(of(plan('APPROVED'))); api.returnForRevision.and.returnValue(of({ ...plan('RETURNED'), returnReason: 'Clarify target' }));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole']);
    auth.hasRole.and.callFake(permission => permission === 'CAN_REVIEW_INDIVIDUAL_KPI');
    assessments = jasmine.createSpyObj<KpiAssessmentService>('assessments', ['reviews']); assessments.reviews.and.returnValue(of([]));
    attitudes = jasmine.createSpyObj<AttitudeAssessmentService>('attitudes', ['reviews']); attitudes.reviews.and.returnValue(of([]));
    periods = jasmine.createSpyObj<TeamReviewPeriodService>('periods', ['periods']);
    revisions = jasmine.createSpyObj<KpiRevisionService>('revisions', ['list', 'get']);
    revisions.list.and.returnValue(of([])); revisions.get.and.returnValue(of(revisionFixture({ level: 'INDIVIDUAL', baseVersionId: null })));
    periods.periods.and.returnValue(of({ periods: [
      { id: 1, name: '2028 Annual Review', status: 'OPEN', startDate: '2028-01-01', endDate: '2028-12-31', kpiSetupDeadline: null },
      { id: 2, name: '2027 Annual Review', status: 'CLOSED', startDate: '2027-01-01', endDate: '2027-12-31', kpiSetupDeadline: null }
    ], defaultPeriodId: 1 }));
    TestBed.configureTestingModule({ imports: [TeamReviewsComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([{ path: 'team-performance/team-reviews', component: TeamReviewsComponent }]), { provide: IndividualKpiPlanService, useValue: api },
      { provide: AuthService, useValue: auth }, { provide: KpiAssessmentService, useValue: assessments },
      { provide: AttitudeAssessmentService, useValue: attitudes },
      { provide: TeamReviewPeriodService, useValue: periods },
      { provide: KpiRevisionService, useValue: revisions },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }, { provide: NzModalService, useValue: modal }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(TeamReviewsComponent);
    component = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('uses the scoped default and preserves the selected period across tabs and refresh', async () => {
    expect(component.periodId).toBe(1); expect(periods.periods).toHaveBeenCalledTimes(1);
    await component.changePeriod(2); expect(component.selectedPeriod?.status).toBe('CLOSED');
    component.selectTab('assessments'); component.selectTab('reviews'); component.loadPeriods();
    expect(component.periodId).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('nz-select').length).toBe(1);
  });
  it('does not show unrelated queues when no default period exists', () => {
    periods.periods.and.returnValue(of({ periods: component.periods, defaultPeriodId: null }));
    component.periodId = null; component.loadPeriods();
    expect(component.visiblePlans).toEqual([]); expect(component.periodId).toBeNull();
  });
  it('passes the shared period to assessment tabs and protects unsaved review answers', async () => {
    auth.hasRole.and.returnValue(true); component.selectTab('assessments'); await fixture.whenStable(); fixture.detectChanges();
    const child = component.assessmentWorkspace!;
    expect(child.periodId).toBe(1); expect(child.sharedPeriod).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('nz-select').length).toBe(1);
    const leave = spyOn(child, 'canLeave').and.returnValue(Promise.resolve(false));
    await component.changePeriod(2); expect(component.periodId).toBe(1);
    leave.and.returnValue(Promise.resolve(true)); await component.changePeriod(2); fixture.detectChanges();
    expect(child.periodId).toBe(2); expect(child.selected).toBeNull();
    component.selectTab('attitude'); await fixture.whenStable(); fixture.detectChanges();
    expect(component.attitudeWorkspace?.periodId).toBe(2); expect(component.attitudeWorkspace?.sharedPeriod).toBeTrue();
  });
  it('shows preserved return feedback for resubmitted plans', () => {
    api.get.and.returnValue(of({ ...plan(), latestReturnReason: 'Clarify your target', latestReturnedByName: 'Alice',
      returnComparisonAvailable: true, changesSinceReturn: [{ type: 'EDITED', itemId: 1, itemName: 'Customers',
        fields: [{ field: 'target', previousValue: '10 customers', currentValue: '20 customers' }] }] }));
    component.open(10); fixture.detectChanges();
    const content = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(content.textContent).toContain('Clarify your target'); expect(content.textContent).toContain('10 customers');
    expect(content.textContent).toContain('20 customers'); expect(content.textContent).toContain('Alice');
  });
  it('reloads approved/returned history and filters by status, employee and period', () => {
    expect(api.reviews).toHaveBeenCalledTimes(1); expect(component.count('APPROVED')).toBe(1);
    component.filter = 'RETURNED'; expect(component.visiblePlans[0].id).toBe(12);
    component.search = 'other'; expect(component.visiblePlans).toEqual([]);
    component.search = 'Retail'; expect(component.visiblePlans.length).toBe(1);
    component.periodId = 2; expect(component.visiblePlans).toEqual([]);
    expect(fixture.nativeElement.textContent).not.toContain('KPI Assessment');
  });
  it('places brief approval guidance above the status buttons with breathing room', () => {
    const intro: HTMLElement = fixture.nativeElement.querySelector('.team-review-intro');
    expect(intro.querySelector('.help')?.textContent).toContain('TEAM_REVIEWS.APPROVAL_HELP');
    expect(getComputedStyle(intro).marginBottom).toBe('20px');
    expect(intro.nextElementSibling?.classList.contains('review-status-tabs')).toBeTrue();
  });
  it('opens the full plan read-only with five criteria', () => {
    component.open(10); fixture.detectChanges();
    const body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.textContent).toContain('Amir'); expect(body.textContent).toContain('Customer growth');
    expect(body.textContent).toContain('10 new customers'); expect(body.querySelectorAll('.criterion-value').length).toBe(5);
    expect(body.querySelector('input')).toBeNull();
  });
  it('confirms approval and moves the plan out of the pending queue', () => {
    component.open(10); component.confirmApprove(); expect(api.approve).not.toHaveBeenCalled(); confirm();
    expect(api.approve).toHaveBeenCalledOnceWith(10); expect(component.canDecide).toBeFalse();
    expect(component.count('PENDING_APPROVAL')).toBe(0); expect(component.count('APPROVED')).toBe(2);
  });
  it('requires a nonblank return reason and keeps it visible after returning', () => {
    component.open(10); component.beginReturn(); component.returnReason = '   '; component.returnForRevision();
    expect(component.reasonError).toBeTrue(); expect(api.returnForRevision).not.toHaveBeenCalled();
    component.returnReason = ' Clarify target '; component.returnForRevision();
    expect(api.returnForRevision).toHaveBeenCalledOnceWith(10, 'Clarify target'); expect(component.selected?.returnReason).toBe('Clarify target');
    expect(component.canDecide).toBeFalse();
  });
  it('does not allow actions on Approved, Returned or Closed plans', () => {
    for (const status of ['APPROVED', 'RETURNED'] as const) {
      component.selected = plan(status); component.confirmApprove(); component.beginReturn(); expect(component.canDecide).toBeFalse();
    }
    component.selected = { ...plan(), reviewPeriodStatus: 'CLOSED' }; component.approve(); expect(component.canDecide).toBeFalse();
    expect(api.approve).not.toHaveBeenCalled(); expect(component.returnMode).toBeFalse();
  });
  it('retains the plan when a review fails and disables decisions if access changed', () => {
    component.open(10); api.approve.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } })));
    component.approve(); expect(component.error).toBe('Please retry'); expect(component.canDecide).toBeTrue();
    api.approve.and.returnValue(throwError(() => ({ status: 403 }))); component.approve();
    expect(component.canDecide).toBeFalse(); expect(component.error).toBe('TEAM_REVIEWS.ACCESS_CHANGED'); expect(component.busy).toBeFalse();
  });
  it('allows an assessment-only reviewer without loading Individual plan or assistance APIs', () => {
    api.reviews.calls.reset(); auth.hasRole.and.callFake(permission => permission === 'CAN_REVIEW_KPI_ASSESSMENT');
    component.ngOnInit(); fixture.detectChanges();
    expect(component.tab).toBe('assessments'); expect(api.reviews).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('app-kpi-scoring-guide')).toBeNull();
    expect(assessments.reviews).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs button').length).toBe(1);
    component.selectTab('assistance'); expect(component.tab).toBe('assessments');
  });
  it('keeps assessment and KPI plan responsibilities in separate permission-controlled tabs', async () => {
    component.selectTab('assessments'); expect(component.tab).toBe('reviews');
    auth.hasRole.and.returnValue(true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs button').length).toBe(5);
    component.selectTab('assessments'); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-kpi-scoring-guide')).toBeNull();
    component.assessmentWorkspace!.drawerVisible = true; component.selectTab('reviews');
    expect(component.tab).toBe('assessments'); expect(modal.confirm).toHaveBeenCalled();
    component.assessmentWorkspace!.drawerVisible = false; component.selectTab('reviews'); await fixture.whenStable(); fixture.detectChanges(); expect(component.tab).toBe('reviews');
    expect(fixture.nativeElement.querySelector('app-kpi-scoring-guide')).not.toBeNull();
  });
  it('opens only Attitude Evaluations for an attitude-only reviewer', () => {
    api.reviews.calls.reset(); auth.hasRole.and.callFake(permission => permission === 'CAN_REVIEW_ATTITUDE_EVALUATION');
    component.ngOnInit(); fixture.detectChanges();
    expect(component.tab).toBe('attitude'); expect(attitudes.reviews).toHaveBeenCalledTimes(1);
    expect(api.reviews).not.toHaveBeenCalled(); expect(assessments.reviews).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs button').length).toBe(1);
    const guideButton = fixture.nativeElement.querySelector('.page-heading button') as HTMLButtonElement;
    expect(guideButton.textContent).toContain('KPI_SCORING_GUIDE.VIEW');
    spyOn(component.attitudeWorkspace!, 'viewScoringGuide'); guideButton.click();
    expect(component.attitudeWorkspace!.viewScoringGuide).toHaveBeenCalled();
    component.selectTab('reviews'); component.selectTab('assessments'); expect(component.tab).toBe('attitude');
  });
  it('denies the attitude tab without authority and protects an open evaluation from tab/route changes', async () => {
    component.selectTab('attitude'); expect(component.tab).toBe('reviews'); expect(attitudes.reviews).not.toHaveBeenCalled();
    auth.hasRole.and.returnValue(true); component.selectTab('attitude'); await fixture.whenStable(); fixture.detectChanges();
    const workspace = component.attitudeWorkspace!;
    workspace.drawerVisible = true; component.selectTab('reviews'); expect(component.tab).toBe('attitude');
    spyOn(workspace, 'canLeave').and.returnValue(false); expect(component.canLeave()).toBeFalse();
    workspace.drawerVisible = false; (workspace.canLeave as jasmine.Spy).and.returnValue(true);
    component.selectTab('reviews'); await fixture.whenStable(); expect(component.tab).toBe('reviews');
  });
  it('scopes Individual revisions to the shared period without adding another period selector', async () => {
    component.selectTab('revisions'); await fixture.whenStable(); fixture.detectChanges();
    expect(component.revisionWorkspace?.superiorOnly).toBeTrue(); expect(component.revisionWorkspace?.sharedPeriod).toBeTrue();
    expect(revisions.list).toHaveBeenCalledWith(1, 'INDIVIDUAL');
    expect(fixture.nativeElement.querySelectorAll('.team-period-selector nz-select').length).toBe(1);
    expect(fixture.nativeElement.querySelector('app-kpi-revisions .plan-picker')?.textContent).not.toContain('2028 Annual Review');
    await component.changePeriod(2); fixture.detectChanges(); expect(revisions.list).toHaveBeenCalledWith(2, 'INDIVIDUAL');
  });
  it('protects revision edits from tab, route and period changes', async () => {
    component.selectTab('revisions'); fixture.detectChanges();
    const child = component.revisionWorkspace!; const workspace = child.workspace!;
    workspace.open(2); fixture.detectChanges();
    workspace.openItem(0); workspace.editorItems[0].target = 'Changed';
    component.selectTab('reviews'); expect(component.tab).toBe('revisions');
    spyOn(child, 'canLeave').and.returnValue(Promise.resolve(false)); expect(await component.canLeave()).toBeFalse();
    spyOn(workspace, 'close').and.returnValue(Promise.resolve());
    await component.changePeriod(2); expect(component.periodId).toBe(1);
  });
  it('does not infer Individual revision review from HR initiation or job role names', () => {
    auth.hasRole.and.callFake(p => ['CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS', 'MD', 'Super Admin'].includes(p));
    component.selectTab('revisions'); expect(component.tab).toBe('reviews'); expect(revisions.list).not.toHaveBeenCalled();
  });
  it('visibly groups all authorised tasks and removes empty groups', () => {
    auth.hasRole.and.returnValue(true); fixture.detectChanges();
    const groups = fixture.nativeElement.querySelectorAll('.workflow-group');
    expect(groups.length).toBe(2);
    expect(groups[0].querySelector('h3').textContent).toContain('REVIEW_NAV.KPI_PLANS');
    expect(groups[0].querySelectorAll('button').length).toBe(3);
    expect(groups[1].querySelector('h3').textContent).toContain('REVIEW_NAV.ASSESSMENTS');
    expect(groups[1].querySelectorAll('button').length).toBe(2);
    auth.hasRole.and.callFake(p => p === 'CAN_REVIEW_ATTITUDE_EVALUATION'); component.ngOnInit(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.workflow-group').length).toBe(1);
    expect(fixture.nativeElement.querySelector('#team-plan-group')).toBeNull();
  });
  it('wraps grouped tabs at desktop, tablet and mobile sizes without horizontal overflow', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('layout', { KPI_REVISIONS: { INDIVIDUAL_TITLE: 'Long translated Individual KPI revision label '.repeat(8) } });
    translate.use('layout');
    auth.hasRole.and.returnValue(true); fixture.detectChanges();
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(fixture.nativeElement.querySelector('main'), width);
      try {
        const nav = layout.root.querySelector<HTMLElement>('.workflow-tabs')!;
        expect(nav.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(nav.clientWidth);
        expect(layout.window.getComputedStyle(nav).flexDirection).toBe(width > 1200 ? 'row' : 'column');
        const secondGroup = nav.querySelectorAll('.workflow-group')[1];
        expect(layout.window.getComputedStyle(secondGroup)[width > 1200 ? 'borderLeftWidth' : 'borderTopWidth']).toBe('1px');
        for (const button of nav.querySelectorAll('button')) expect(button.getBoundingClientRect().width).toBeLessThanOrEqual(nav.clientWidth);
      } finally { layout.remove(); }
    }
    const button = fixture.nativeElement.querySelector('.workflow-tabs button') as HTMLButtonElement;
    button.focus(); expect(document.activeElement).toBe(button); expect(button.type).toBe('button');
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });
  it('preserves the active assessment tab and URL on period changes and guards simultaneous navigation', async () => {
    auth.hasRole.and.returnValue(true); component.selectTab('assessments'); await fixture.whenStable(); fixture.detectChanges();
    const child = component.assessmentWorkspace!;
    let resolve!: (allowed: boolean) => void;
    spyOn(child, 'canLeave').and.returnValue(new Promise<boolean>(r => resolve = r));
    const changing = component.changePeriod(2);
    component.selectTab('attitude'); expect(component.tab).toBe('assessments');
    expect(component.canLeave()).toBeFalse(); expect(component.periodChanging).toBeTrue();
    resolve(false); await changing;
    expect(component.periodId).toBe(1); expect(component.periodChanging).toBeFalse();
    expect(TestBed.inject(Router).url).toContain('tab=assessments');
    (child.canLeave as jasmine.Spy).and.returnValue(true);
    await component.changePeriod(2); fixture.detectChanges();
    expect(component.tab).toBe('assessments'); expect(child.periodId).toBe(2);
    expect(TestBed.inject(Router).url).toContain('tab=assessments');
  });
  it('loads a requested assessment tab without requesting the Individual approval queue', async () => {
    fixture.destroy(); api.reviews.calls.reset(); auth.hasRole.and.returnValue(true);
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/team-performance/team-reviews?tab=assessments', TeamReviewsComponent);
    expect(page.tab).toBe('assessments'); expect(assessments.reviews).toHaveBeenCalledTimes(1);
    expect(api.reviews).not.toHaveBeenCalled(); expect(attitudes.reviews).not.toHaveBeenCalled();
    await page.changePeriod(2); harness.detectChanges();
    expect(page.tab).toBe('assessments'); expect(page.assessmentWorkspace?.periodId).toBe(2);
  });
  it('shows no tabs or queues when no review authority is present', () => {
    api.reviews.calls.reset(); periods.periods.calls.reset(); auth.hasRole.and.returnValue(false);
    component.ngOnInit(); fixture.detectChanges();
    expect(component.tab).toBeNull(); expect(fixture.nativeElement.querySelectorAll('.workflow-group').length).toBe(0);
    expect(api.reviews).not.toHaveBeenCalled(); expect(periods.periods).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('REVIEW_NAV.NO_TABS');
  });
  it('restores the existing per-user Review Period selection without resetting the active tab', async () => {
    const key = 'team-review-period:navigation-test';
    const previous = sessionStorage.getItem(key);
    Object.defineProperty(auth, 'userId', { value: 'navigation-test' });
    sessionStorage.setItem(key, '2');
    try {
      auth.hasRole.and.returnValue(true);
      component.selectTab('attitude'); await fixture.whenStable(); fixture.detectChanges();
      component.periodId = null; component.loadPeriods();
      expect(component.selectedPeriod?.id).toBe(2); expect(component.tab).toBe('attitude');
    } finally {
      if (previous === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, previous);
    }
  });
});
