import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline, SearchOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { KpiAssistanceComponent } from './kpi-assistance.component';
import { KpiAssistanceService } from '../../services/kpi-assistance.service';
import { AuthService } from '../../services/auth.service';
import { KpiAssistance } from '../../models/kpi-assistance.model';
import { assistanceCase, assistanceEmployee } from './kpi-assistance.testing';

describe('KPI assistance workspace', () => {
  let fixture: ComponentFixture<KpiAssistanceComponent>; let component: KpiAssistanceComponent;
  let api: jasmine.SpyObj<KpiAssistanceService>; let auth: jasmine.SpyObj<AuthService>; let modal: NzModalService;
  let confirmDialog: jasmine.Spy<NzModalService['confirm']>;
  let permissions: string[];
  beforeEach(async () => {
    permissions = ['CAN_REVIEW_INDIVIDUAL_KPI'];
    api = jasmine.createSpyObj<KpiAssistanceService>('api', ['employees', 'list', 'get', 'request', 'approve', 'reject', 'plan', 'createPlan', 'updatePlan', 'confirmPlan']);
    api.list.and.returnValue(of([assistanceCase()])); api.employees.and.returnValue(of([assistanceEmployee])); api.get.and.returnValue(of(assistanceCase()));
    api.request.and.returnValue(of(assistanceCase('REQUESTED', 9))); api.approve.and.returnValue(of(assistanceCase('AUTHORIZED')));
    api.reject.and.returnValue(of({ ...assistanceCase('REJECTED'), rejectionReason: 'Discuss targets first', rejectedByName: 'HR', rejectedAt: '2028-01-02' }));
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole'], { userId: 'superior' }); auth.hasRole.and.callFake(value => permissions.includes(value));
    TestBed.configureTestingModule({ imports: [KpiAssistanceComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiAssistanceService, useValue: api }, { provide: AuthService, useValue: auth },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline, SearchOutline] }] });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(KpiAssistanceComponent); component = fixture.componentInstance;
    modal = fixture.debugElement.injector.get(NzModalService); confirmDialog = spyOn(modal, 'confirm');
  });
  function start(hr = false) { component.hr = hr; fixture.detectChanges(); }
  function confirm() { const callback = confirmDialog.calls.mostRecent().args[0]?.nzOnOk; if (callback && !(callback instanceof EventEmitter)) callback(undefined); }
  it('shows only a Superior\'s own cases even with both permissions', () => {
    permissions.push('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE');
    api.list.and.returnValue(of([assistanceCase(), { ...assistanceCase('REQUESTED', 9), superiorId: 'other' }])); start();
    expect(component.mine.length).toBe(1); expect(component.visibleCases.length).toBe(1);
    expect(component.canDecide).toBeFalse();
  });
  it('refreshes eligible options and does not offer a duplicate active request', () => {
    start(); component.requestPermission(); expect(component.eligible.length).toBe(0); expect(component.requestVisible).toBeTrue();
    component.sendRequest(); expect(api.request).not.toHaveBeenCalled();
    component.cases = [assistanceCase('REJECTED')]; component.requestPeriodId = 1; component.participantId = 7; component.requestReason = 'Needs help preparing KPIs'; component.sendRequest();
    expect(api.request).toHaveBeenCalledOnceWith(7, 'Needs help preparing KPIs'); expect(component.requestVisible).toBeFalse(); expect(component.cases.length).toBe(2);
    expect(component.cases.find(value => value.id === 8)?.status).toBe('REJECTED');
  });
  it('offers Request Again only after rejection and preserves the old reason', () => {
    const rejected = { ...assistanceCase('REJECTED'), rejectionReason: 'Discuss targets first' };
    api.list.and.returnValue(of([rejected])); api.get.and.returnValue(of(rejected)); start();
    expect(component.canRequestAgain(rejected)).toBeTrue(); expect(component.canPrepare(rejected)).toBeFalse();
    component.open(rejected); fixture.detectChanges(); expect(document.querySelector('.ant-drawer-open')?.textContent).toContain('Discuss targets first');
    component.requestPermission(rejected); expect(component.participantId).toBe(7); expect(component.requestPeriodId).toBe(1);
    api.employees.and.returnValue(of([])); component.requestPermission(rejected); expect(component.error).toBe('KPI_ASSISTANCE.NOT_ELIGIBLE');
  });
  it('opens creation only after a refreshed authorised request', () => {
    start(); api.get.and.returnValue(of(assistanceCase('REJECTED'))); component.open(assistanceCase('AUTHORIZED'), true);
    expect(component.workspace).toBeNull(); expect(component.drawerVisible).toBeTrue();
    api.get.and.returnValue(of(assistanceCase('AUTHORIZED'))); component.open(assistanceCase('AUTHORIZED'), true);
    expect(component.workspace?.status).toBe('AUTHORIZED'); expect(component.drawerVisible).toBeFalse();
  });
  it('HR sees all requests but never receives subordinate or editing controls', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true);
    expect(api.employees).not.toHaveBeenCalled(); expect(component.mine.length).toBe(1);
    component.selected = assistanceCase('AUTHORIZED'); expect(component.canPrepare(component.selected)).toBeFalse();
    component.requestPermission(); component.sendRequest(); expect(api.request).not.toHaveBeenCalled();
  });
  it('HR approves a refreshed Pending request and then disables decisions', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true); component.open(assistanceCase());
    component.approve(); expect(api.approve).not.toHaveBeenCalled(); confirm();
    expect(api.approve).toHaveBeenCalledOnceWith(8); expect(component.selected?.status).toBe('AUTHORIZED'); expect(component.canDecide).toBeFalse();
  });
  it('requires and trims the rejection reason, then displays the decision', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true); component.open(assistanceCase());
    component.rejectMode = true; component.reason = '   '; component.reject(); expect(component.reasonError).toBeTrue(); expect(modal.confirm).not.toHaveBeenCalled();
    component.reason = 'x'.repeat(10001); component.reject(); expect(api.reject).not.toHaveBeenCalled();
    component.reason = ' Discuss targets first '; component.reject(); confirm();
    expect(api.reject).toHaveBeenCalledOnceWith(8, 'Discuss targets first'); expect(component.selected?.rejectionReason).toBe('Discuss targets first');
    expect(component.canDecide).toBeFalse(); fixture.detectChanges(); expect(document.querySelector('.ant-drawer-open')?.textContent).toContain('Discuss targets first');
  });
  it('does not act on stale, Closed or already-decided requests', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true); component.selected = assistanceCase();
    api.get.and.returnValue(of(assistanceCase('REJECTED'))); component.decide(false); expect(api.approve).not.toHaveBeenCalled();
    expect(component.selected?.status).toBe('REJECTED'); expect(component.error).toBe('KPI_ASSISTANCE.DECISION_CHANGED');
    for (const status of ['AUTHORIZED', 'REJECTED', 'CONSUMED'] as const) { component.selected = assistanceCase(status); expect(component.canDecide).toBeFalse(); }
    component.selected = { ...assistanceCase(), reviewPeriodStatus: 'CLOSED' }; expect(component.canDecide).toBeFalse();
  });
  it('keeps failed reasons and blocks duplicate clicks while a decision is saving', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true); component.selected = assistanceCase(); component.reason = 'Discuss targets first';
    const response = new Subject<KpiAssistance>(); api.reject.and.returnValue(response); component.decide(true); component.decide(true);
    expect(api.reject).toHaveBeenCalledTimes(1); expect(component.busy).toBeTrue();
    response.error({ error: { message: 'Please retry' } }); expect(component.reason).toBe('Discuss targets first'); expect(component.error).toBe('Please retry'); expect(component.busy).toBeFalse();
    api.reject.and.returnValue(throwError(() => ({ status: 403 }))); component.decide(true); expect(component.canDecide).toBeFalse();
  });
  it('does not load APIs or offer actions based on Role names', () => {
    permissions = ['Super Admin', 'HR']; start(true); expect(api.list).not.toHaveBeenCalled(); expect(component.allowed).toBeFalse();
  });
  it('requires a request reason and retains input if saving fails', () => {
    api.list.and.returnValue(of([])); start(); component.requestPermission(); component.participantId = 7;
    for (const reason of ['', '   ', 'x'.repeat(10001)]) {
      component.requestReason = reason; component.sendRequest(); expect(component.requestReasonError).toBeTrue();
    }
    expect(api.request).not.toHaveBeenCalled();
    const response = new Subject<KpiAssistance>(); api.request.and.returnValue(response);
    component.requestReason = ' Needs help preparing KPIs '; component.sendRequest(); component.sendRequest();
    expect(api.request).toHaveBeenCalledOnceWith(7, 'Needs help preparing KPIs');
    response.error({ error: { message: 'Please retry' } });
    expect(component.requestReason).toBe(' Needs help preparing KPIs '); expect(component.requestVisible).toBeTrue(); expect(component.busy).toBeFalse();
  });
  it('highlights missing request selections and reason before sending, with no initial errors', () => {
    api.list.and.returnValue(of([])); start(); component.requestPermission();
    expect(component.requestEmployeeError).toBeFalse(); expect(component.requestReasonError).toBeFalse();
    component.requestPeriodId = null; component.sendRequest(); fixture.detectChanges();
    expect(api.request).not.toHaveBeenCalled();
    expect(component.requestPeriodError).toBeTrue(); expect(component.requestEmployeeError).toBeTrue();
    expect(component.requestReasonError).toBeTrue();
    expect(document.querySelector('#assistance-period-error')).not.toBeNull();
    expect(document.querySelector('#assistance-employee-error')).not.toBeNull();
    const periodSelect = document.querySelector('#assistance-request-period')?.closest('nz-select');
    expect(periodSelect).not.toBeNull(); expect(periodSelect?.closest('label')).toBeNull();
  });
  it('shows the request reason to HR and keeps earlier requests with no reason viewable', () => {
    permissions = ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE']; start(true); component.open(assistanceCase()); fixture.detectChanges();
    expect(document.querySelector('.ant-drawer-open .request-reason')?.textContent).toContain('Needs help preparing KPIs');
    const earlier = { ...assistanceCase(), requestReason: null }; api.get.and.returnValue(of(earlier));
    component.open(earlier); fixture.detectChanges();
    expect(document.querySelector('.ant-drawer-open .request-reason')?.textContent).toContain('KPI_ASSISTANCE.NO_REQUEST_REASON');
    expect(component.canDecide).toBeTrue();
  });
  it('sorts requested dates both ways without mutating history or losing filters', () => {
    api.list.and.returnValue(of([
      { ...assistanceCase('REQUESTED', 8), requestedAt: '2028-01-02T10:00:00Z' },
      { ...assistanceCase('REQUESTED', 9), requestedAt: '2028-01-03T10:00:00Z' },
      { ...assistanceCase('REQUESTED', 10), requestedAt: '2028-01-03T18:00:00+08:00' },
      assistanceCase('AUTHORIZED', 11)
    ])); start(); const before = component.cases.map(value => value.id);
    expect(component.visibleCases.map(value => value.id)).toEqual([10, 9, 8]);
    fixture.nativeElement.querySelector('.date-sort-button').click(); fixture.detectChanges();
    expect(component.visibleCases.map(value => value.id)).toEqual([8, 9, 10]);
    expect(fixture.nativeElement.querySelector('th[aria-sort]').getAttribute('aria-sort')).toBe('ascending');
    expect(component.cases.map(value => value.id)).toEqual(before);
    component.search = 'Other employee'; expect(component.visibleCases).toEqual([]);
    component.search = ''; component.periodId = 2; expect(component.visibleCases).toEqual([]);
  });
});
