import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzIconModule, NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, throwError } from 'rxjs';
import { DepartmentKpiPlanComponent } from './department-kpi-plan.component';
import { DepartmentKpiPlanService } from '../../services/department-kpi-plan.service';
import { KpiItem, KpiPeriodContext, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

describe('Department KPI plan workspace', () => {
  let fixture: ComponentFixture<DepartmentKpiPlanComponent>;
  let component: DepartmentKpiPlanComponent;
  let api: jasmine.SpyObj<DepartmentKpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const period = (status: KpiPeriodContext['status'] = 'OPEN'): KpiPeriodContext => ({
    id: 1, name: '2028 Annual Review', status, startDate: '2028-01-01', endDate: '2028-12-31',
    kpiSetupDeadline: '2028-01-31', participantsSnapshottedAt: '2027-12-01T00:00:00Z'
  });
  const item = (name = 'Sales', weightage = 100): KpiItem => ({ ...emptyKpiItem(), name, target: 'RM 80,000',
    perspective: 'Financial', kra: 'Sales growth', weightage,
    scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } });
  const plan = (status: KpiPlan['status'] = 'DRAFT'): KpiPlan => ({
    id: 10, reviewPeriodId: 1, departmentId: 2, departmentName: 'Retail Sales',
    reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', level: 'DEPARTMENT', status,
    items: [item()], kpiSetupDeadline: '2028-01-31', totalWeightage: 100, overdue: false,
    createdAt: '2028-01-01', updatedAt: '2028-01-01'
  });
  beforeEach(async () => {
    api = jasmine.createSpyObj<DepartmentKpiPlanService>('api', ['periods', 'departments', 'list', 'create', 'update', 'submit']);
    api.periods.and.returnValue(of([period()])); api.departments.and.returnValue(of([{ id: 2, name: 'Retail Sales' }]));
    api.list.and.returnValue(of([]));
    api.create.and.callFake(request => of({ ...plan(), items: request.items }));
    api.update.and.callFake((id, request) => of({ ...plan(), id, items: request.items }));
    api.submit.and.returnValue(of(plan('PENDING_APPROVAL')));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [DepartmentKpiPlanComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: DepartmentKpiPlanService, useValue: api },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] },
      { provide: NzModalService, useValue: modal }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(DepartmentKpiPlanComponent); component = fixture.componentInstance;
    fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('shows the Department plan and within-level 100% progress', () => {
    component.plan = plan(); component.items = [item('Sales', 60), item('Service', 40)]; fixture.detectChanges();
    expect(component.total).toBe(100); expect(component.canSubmit).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.weight-total').textContent).toContain('100%');
  });
  it('validates an item before saving it in a Draft plan', () => {
    component.openItem(null); component.applyItem();
    expect(component.items).toEqual([]); expect(component.drawerVisible).toBeTrue();
    expect(Object.keys(component.fieldErrors).length).toBe(10); expect(api.create).not.toHaveBeenCalled();
    component.editorItems[0] = item(); component.applyItem();
    expect(api.create).toHaveBeenCalledOnceWith({ reviewPeriodId: 1, departmentId: 2, items: [item()] });
    expect(component.drawerVisible).toBeFalse(); expect(component.plan?.status).toBe('DRAFT');
  });
  it('requires the complete plan before submitting and waits for confirmation', () => {
    component.plan = plan(); component.items = [item('Sales', 60)];
    expect(component.canSubmit).toBeFalse(); expect(component.submitBlocker?.params?.amount).toBe(40);
    component.items[0].weightage = 100; delete component.items[0].scoringDefinitions[5];
    expect(component.canSubmit).toBeFalse();
    component.items[0].scoringDefinitions[5] = 'Excellent'; component.confirmSubmit();
    expect(api.submit).not.toHaveBeenCalled(); confirm();
    expect(api.submit).toHaveBeenCalledOnceWith(10); expect(component.plan?.status).toBe('PENDING_APPROVAL');
    expect(component.readonly).toBeTrue();
  });
  it('shows a return reason and allows revision and resubmission', () => {
    component.plan = { ...plan('RETURNED'), revisionRequired: true, returnReason: 'Clarify the sales target' };
    component.items = structuredClone(component.plan.items); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.return-note').textContent).toContain('Clarify the sales target');
    expect(component.readonly).toBeFalse();
    expect(component.canSubmit).toBeFalse();
    expect(component.submitBlocker?.key).toBe('APPROVAL_REVISION.CHANGE_REQUIRED');
    component.confirmSubmit(); expect(modal.confirm).not.toHaveBeenCalled();
    api.update.and.callFake((id, request) => of({ ...plan('RETURNED'), id, revisionRequired: false, items: request.items }));
    component.openItem(0); component.editorItems[0].name = 'Revised Sales'; component.applyItem();
    expect(api.update).toHaveBeenCalled();
    expect(component.canSubmit).toBeTrue();
    component.confirmSubmit(); confirm(); expect(api.submit).toHaveBeenCalledOnceWith(10);
  });
  it('keeps unchanged saves and reloaded returned plans blocked', () => {
    const returned = { ...plan('RETURNED'), revisionRequired: true };
    component.plan = returned; component.items = structuredClone(returned.items);
    api.update.and.returnValue(of(returned)); component.openItem(0); component.applyItem();
    expect(api.update).toHaveBeenCalled(); expect(component.canSubmit).toBeFalse();
    component.plans = [returned]; component.selectPlan(); expect(component.canSubmit).toBeFalse();
    component.submitPlan(); expect(api.submit).not.toHaveBeenCalled();
  });
  it('keeps Pending, Approved and Closed plans read-only', () => {
    component.plan = plan('APPROVED'); component.openItem(null); expect(component.drawerVisible).toBeFalse();
    component.plan = plan('PENDING_APPROVAL'); component.submitPlan(); expect(api.submit).not.toHaveBeenCalled();
    component.plan = null; component.periods = [period('CLOSED')]; component.openItem(null);
    expect(component.drawerVisible).toBeFalse(); expect(component.readonly).toBeTrue();
  });
  it('retains the Draft and entry on a save failure', () => {
    api.create.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } })));
    component.openItem(null); component.editorItems[0] = item(); component.applyItem();
    expect(component.items).toEqual([]); expect(component.editorItems[0].name).toBe('Sales');
    expect(component.drawerVisible).toBeTrue(); expect(component.itemError).toBe('Please retry');
  });
  it('allows late setup while the review period is Open', () => {
    component.plan = plan(); component.items = [item()]; component.periods = [{ ...period(), kpiSetupDeadline: '2000-01-01' }];
    expect(component.canSubmit).toBeTrue();
  });
  it('does not make unrelated Departments editable', () => {
    component.departments = [{ id: 2, name: 'Retail Sales' }]; component.departmentId = 2;
    component.plans = [{ ...plan(), departmentId: 3, departmentName: 'HR' }]; component.selectPlan();
    expect(component.plan).toBeNull(); expect(component.items).toEqual([]);
  });
});
