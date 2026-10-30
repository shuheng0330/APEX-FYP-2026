import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, throwError } from 'rxjs';
import { DepartmentKpiReviewComponent } from './department-kpi-review.component';
import { DepartmentKpiPlanService } from '../../services/department-kpi-plan.service';
import { KpiItem, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

describe('Department KPI review workspace', () => {
  let fixture: ComponentFixture<DepartmentKpiReviewComponent>;
  let component: DepartmentKpiReviewComponent;
  let api: jasmine.SpyObj<DepartmentKpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const item: KpiItem = { ...emptyKpiItem(), name: 'Sales', perspective: 'Financial', kra: 'Sales growth',
    target: 'RM 80,000', weightage: 100,
    scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } };
  const plan = (status: KpiPlan['status'] = 'PENDING_APPROVAL', id = 10): KpiPlan => ({
    id, reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN',
    departmentId: 2, departmentName: 'Retail Sales', level: 'DEPARTMENT', status, items: [item],
    kpiSetupDeadline: '2028-01-31', totalWeightage: 100, overdue: false,
    submittedBy: 'id-1', submittedByName: 'Sales HOD', submittedAt: '2028-02-01T00:00:00Z',
    createdAt: '2028-01-01', updatedAt: '2028-01-01'
  });
  beforeEach(async () => {
    api = jasmine.createSpyObj<DepartmentKpiPlanService>('api', ['list', 'get', 'approve', 'returnForRevision']);
    api.list.and.returnValue(of([plan(), plan('APPROVED', 11), plan('RETURNED', 12), plan('DRAFT', 13)]));
    api.get.and.callFake(id => of(plan(id === 10 ? 'PENDING_APPROVAL' : id === 11 ? 'APPROVED' : 'RETURNED', id)));
    api.approve.and.returnValue(of(plan('APPROVED')));
    api.returnForRevision.and.returnValue(of({ ...plan('RETURNED'), returnReason: 'Clarify sales target' }));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [DepartmentKpiReviewComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: DepartmentKpiPlanService, useValue: api },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] },
      { provide: NzModalService, useValue: modal }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(DepartmentKpiReviewComponent); component = fixture.componentInstance;
    fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('lists only submitted plans and defaults to the pending queue', () => {
    expect(component.plans.length).toBe(3); expect(component.visiblePlans.length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('Sales HOD');
    component.filter = 'APPROVED'; expect(component.visiblePlans[0].id).toBe(11);
    component.filter = 'RETURNED'; expect(component.visiblePlans[0].id).toBe(12);
  });
  it('opens a complete read-only plan with all five scoring definitions', () => {
    component.open(10); fixture.detectChanges();
    expect(api.get).toHaveBeenCalledOnceWith(10); expect(component.drawerVisible).toBeTrue();
    const body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.textContent).toContain('Financial'); expect(body.textContent).toContain('Sales growth');
    expect(body.textContent).toContain('RM 80,000'); expect(body.textContent).toContain('Excellent');
    expect(body.querySelectorAll('.criterion-value').length).toBe(5);
  });
  it('approves only a pending plan after confirmation', () => {
    component.open(10); component.confirmApprove(); expect(api.approve).not.toHaveBeenCalled();
    confirm(); expect(api.approve).toHaveBeenCalledOnceWith(10); expect(component.selected?.status).toBe('APPROVED');
    expect(component.canDecide).toBeFalse(); expect(component.count('PENDING_APPROVAL')).toBe(0);
  });
  it('requires a return reason and shows it after sending back', () => {
    component.open(10); component.beginReturn(); component.returnForRevision();
    expect(component.reasonError).toBeTrue(); expect(api.returnForRevision).not.toHaveBeenCalled();
    component.returnReason = ' Clarify sales target '; component.returnForRevision();
    expect(api.returnForRevision).toHaveBeenCalledOnceWith(10, 'Clarify sales target');
    expect(component.selected?.status).toBe('RETURNED'); expect(component.selected?.returnReason).toBe('Clarify sales target');
    expect(component.canDecide).toBeFalse();
  });
  it('does not offer decisions for Approved, Returned or Closed plans', () => {
    component.open(11); component.confirmApprove(); component.beginReturn();
    expect(component.canDecide).toBeFalse(); expect(component.returnMode).toBeFalse();
    component.open(12); expect(component.canDecide).toBeFalse();
    component.selected = { ...plan(), reviewPeriodStatus: 'CLOSED' };
    expect(component.canDecide).toBeFalse(); expect(api.approve).not.toHaveBeenCalled();
  });
  it('keeps the plan available when approval fails', () => {
    component.open(10); api.approve.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } })));
    component.approve(); expect(component.selected?.status).toBe('PENDING_APPROVAL');
    expect(component.error).toBe('Please retry'); expect(component.busy).toBeFalse();
  });
});
