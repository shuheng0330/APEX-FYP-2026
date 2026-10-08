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
  let api: jasmine.SpyObj<KpiAssistanceService>; let auth: jasmine.SpyObj<AuthService>; let modal: jasmine.SpyObj<NzModalService>;
  let permissions: string[];
  beforeEach(async () => {
    permissions = ['CAN_REVIEW_INDIVIDUAL_KPI'];
    api = jasmine.createSpyObj<KpiAssistanceService>('api', ['employees', 'list', 'get', 'request', 'approve', 'reject', 'plan', 'createPlan', 'updatePlan', 'confirmPlan']);
    api.list.and.returnValue(of([assistanceCase()])); api.employees.and.returnValue(of([assistanceEmployee])); api.get.and.returnValue(of(assistanceCase()));
    api.request.and.returnValue(of(assistanceCase('REQUESTED', 9))); api.approve.and.returnValue(of(assistanceCase('AUTHORIZED')));
    api.reject.and.returnValue(of({ ...assistanceCase('REJECTED'), rejectionReason: 'Discuss targets first', rejectedByName: 'HR', rejectedAt: '2028-01-02' }));
    auth = jasmine.createSpyObj<AuthService>('auth', ['hasRole'], { userId: 'superior' }); auth.hasRole.and.callFake(value => permissions.includes(value));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [KpiAssistanceComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiAssistanceService, useValue: api }, { provide: AuthService, useValue: auth }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline, SearchOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(KpiAssistanceComponent); component = fixture.componentInstance;
  });
  function start(hr = false) { component.hr = hr; fixture.detectChanges(); }
  function confirm() { const callback = modal.confirm.calls.mostRecent().args[0]?.nzOnOk; if (callback && !(callback instanceof EventEmitter)) callback(undefined); }
  it('shows only a Superior\'s own cases even with both permissions', () => {
    permissions.push('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE');
    api.list.and.returnValue(of([assistanceCase(), { ...assistanceCase('REQUESTED', 9), superiorId: 'other' }])); start();
    expect(component.mine.length).toBe(1); expect(component.visibleCases.length).toBe(1);
    expect(component.canDecide).toBeFalse();
  });
  it('refreshes eligible options and does not offer a duplicate active request', () => {
    start(); component.requestPermission(); expect(component.eligible.length).toBe(0); expect(component.requestVisible).toBeTrue();
    component.sendRequest(); expect(api.request).not.toHaveBeenCalled();
    component.cases = [assistanceCase('REJECTED')]; component.requestPeriodId = 1; component.participantId = 7; component.sendRequest();
    expect(api.request).toHaveBeenCalledOnceWith(7); expect(component.requestVisible).toBeFalse(); expect(component.cases.length).toBe(2);
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
});
