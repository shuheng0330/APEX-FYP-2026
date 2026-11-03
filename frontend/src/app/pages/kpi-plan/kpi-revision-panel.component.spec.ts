import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, throwError } from 'rxjs';
import { KpiRevisionPanelComponent } from './kpi-revision-panel.component';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { AuthService } from '../../services/auth.service';
import { revisionFixture, revisionPlan } from './kpi-revision.test-data';

describe('KPI plan revision entry points', () => {
  let fixture: ComponentFixture<KpiRevisionPanelComponent>; let page: KpiRevisionPanelComponent;
  let api: jasmine.SpyObj<KpiRevisionService>; let permissions: string[];
  beforeEach(async () => {
    permissions = ['CAN_REVISE_COMPANY_KPI'];
    api = jasmine.createSpyObj<KpiRevisionService>('api', ['history', 'start', 'get']);
    api.history.and.returnValue(of([])); api.start.and.returnValue(of(revisionFixture()));
    api.get.and.callFake(id => of(revisionFixture({ id, baseVersionId: null })));
    TestBed.configureTestingModule({ imports: [KpiRevisionPanelComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiRevisionService, useValue: api }, { provide: AuthService, useValue: { hasRole: (p: string) => permissions.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }] });
    await TestBed.compileComponents(); fixture = TestBed.createComponent(KpiRevisionPanelComponent); page = fixture.componentInstance;
    page.plan = revisionPlan(); fixture.detectChanges();
  });
  it('checks for an unfinished revision before offering a new one', () => {
    api.history.and.returnValue(of([revisionFixture()])); spyOn(page.workspace!, 'open'); page.manage();
    expect(page.workspace!.open).toHaveBeenCalledWith(2); expect(page.requestVisible).toBeFalse(); expect(api.start).not.toHaveBeenCalled();
  });
  it('requires an initial reason and never overwrites the current plan', () => {
    page.manage(); expect(page.requestVisible).toBeTrue(); page.start(); expect(page.reasonError).toBeTrue(); expect(api.start).not.toHaveBeenCalled();
    page.reason = 'Unexpected market conditions'; page.start(); expect(api.start).toHaveBeenCalledWith(page.plan, 'Unexpected market conditions');
    expect(page.plan.currentVersionNumber).toBe(1); expect(page.requestVisible).toBeFalse();
  });
  it('allows a fresh revision after cancellation without resetting the cancelled record', () => {
    api.history.and.returnValue(of([revisionFixture({ status: 'CANCELLED', allowedActions: [] })])); page.manage();
    expect(page.requestVisible).toBeTrue(); expect(api.start).not.toHaveBeenCalled();
  });
  it('uses permissions rather than HR/MD role names and separates Individual initiation from review', () => {
    permissions = ['MD', 'Super Admin']; expect(page.canOperate).toBeFalse();
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS']; expect(page.canOperate).toBeTrue(); expect(page.canStart).toBeFalse();
    page.plan = revisionPlan({ level: 'INDIVIDUAL' }); expect(page.canOperate).toBeTrue(); expect(page.canStart).toBeFalse();
    permissions = ['CAN_REVIEW_INDIVIDUAL_KPI']; expect(page.canOperate).toBeTrue(); expect(page.canStart).toBeFalse();
    permissions = ['CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS']; expect(page.canStart).toBeTrue();
  });
  it('defaults Individual requests to Superior preparation and sends the selected path', () => {
    permissions = ['CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'];
    page.plan = revisionPlan({ level: 'INDIVIDUAL', status: 'APPROVED', employeeName: 'Bob' });
    page.manage(); fixture.detectChanges();
    expect(document.querySelector('.ant-modal-body')?.textContent).toContain('Bob');
    expect(document.querySelector('.ant-modal-body')?.textContent).toContain('KPI_REVISIONS.PREPARATION');
    expect(page.preparationMode).toBe('SUPERIOR'); expect(page.requestVisible).toBeTrue();
    page.reason = ' '; page.start(); expect(api.start).not.toHaveBeenCalled();
    page.reason = 'Change target'; page.preparationMode = 'HR_MD'; page.start();
    expect(api.start).toHaveBeenCalledWith(page.plan, 'Change target', 'HR_MD');
  });
  it('opens a Superior existing Individual revision without allowing initiation', () => {
    permissions = ['CAN_REVIEW_INDIVIDUAL_KPI']; page.plan = revisionPlan({ level: 'INDIVIDUAL' });
    api.history.and.returnValue(of([revisionFixture({ level: 'INDIVIDUAL', allowedActions: ['PREPARE'] })]));
    spyOn(page.workspace!, 'open'); page.manage();
    expect(page.workspace!.open).toHaveBeenCalledWith(2); expect(page.requestVisible).toBeFalse(); expect(api.start).not.toHaveBeenCalled();
  });
  it('allows scoped Department request entry but no creation for Closed periods', () => {
    permissions = ['CAN_MANAGE_DEPARTMENT_KPI']; page.plan = revisionPlan({ level: 'DEPARTMENT', status: 'APPROVED' });
    expect(page.canStart).toBeTrue(); page.plan.reviewPeriodStatus = 'CLOSED'; page.manage();
    expect(page.requestVisible).toBeFalse(); expect(api.start).not.toHaveBeenCalled();
  });
  it('preserves the request reason when access is disabled or a competing request exists', () => {
    page.manage(); page.reason = 'Market change'; api.start.and.returnValue(throwError(() => ({ status: 409, error: { message: 'Another revision exists' } })));
    page.start(); expect(page.requestVisible).toBeTrue(); expect(page.reason).toBe('Market change'); expect(page.error).toBe('Another revision exists');
  });
  it('keeps employee history read-only regardless of other permissions', () => {
    page.historyOnly = true; expect(page.canOperate).toBeFalse(); page.manage(); expect(api.history).not.toHaveBeenCalled();
  });
  it('loads working progress when a confirmed plan is selected', () => {
    api.history.and.returnValue(of([revisionFixture({ status: 'PENDING_MD_APPROVAL', allowedActions: [] })]));
    fixture.componentRef.setInput('plan', revisionPlan()); fixture.detectChanges();
    expect(page.working?.status).toBe('PENDING_MD_APPROVAL');
    expect(fixture.nativeElement.textContent).toContain('KPI_REVISIONS.CONTINUE');
  });
  it('does not offer Company creation to HR or a Department request to MD reviewers', () => {
    permissions = ['CAN_MANAGE_KPI_AMENDMENTS']; fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('KPI_REVISIONS.MANAGE_COMPANY');
    permissions = ['CAN_APPROVE_DEPARTMENT_KPI']; page.plan = revisionPlan({ level: 'DEPARTMENT' }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('KPI_REVISIONS.MANAGE_DEPARTMENT');
  });
  it('uses queue metadata for compact rows but rechecks history before starting a request', () => {
    fixture.componentRef.setInput('compact', true); fixture.componentRef.setInput('workingRevision', null);
    fixture.componentRef.setInput('plan', revisionPlan()); fixture.detectChanges();
    expect(api.history).not.toHaveBeenCalled();
    api.history.and.returnValue(of([revisionFixture({ id: 7 })])); spyOn(page.workspace!, 'open'); page.manage();
    expect(api.history).toHaveBeenCalledWith(1); expect(page.workspace!.open).toHaveBeenCalledWith(7);
    expect(page.requestVisible).toBeFalse();
  });
});
