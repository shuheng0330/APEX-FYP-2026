import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AssistedKpiPlanComponent } from './assisted-kpi-plan.component';
import { KpiAssistanceService } from '../../services/kpi-assistance.service';
import { AuthService } from '../../services/auth.service';
import { KpiPlan } from '../../models/kpi-plan.model';
import { assistanceCase, assistedItem, assistedPlan } from './kpi-assistance.testing';

describe('Assisted Individual KPI Plan', () => {
  let fixture: ComponentFixture<AssistedKpiPlanComponent>; let component: AssistedKpiPlanComponent;
  let api: jasmine.SpyObj<KpiAssistanceService>; let modal: jasmine.SpyObj<NzModalService>; let auth: jasmine.SpyObj<AuthService>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiAssistanceService>('api', ['get', 'plan', 'createPlan', 'updatePlan', 'confirmPlan']);
    api.get.and.returnValue(of(assistanceCase('AUTHORIZED'))); api.plan.and.returnValue(of(assistedPlan()));
    api.createPlan.and.callFake((id, items) => of({ ...assistedPlan(), items })); api.updatePlan.and.callFake((id, items) => of({ ...assistedPlan(), items }));
    api.confirmPlan.and.returnValue(of(assistedPlan('APPROVED')));
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole'], { userId: 'superior' }); auth.hasRole.and.returnValue(true);
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [AssistedKpiPlanComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiAssistanceService, useValue: api }, { provide: AuthService, useValue: auth }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(AssistedKpiPlanComponent); component = fixture.componentInstance;
    fixture.componentRef.setInput('assistance', assistanceCase('AUTHORIZED')); fixture.detectChanges();
  });
  function confirm() { const callback = modal.confirm.calls.mostRecent().args[0]?.nzOnOk; if (callback && !(callback instanceof EventEmitter)) callback(undefined); }
  it('starts without a database Draft and validates before the first persisted item', () => {
    expect(api.createPlan).not.toHaveBeenCalled(); expect(component.plan).toBeNull();
    component.openItem(null); component.applyItem(); expect(Object.keys(component.fieldErrors).length).toBe(10); expect(api.createPlan).not.toHaveBeenCalled();
    component.editorItems = [assistedItem('Customers', 60)]; component.applyItem();
    expect(api.createPlan).toHaveBeenCalledOnceWith(8, [assistedItem('Customers', 60)]); expect(component.total).toBe(60); expect(component.canConfirm).toBeFalse();
    expect(component.drawerVisible).toBeFalse(); expect(component.assistance.planId).toBe(10);
  });
  it('continues an existing Draft and persists edits and removals without another plan', () => {
    api.get.and.returnValue(of({ ...assistanceCase('AUTHORIZED'), planId: 10 })); component.load(); expect(api.plan).toHaveBeenCalledOnceWith(8);
    component.openItem(0); component.editorItems[0].target = '20 customers'; component.applyItem(); expect(api.updatePlan.calls.mostRecent().args[1][0].target).toBe('20 customers');
    component.removeItem(0); confirm(); expect(api.updatePlan.calls.mostRecent().args).toEqual([8, []]); expect(component.total).toBe(0); expect(api.createPlan).not.toHaveBeenCalled();
  });
  it('rejects duplicate names and incomplete or non-100% confirmation', () => {
    component.plan = assistedPlan(); component.items = [assistedItem()]; component.openItem(null); component.editorItems = [assistedItem()]; component.applyItem();
    expect(component.fieldErrors['name']).toBe('INDIVIDUAL_KPI.DUPLICATE_NAME'); expect(api.updatePlan).not.toHaveBeenCalled();
    component.drawerVisible = false; component.items = [assistedItem('Customers', 99.99)]; component.confirm(); expect(api.confirmPlan).not.toHaveBeenCalled();
    component.items = [assistedItem()]; delete component.items[0].scoringDefinitions[5]; expect(component.canConfirm).toBeFalse();
  });
  it('confirms directly to Approved without any employee submission or review', () => {
    component.plan = assistedPlan(); component.items = [assistedItem()];
    api.get.and.returnValue(of({ ...assistanceCase('CONSUMED'), planId: 10, consumedAt: '2028-01-02' }));
    component.confirm(); expect(api.confirmPlan).not.toHaveBeenCalled(); confirm();
    expect(api.confirmPlan).toHaveBeenCalledOnceWith(8); expect(component.plan?.status).toBe('APPROVED'); expect(component.assistance.status).toBe('CONSUMED'); expect(component.readonly).toBeTrue();
  });
  it('preserves the drawer and previous saved items on integration failure', () => {
    component.plan = assistedPlan(); component.items = [assistedItem()]; component.openItem(0); component.editorItems[0].target = '20 customers';
    api.updatePlan.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } }))); component.applyItem();
    expect(component.drawerVisible).toBeTrue(); expect(component.items[0].target).toBe('10 customers'); expect(component.editorItems[0].target).toBe('20 customers'); expect(component.itemError).toBe('Please retry');
  });
  it('blocks editing for rejected/consumed cases, Closed periods, wrong Superior and HR-only permissions', () => {
    for (const status of ['REQUESTED', 'REJECTED', 'CONSUMED'] as const) { component.assistance = assistanceCase(status); expect(component.readonly).toBeTrue(); component.openItem(null); }
    component.assistance = { ...assistanceCase('AUTHORIZED'), reviewPeriodStatus: 'CLOSED' }; expect(component.readonly).toBeTrue();
    component.assistance = { ...assistanceCase('AUTHORIZED'), superiorId: 'other' }; expect(component.readonly).toBeTrue();
    component.assistance = assistanceCase('AUTHORIZED'); auth.hasRole.and.returnValue(false); expect(component.readonly).toBeTrue();
    component.applyItem(); component.confirmPlan(); expect(api.createPlan).not.toHaveBeenCalled(); expect(api.confirmPlan).not.toHaveBeenCalled();
  });
  it('permits only viewing existing completed plans and five criteria', () => {
    api.get.and.returnValue(of({ ...assistanceCase('CONSUMED'), planId: 10 })); api.plan.and.returnValue(of(assistedPlan('APPROVED'))); component.load();
    component.openItem(0, true); fixture.detectChanges(); expect(document.querySelectorAll('.ant-drawer-open .criterion-value').length).toBe(5);
    component.applyItem(); expect(api.updatePlan).not.toHaveBeenCalled();
  });
  it('blocks repeated save clicks and disables edits after a reporting/access change', () => {
    const pending = new Subject<KpiPlan>(); api.createPlan.and.returnValue(pending); component.openItem(null); component.editorItems = [assistedItem()];
    component.applyItem(); component.applyItem(); expect(api.createPlan).toHaveBeenCalledTimes(1); pending.error({ status: 403 });
    expect(component.readonly).toBeTrue(); expect(component.itemError).toBe('KPI_ASSISTANCE.ACCESS_CHANGED'); expect(component.drawerVisible).toBeTrue();
  });
});
