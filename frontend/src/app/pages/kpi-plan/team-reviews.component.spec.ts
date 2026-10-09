import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, throwError } from 'rxjs';
import { TeamReviewsComponent } from './team-reviews.component';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { AuthService } from '../../services/auth.service';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { KpiItem, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

describe('Team Reviews', () => {
  let fixture: ComponentFixture<TeamReviewsComponent>;
  let component: TeamReviewsComponent;
  let api: jasmine.SpyObj<IndividualKpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  let auth: jasmine.SpyObj<AuthService>;
  let assessments: jasmine.SpyObj<KpiAssessmentService>;
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
    TestBed.configureTestingModule({ imports: [TeamReviewsComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: IndividualKpiPlanService, useValue: api },
      { provide: AuthService, useValue: auth }, { provide: KpiAssessmentService, useValue: assessments },
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
  it('reloads approved/returned history and filters by status, employee and period', () => {
    expect(api.reviews).toHaveBeenCalledTimes(1); expect(component.count('APPROVED')).toBe(1);
    component.filter = 'RETURNED'; expect(component.visiblePlans[0].id).toBe(12);
    component.search = 'other'; expect(component.visiblePlans).toEqual([]);
    component.search = 'Retail'; expect(component.visiblePlans.length).toBe(1);
    component.periodId = 2; expect(component.visiblePlans).toEqual([]);
    expect(fixture.nativeElement.textContent).not.toContain('KPI Assessment');
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
    expect(assessments.reviews).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs button').length).toBe(1);
    component.selectTab('assistance'); expect(component.tab).toBe('assessments');
  });
  it('keeps assessment and KPI plan responsibilities in separate permission-controlled tabs', () => {
    component.selectTab('assessments'); expect(component.tab).toBe('reviews');
    auth.hasRole.and.returnValue(true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.workflow-tabs button').length).toBe(3);
    component.selectTab('assessments'); fixture.detectChanges();
    component.assessmentWorkspace!.drawerVisible = true; component.selectTab('reviews');
    expect(component.tab).toBe('assessments'); expect(modal.confirm).toHaveBeenCalled();
    component.assessmentWorkspace!.drawerVisible = false; component.selectTab('reviews'); expect(component.tab).toBe('reviews');
  });
});
