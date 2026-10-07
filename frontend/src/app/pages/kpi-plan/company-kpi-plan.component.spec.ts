import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of, throwError } from 'rxjs';
import { CompanyKpiPlanComponent } from './company-kpi-plan.component';
import { KpiPlanService } from '../../services/kpi-plan.service';
import { KpiItem, KpiPeriodContext, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

describe('Company KPI plan workspace', () => {
  let fixture: ComponentFixture<CompanyKpiPlanComponent>;
  let component: CompanyKpiPlanComponent;
  let api: jasmine.SpyObj<KpiPlanService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const period = (status: KpiPeriodContext['status'] = 'OPEN'): KpiPeriodContext => ({
    id: 1, name: '2028 Annual Review', status, startDate: '2028-01-01', endDate: '2028-12-31',
    kpiSetupDeadline: '2028-01-31', participantsSnapshottedAt: '2027-12-01T00:00:00Z'
  });
  const item = (name = 'Revenue', weightage = 100): KpiItem => ({ ...emptyKpiItem(), name, target: '8%',
    measurementUnit: '%', weightage, scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } });
  const plan = (status: KpiPlan['status'] = 'DRAFT'): KpiPlan => ({
    id: 10, reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN',
    level: 'COMPANY', status, items: [item()], kpiSetupDeadline: '2028-01-31', totalWeightage: 100,
    overdue: false, createdAt: '2028-01-01', updatedAt: '2028-01-01'
  });
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiPlanService>('api', ['periods', 'list', 'create', 'update', 'publish']);
    api.periods.and.returnValue(of([period(), { ...period(), id: 2 }])); api.list.and.returnValue(of([]));
    api.create.and.returnValue(of(plan())); api.update.and.returnValue(of(plan())); api.publish.and.returnValue(of(plan('PUBLISHED')));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [CompanyKpiPlanComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: KpiPlanService, useValue: api },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] },
      { provide: NzModalService, useValue: modal }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(CompanyKpiPlanComponent); component = fixture.componentInstance;
    fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('shows one concise plan table with a 100% total, not the employee-level allocation', () => {
    component.items = [item('Revenue', 60), item('Customer satisfaction', 40)]; fixture.detectChanges();
    expect(component.total).toBe(100); expect(component.canPublish).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.weight-total').textContent).toContain('100%');
  });
  it('allows incomplete Drafts and saves the whole collection without publishing', () => {
    component.items = [emptyKpiItem()]; component.save();
    expect(api.create).toHaveBeenCalledWith({ reviewPeriodId: 1, items: [emptyKpiItem()] });
    expect(api.publish).not.toHaveBeenCalled();
  });
  it('adds a KPI using a working copy; KRA has no automatic default', () => {
    component.openItem(null); expect(component.editorItems[0].kra).toBeNull();
    component.editorItems[0].name = 'Growth'; component.applyItem();
    expect(component.items[0].name).toBe('Growth'); expect(component.dirty).toBeTrue();
    expect(api.create).not.toHaveBeenCalled();
  });
  it('keeps cancelled edits out of the plan', () => {
    component.items = [item()]; component.openItem(0); component.editorItems[0].name = 'Changed';
    component.drawerVisible = false;
    expect(component.items[0].name).toBe('Revenue');
  });
  it('rejects invalid supplied weights and duplicate names while allowing partial items', () => {
    component.items = [item()]; component.openItem(null); component.editorItems[0].weightage = 101;
    component.applyItem(); expect(component.drawerVisible).toBeTrue(); expect(component.itemError).toBeTruthy();
    component.editorItems[0].weightage = 20; component.editorItems[0].name = ' revenue ';
    component.applyItem(); expect(component.items.length).toBe(1); expect(component.drawerVisible).toBeTrue();
  });
  it('publishes the entire completed plan only after confirmation', () => {
    component.items = [item()]; component.confirmPublish();
    expect(api.publish).not.toHaveBeenCalled(); confirm();
    expect(api.create).toHaveBeenCalled(); expect(api.publish).toHaveBeenCalledOnceWith(10);
    expect(component.readonly).toBeTrue(); expect(component.plan?.status).toBe('PUBLISHED');
  });
  it('requires all five scoring definitions and exactly 100% before publication', () => {
    component.items = [item('Revenue', 15)]; expect(component.canPublish).toBeFalse();
    component.items[0].weightage = 100; delete component.items[0].scoringDefinitions[5];
    component.save(true); expect(api.create).not.toHaveBeenCalled(); expect(api.publish).not.toHaveBeenCalled();
  });
  it('requires a published period and snapshot even when the plan is complete', () => {
    component.items = [item()]; component.periods = [period('DRAFT')]; expect(component.canPublish).toBeFalse();
    component.periods = [{ ...period(), participantsSnapshottedAt: null }]; expect(component.canPublish).toBeFalse();
  });
  it('allows late setup in an Open period', () => {
    component.periods = [{ ...period(), kpiSetupDeadline: '2000-01-01' }]; component.items = [item()];
    expect(component.selectedPeriod?.status).toBe('OPEN'); expect(component.canPublish).toBeTrue();
  });
  it('retains the saved Draft ID when publication fails so a retry does not create another plan', () => {
    component.items = [item()]; api.publish.and.returnValue(throwError(() => ({ error: { message: 'Roster missing' } })));
    component.save(true); expect(component.plan?.id).toBe(10); expect(component.error).toBe('Roster missing');
    expect(component.items.length).toBe(1); expect(component.busy).toBeFalse();
    api.publish.and.returnValue(of(plan('PUBLISHED'))); component.save(true);
    expect(api.create).toHaveBeenCalledTimes(1); expect(api.update).toHaveBeenCalledTimes(1);
  });
  it('makes Closed periods and Published plans read-only', () => {
    component.periods = [period('CLOSED')]; component.save(); component.openItem(null);
    expect(component.readonly).toBeTrue(); expect(component.drawerVisible).toBeFalse(); expect(api.create).not.toHaveBeenCalled();
    component.periods = [period()]; component.plan = plan('PUBLISHED'); component.items = component.plan.items;
    component.removeItem(0); component.save(); expect(modal.confirm).not.toHaveBeenCalled();
    component.openItem(0); expect(component.drawerVisible).toBeTrue();
  });
  it('asks before discarding unsaved changes on period selection', () => {
    component.items = [item()]; component.changePeriod(2);
    expect(component.selectedId).toBe(1); expect(modal.confirm).toHaveBeenCalled();
    confirm(); expect(component.selectedId).toBe(2); expect(component.items).toEqual([]);
  });
  it('requires confirmation to remove an item without a per-item API call', () => {
    component.items = [item()]; component.removeItem(0); expect(component.items.length).toBe(1);
    confirm(); expect(component.items.length).toBe(0); expect(api.update).not.toHaveBeenCalled();
  });
  it('retains entries when Draft saving fails', () => {
    component.items = [item()]; api.create.and.returnValue(throwError(() => ({ error: { message: 'Conflict' } })));
    component.save(); expect(component.items[0].name).toBe('Revenue'); expect(component.error).toBe('Conflict'); expect(component.busy).toBeFalse();
  });
});
