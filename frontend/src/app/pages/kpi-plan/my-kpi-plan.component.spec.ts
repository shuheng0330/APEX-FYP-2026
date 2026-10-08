import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { MyKpiPlanComponent } from './my-kpi-plan.component';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { KpiItem, KpiPeriodContext, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

describe('My KPI Plan', () => {
  let fixture: ComponentFixture<MyKpiPlanComponent>;
  let component: MyKpiPlanComponent;
  let api: jasmine.SpyObj<IndividualKpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const period = (id = 1, status: KpiPeriodContext['status'] = 'OPEN'): KpiPeriodContext => ({
    id, name: `${2027 + id} Annual Review`, status, startDate: '2028-01-01', endDate: '2028-12-31',
    kpiSetupDeadline: '2028-01-31', participantsSnapshottedAt: '2027-12-01T00:00:00Z'
  });
  const item = (name = 'New Customers', weightage = 100): KpiItem => ({ ...emptyKpiItem(), name,
    perspective: 'Customer', kra: 'Customer growth', target: '10 new customers', weightage,
    scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } });
  const plan = (status: KpiPlan['status'] = 'DRAFT'): KpiPlan => ({
    id: 10, reviewPeriodId: 1, ownerParticipantId: 7, employeeName: 'Amir',
    reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', level: 'INDIVIDUAL', status,
    items: [item()], totalWeightage: 100, overdue: false, kpiSetupDeadline: '2028-01-31',
    createdAt: '2028-01-01', updatedAt: '2028-01-01'
  });
  beforeEach(async () => {
    api = jasmine.createSpyObj<IndividualKpiPlanService>('api', ['periods', 'mine', 'assigned', 'create', 'update', 'submit']);
    api.periods.and.returnValue(of([period()])); api.mine.and.returnValue(of([])); api.assigned.and.returnValue(of([]));
    api.create.and.callFake(request => of({ ...plan(), items: request.items }));
    api.update.and.callFake((id, request) => of({ ...plan(), id, items: request.items }));
    api.submit.and.returnValue(of(plan('PENDING_APPROVAL')));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [MyKpiPlanComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: IndividualKpiPlanService, useValue: api },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }, { provide: NzModalService, useValue: modal }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(MyKpiPlanComponent);
    component = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('keeps assigned KPI weights separate from the Individual plan total', () => {
    component.plan = plan(); component.items = [item('Customers', 60), item('Cross sell', 40)];
    component.assignedPlans = [{ ...plan('PUBLISHED'), id: 20, level: 'COMPANY', items: [item('Revenue', 100)] }];
    fixture.detectChanges(); expect(component.total).toBe(100); expect(component.canSubmit).toBeTrue();
    expect(fixture.nativeElement.querySelector('.weight-total').textContent).toContain('100%');
    component.viewAssigned(component.assignedPlans[0].items[0], 'COMPANY');
    expect(component.drawerReadonly).toBeTrue(); component.applyItem(); expect(api.update).not.toHaveBeenCalled();
  });
  it('shows the selected period allocation read-only without scaling editable KPI weights', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { INDIVIDUAL_KPI: {
      ALLOCATION_EXPLANATION: 'Your Individual KPI Plan must total 100%. It contributes {{amount}}% to your overall KPI performance score.'
    } });
    translate.use('en');
    component.periods = [{ ...period(), kpiAllocation: { employeeLevelId: 4, employeeLevelName: 'Executive',
      companyKpiWeight: 15, departmentKpiWeight: 25, individualKpiWeight: 60 } },
      { ...period(2), kpiAllocation: { employeeLevelId: 5, employeeLevelName: 'Admin',
        companyKpiWeight: 10, departmentKpiWeight: 10, individualKpiWeight: 80 } }];
    component.plan = plan(); component.plans = [plan()]; component.items = [item()]; fixture.detectChanges();
    const allocation = () => fixture.nativeElement.querySelector('.kpi-allocation');
    expect(allocation().textContent).toContain('Executive');
    expect(allocation().querySelector('.employee-level-pill').textContent).toContain('Executive');
    expect([...allocation().querySelectorAll('.allocation-bar > span')].map((node: unknown) => (node as HTMLElement).style.width)).toEqual(['15%', '25%', '60%']);
    expect(allocation().compareDocumentPosition(fixture.nativeElement.querySelector('.weight-total')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('INDIVIDUAL_KPI.WEIGHT_HELP');
    expect([...allocation().querySelectorAll('dd')].map((node: unknown) => (node as HTMLElement).textContent?.trim())).toEqual(['15%', '25%', '60%']);
    expect(allocation().textContent).toContain('It contributes 60% to your overall KPI performance score.');
    expect(allocation().querySelectorAll('input, button, nz-select').length).toBe(0);
    component.openItem(0); expect(component.editorItems[0].weightage).toBe(100); component.applyItem();
    expect(api.update.calls.mostRecent().args[1].items[0].weightage).toBe(100); expect(component.total).toBe(100);
    component.changePeriod(2); fixture.detectChanges();
    expect(allocation().textContent).toContain('Admin'); expect(allocation().textContent).toContain('It contributes 80%');
  });
  it('does not assume an allocation when unavailable and displays a recorded zero', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.kpi-allocation').textContent).toContain('INDIVIDUAL_KPI.ALLOCATION_UNAVAILABLE');
    expect(fixture.nativeElement.querySelector('.allocation-bar')).toBeNull();
    component.periods = [{ ...period(), kpiAllocation: { employeeLevelId: 4, employeeLevelName: 'Executive',
      companyKpiWeight: 50, departmentKpiWeight: 50, individualKpiWeight: 0 } }]; fixture.detectChanges();
    const values = fixture.nativeElement.querySelectorAll('.allocation-values dd');
    expect(values[2].textContent).toBe('0%');
    expect(fixture.nativeElement.querySelector('.allocation-explanation').textContent).not.toContain('ALLOCATION_UNAVAILABLE');
  });
  it('validates required fields before Apply to Plan saves an item', () => {
    component.openItem(null); component.applyItem();
    expect(api.create).not.toHaveBeenCalled(); expect(component.drawerVisible).toBeTrue();
    expect(Object.keys(component.fieldErrors).length).toBe(10);
    component.editorItems[0] = item('Customers', 60); component.applyItem();
    expect(api.create).toHaveBeenCalledOnceWith({ reviewPeriodId: 1, items: [item('Customers', 60)] });
    expect(component.drawerVisible).toBeFalse(); expect(component.total).toBe(60); expect(component.canSubmit).toBeFalse();
  });
  it('prevents duplicate names and persists edits and removal as a whole plan', () => {
    component.plan = plan(); component.items = [item()];
    component.openItem(null); component.editorItems[0] = item(); component.applyItem();
    expect(component.fieldErrors['name']).toBe('INDIVIDUAL_KPI.DUPLICATE_NAME'); expect(api.update).not.toHaveBeenCalled();
    component.drawerVisible = false; component.openItem(0); component.editorItems[0].name = 'Revised Customers'; component.applyItem();
    expect(api.update.calls.mostRecent().args[1].items[0].name).toBe('Revised Customers');
    component.removeItem(0); confirm(); expect(api.update.calls.mostRecent().args[1].items).toEqual([]);
  });
  it('blocks incomplete submission and makes submitted values read-only', () => {
    component.plan = plan(); component.items = [item('Customers', 60)];
    expect(component.submitBlocker?.params?.amount).toBe(40); component.confirmSubmit(); expect(modal.confirm).not.toHaveBeenCalled();
    component.items[0].weightage = 100; delete component.items[0].scoringDefinitions[5]; expect(component.canSubmit).toBeFalse();
    component.items[0].scoringDefinitions[5] = 'Excellent'; component.confirmSubmit(); expect(api.submit).not.toHaveBeenCalled();
    confirm(); expect(api.submit).toHaveBeenCalledOnceWith(10); expect(component.readonly).toBeTrue();
    component.openItem(null); expect(component.drawerVisible).toBeFalse();
  });
  it('shows return reasons and allows correction and resubmission', () => {
    component.plan = { ...plan('RETURNED'), revisionRequired: true, returnReason: 'Clarify your target' }; component.items = [item()]; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.return-note').textContent).toContain('Clarify your target');
    expect(component.readonly).toBeFalse(); expect(component.canSubmit).toBeFalse();
    expect(component.submitBlocker?.key).toBe('APPROVAL_REVISION.CHANGE_REQUIRED');
    component.confirmSubmit(); expect(modal.confirm).not.toHaveBeenCalled();
    api.update.and.callFake((id, request) => of({ ...plan('RETURNED'), id, revisionRequired: false, items: request.items }));
    component.openItem(0); component.editorItems[0].target = '12 new customers'; component.applyItem();
    expect(component.canSubmit).toBeTrue();
    component.confirmSubmit(); confirm(); expect(api.submit).toHaveBeenCalledOnceWith(10);
  });
  it('uses saved revision state after an unchanged edit or reload', () => {
    const returned = { ...plan('RETURNED'), revisionRequired: true };
    api.mine.and.returnValue(of([returned])); component.load();
    expect(component.canSubmit).toBeFalse();
    api.update.and.returnValue(of(returned)); component.openItem(0); component.applyItem();
    expect(api.update).toHaveBeenCalled(); expect(component.canSubmit).toBeFalse();
    component.load(); expect(component.canSubmit).toBeFalse();
  });
  it('allows late Open-period setup but keeps Approved and Closed plans read-only', () => {
    component.periods = [{ ...period(), kpiSetupDeadline: '2000-01-01' }]; component.plan = plan(); component.items = [item()];
    expect(component.canSubmit).toBeTrue(); component.plan = plan('APPROVED'); expect(component.canSubmit).toBeFalse();
    component.plan = null; component.periods = [period(1, 'CLOSED')]; component.openItem(null);
    expect(component.drawerVisible).toBeFalse(); expect(api.create).not.toHaveBeenCalled();
  });
  it('retains the item on save failure and prevents duplicate saves while pending', () => {
    const pending = new Subject<KpiPlan>(); api.create.and.returnValue(pending);
    component.openItem(null); component.editorItems[0] = item(); component.applyItem(); component.applyItem();
    expect(api.create).toHaveBeenCalledTimes(1); pending.error({ error: { message: 'Please retry' } });
    expect(component.busy).toBeFalse(); expect(component.drawerVisible).toBeTrue(); expect(component.items).toEqual([]);
    expect(component.itemError).toBe('Please retry'); expect(component.editorItems[0].name).toBe('New Customers');
  });
  it('loads assignments for the selected enrolled period without retaining previous items', () => {
    component.periods = [period(), period(2)]; component.plan = plan(); component.plans = [plan()]; component.items = [item()];
    component.changePeriod(2); expect(api.assigned).toHaveBeenCalledWith(2);
    expect(component.plan).toBeNull(); expect(component.items).toEqual([]); expect(component.assignedPlans).toEqual([]);
  });
  it('shows meaningful empty enrollment and load errors without enabling editing', () => {
    api.periods.and.returnValue(of([])); component.load(); fixture.detectChanges();
    expect(component.periodId).toBeNull(); expect(fixture.nativeElement.textContent).toContain('INDIVIDUAL_KPI.NO_PERIODS');
    api.periods.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } }))); component.load();
    expect(component.ready).toBeFalse(); expect(component.readonly).toBeTrue(); expect(component.error).toBe('Please retry');
  });
});
