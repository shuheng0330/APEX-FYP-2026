import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { EventEmitter } from '@angular/core';
import { of, Subject } from 'rxjs';
import { KpiRevisionsComponent } from './kpi-revisions.component';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { AuthService } from '../../services/auth.service';
import { KpiRevision } from '../../models/kpi-revision.model';
import { revisionFixture, revisionPlan } from './kpi-revision.test-data';

describe('KPI revision review queue and amendment controls', () => {
  let fixture: ComponentFixture<KpiRevisionsComponent>; let page: KpiRevisionsComponent;
  let api: jasmine.SpyObj<KpiRevisionService>; let modal: jasmine.SpyObj<NzModalService>; let permissions: string[];
  const access = { reviewPeriodId: 4, reviewPeriodName: 'Annual Review', periodStatus: 'OPEN' as const,
    enabled: false, closeDate: null, available: false, expired: false, changedAt: null, changedBy: null };
  beforeEach(async () => {
    permissions = [];
    api = jasmine.createSpyObj<KpiRevisionService>('api', ['periods', 'plans', 'list', 'access', 'saveAccess']);
    api.periods.and.returnValue(of([{ id: 3, name: 'Previous', status: 'CLOSED', startDate: null, endDate: null, kpiSetupDeadline: null },
      { id: 4, name: 'Open Review', status: 'OPEN', startDate: null, endDate: null, kpiSetupDeadline: null }]));
    api.plans.and.returnValue(of([])); api.list.and.returnValue(of([revisionFixture()])); api.access.and.returnValue(of(access));
    api.saveAccess.and.returnValue(of({ ...access, enabled: true, available: true }));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [KpiRevisionsComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiRevisionService, useValue: api }, { provide: AuthService, useValue: { userId: 'superior', hasRole: (p: string) => permissions.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
  });
  function start() { fixture = TestBed.createComponent(KpiRevisionsComponent); page = fixture.componentInstance; fixture.detectChanges(); }
  it('selects an Open period and scopes both plans and revisions to it', () => {
    start(); expect(page.periodId).toBe(4); expect(api.plans).toHaveBeenCalledWith(4); expect(api.list).toHaveBeenCalledWith(4);
    expect(api.access).not.toHaveBeenCalled();
  });
  it('loads amendment controls only with the amendment permission', () => {
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS']; start(); expect(api.access).toHaveBeenCalledWith(4); expect(page.enabled).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('KPI_REVISIONS.ACCESS_DISABLED');
  });
  it('confirms and saves access settings separately from the period configuration', () => {
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS']; start(); page.enabled = true; page.closeDate = '2026-12-31'; page.saveAccess();
    const callback = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (callback && !(callback instanceof EventEmitter)) callback(undefined);
    expect(api.saveAccess).toHaveBeenCalledWith(4, true, '2026-12-31'); expect(page.accessDirty).toBeFalse();
  });
  it('never writes amendment controls in a Closed period', () => {
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS']; start(); page.access = { ...access, periodStatus: 'CLOSED' }; page.enabled = true;
    page.saveAccess(); expect(api.saveAccess).not.toHaveBeenCalled(); expect(modal.confirm).not.toHaveBeenCalled();
  });
  it('keeps the page locked until scoped loading finishes', () => {
    const pending = new Subject<KpiRevision[]>(); api.list.and.returnValue(pending); start();
    expect(page.busy).toBeTrue(); expect(page.locked).toBeTrue(); pending.next([]); pending.complete(); expect(page.busy).toBeFalse();
  });
  it('filters revision states without changing the selected period', () => {
    start(); page.revisions.push(revisionFixture({ id: 3, status: 'RETURNED' })); page.filter = 'RETURNED';
    expect(page.visibleRevisions.map(r => r.id)).toEqual([3]); expect(page.periodId).toBe(4);
  });
  it('supports employee and level filtering for HR Individual revision requests', () => {
    start(); page.revisions.push(revisionFixture({ id: 8, level: 'INDIVIDUAL', employeeName: 'Bob', status: 'PENDING_APPROVAL' }));
    page.plans = [revisionPlan(), revisionPlan({ id: 4, level: 'INDIVIDUAL', employeeName: 'Bob', status: 'APPROVED' })];
    page.level = 'INDIVIDUAL'; page.search = 'bob'; page.filter = 'PENDING_APPROVAL';
    expect(page.visibleRevisions.map(r => r.id)).toEqual([8]); expect(page.visiblePlans.map(p => p.id)).toEqual([4]);
  });
  it('uses the Superior shared-period queue without loading HR controls or confirmed plans', () => {
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS', 'CAN_REVIEW_INDIVIDUAL_KPI'];
    fixture = TestBed.createComponent(KpiRevisionsComponent); page = fixture.componentInstance;
    fixture.componentRef.setInput('superiorOnly', true); fixture.componentRef.setInput('sharedPeriod', true); fixture.componentRef.setInput('periodId', 4);
    fixture.detectChanges();
    expect(api.periods).not.toHaveBeenCalled(); expect(api.list).toHaveBeenCalledWith(4, 'INDIVIDUAL');
    expect(api.plans).not.toHaveBeenCalled(); expect(api.access).not.toHaveBeenCalled(); expect(page.canManageAccess).toBeFalse();
    page.revisions = [revisionFixture({ id: 8, level: 'INDIVIDUAL', revisionSuperiorId: 'superior' }),
      revisionFixture({ id: 9, level: 'INDIVIDUAL', revisionSuperiorId: 'someone-else' })];
    expect(page.visibleRevisions.map(r => r.id)).toEqual([8]);
    fixture.componentRef.setInput('periodId', 3); fixture.detectChanges(); expect(api.list).toHaveBeenCalledWith(3, 'INDIVIDUAL');
    expect(page.availableStatuses).toContain('PENDING_APPROVAL'); expect(page.availableStatuses).not.toContain('PENDING_MD_APPROVAL');
  });
});
