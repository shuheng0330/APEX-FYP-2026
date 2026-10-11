import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { KpiRevisionWorkspaceComponent } from './kpi-revision-workspace.component';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { KpiRevision } from '../../models/kpi-revision.model';
import { revisionFixture, revisionItem } from './kpi-revision.test-data';
import { KpiItemEditorComponent } from './kpi-item-editor.component';

describe('Shared KPI revision workspace', () => {
  let fixture: ComponentFixture<KpiRevisionWorkspaceComponent>; let page: KpiRevisionWorkspaceComponent;
  let api: jasmine.SpyObj<KpiRevisionService>; let modal: jasmine.SpyObj<NzModalService>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiRevisionService>('revisions', ['get', 'save', 'decide']);
    api.get.and.callFake(id => of(id === 1 ? revisionFixture({ id: 1, baseVersionId: null, versionNumber: 1, status: 'EFFECTIVE', items: [revisionItem({ id: 10 })] }) : revisionFixture()));
    api.save.and.callFake((revision, items) => of({ ...revision, items, lockVersion: revision.lockVersion + 1 }));
    api.decide.and.returnValue(of(revisionFixture({ status: 'EFFECTIVE', allowedActions: [] })));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [KpiRevisionWorkspaceComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: KpiRevisionService, useValue: api },
      { provide: NZ_ICONS, useValue: [CloseOutline, LoadingOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(KpiRevisionWorkspaceComponent); page = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() { const callback = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (callback && !(callback instanceof EventEmitter)) callback(undefined); }
  it('loads immutable baseline definitions before enabling revision editing', () => {
    page.open(2); expect(api.get.calls.allArgs()).toEqual([[2], [1]]); expect(page.baselineItems[0].id).toBe(10);
    expect(page.can('EDIT')).toBeTrue();
  });
  it('validates Apply to Plan early and automatically persists valid edits', () => {
    page.open(2); page.openItem(null); page.applyItem(); expect(api.save).not.toHaveBeenCalled(); expect(page.fieldErrors['name']).toBeTruthy();
    page.editorItems = [revisionItem({ id: undefined, name: 'New target' })]; page.applyItem();
    expect(api.save).toHaveBeenCalled(); expect(api.save.calls.mostRecent().args[1].length).toBe(2);
    expect(page.editorVisible).toBeFalse(); expect(page.revision?.lockVersion).toBe(4);
  });
  it('prevents duplicate item names without saving', () => {
    page.open(2); page.openItem(null); page.editorItems = [revisionItem({ name: ' revenue ' })]; page.applyItem();
    expect(page.fieldErrors['name']).toBeTruthy(); expect(api.save).not.toHaveBeenCalled();
  });
  it('preserves the editor on a failed save and prevents stale writes', () => {
    page.open(2); page.openItem(0); page.editorItems[0].target = '200 units';
    api.save.and.returnValue(throwError(() => ({ status: 409, error: { message: 'Revision changed' } }))); page.applyItem();
    expect(page.editorVisible).toBeTrue(); expect(page.editorItems[0].target).toBe('200 units');
    expect(page.itemError).toBe('Revision changed'); expect(page.can('EDIT')).toBeFalse(); expect(page.can('CONFIRM')).toBeFalse();
  });
  it('uses backend actions, including cancellation while access is disabled, without granting editing', () => {
    page.revision = revisionFixture({ allowedActions: ['CANCEL'], blockers: ['Revisions disabled'] });
    expect(page.can('CANCEL')).toBeTrue(); expect(page.can('EDIT')).toBeFalse(); page.openItem(null); expect(page.editorVisible).toBeFalse();
    page.act('CANCEL'); page.reason = ' '; page.submitReason(); expect(page.reasonError).toBeTrue(); expect(api.decide).not.toHaveBeenCalled();
    page.reason = 'No longer needed'; page.submitReason(); expect(api.decide).toHaveBeenCalledWith(jasmine.anything(), 'CANCEL', 'No longer needed');
  });
  it('requires a reason for returning a Department revision and offers no approval when not allowed', () => {
    page.revision = revisionFixture({ level: 'DEPARTMENT', status: 'PENDING_HOD_CONFIRMATION', allowedActions: ['RETURN', 'CONFIRM'] });
    page.act('APPROVE'); expect(modal.confirm).not.toHaveBeenCalled(); page.act('RETURN'); page.submitReason();
    expect(api.decide).not.toHaveBeenCalled(); page.reason = 'Correct the targets'; page.submitReason();
    expect(api.decide).toHaveBeenCalledWith(jasmine.anything(), 'RETURN', 'Correct the targets');
  });
  it('confirms the whole Company revision without another approval', () => {
    page.open(2); page.act('CONFIRM'); confirm(); expect(api.decide).toHaveBeenCalledWith(jasmine.anything(), 'CONFIRM', undefined);
    expect(page.revision?.status).toBe('EFFECTIVE'); expect(page.can('EDIT')).toBeFalse();
  });
  it('treats Superior-prepared Individual confirmation as the only approval', () => {
    page.revision = revisionFixture({ level: 'INDIVIDUAL', preparationMode: 'SUPERIOR', employeeName: 'Bob',
      revisionSuperiorId: 'superior', allowedActions: ['EDIT', 'CONFIRM'] });
    expect(page.label('CONFIRM')).toBe('KPI_REVISIONS.APPROVE_APPLY_INDIVIDUAL');
    api.decide.and.returnValue(of(revisionFixture({ level: 'INDIVIDUAL', status: 'EFFECTIVE', allowedActions: [], versionNumber: 2,
      approvedBy: 'superior', approvedAt: '2026-10-11T12:00:00Z', effectiveAt: '2026-10-11T12:00:00Z' })));
    page.act('CONFIRM'); expect(modal.confirm.calls.mostRecent().args[0]?.nzContent).toBe('KPI_REVISIONS.INDIVIDUAL_CONFIRM_HELP.CONFIRM');
    confirm(); expect(api.decide).toHaveBeenCalledOnceWith(jasmine.anything(), 'CONFIRM', undefined);
    expect(page.revision?.approvedBy).toBe('superior'); expect(page.revision?.effectiveAt).toBe(page.revision?.approvedAt);
    expect(page.can('APPROVE')).toBeFalse();
  });
  it('sends an HR-prepared Individual proposal to the Superior rather than MD', () => {
    page.revision = revisionFixture({ level: 'INDIVIDUAL', preparationMode: 'HR_MD', allowedActions: ['EDIT', 'SUBMIT'] });
    expect(page.label('SUBMIT')).toBe('KPI_REVISIONS.SUBMIT_INDIVIDUAL');
    api.decide.and.returnValue(of(revisionFixture({ level: 'INDIVIDUAL', status: 'PENDING_APPROVAL', allowedActions: [] })));
    page.act('SUBMIT'); confirm(); expect(page.revision?.status).toBe('PENDING_APPROVAL'); expect(page.can('EDIT')).toBeFalse();
  });
  it('allows the Superior to approve or return a read-only HR proposal with a required reason', () => {
    page.revision = revisionFixture({ level: 'INDIVIDUAL', preparationMode: 'HR_MD', status: 'PENDING_APPROVAL', allowedActions: ['APPROVE', 'RETURN'] });
    expect(page.label('APPROVE')).toBe('KPI_REVISIONS.ACTION.APPROVE'); expect(page.label('RETURN')).toBe('KPI_REVISIONS.RETURN_INDIVIDUAL');
    page.openItem(0); expect(page.editorVisible).toBeFalse(); page.act('RETURN'); page.reason = ' '; page.submitReason();
    expect(page.reasonError).toBeTrue(); expect(api.decide).not.toHaveBeenCalled();
    page.reason = 'Clarify the target'; page.submitReason(); expect(api.decide).toHaveBeenCalledWith(jasmine.anything(), 'RETURN', 'Clarify the target');
  });
  it('keeps returned or incomplete Individual proposals editable without enabling premature resubmission', () => {
    page.revision = revisionFixture({ level: 'INDIVIDUAL', status: 'RETURNED', revisionRequired: true, allowedActions: ['EDIT'],
      blockers: ['Save meaningful changes before resubmitting'] });
    page.act('SUBMIT'); page.act('CONFIRM'); expect(modal.confirm).not.toHaveBeenCalled();
    page.openItem(0); expect(page.editorVisible).toBeTrue();
  });
  it('shows the employee, routed Superior and approval audit in Individual details', () => {
    page.visible = true; page.revision = revisionFixture({ level: 'INDIVIDUAL', preparationMode: 'SUPERIOR', employeeName: 'Bob Smith',
      revisionSuperiorId: 'superior', status: 'EFFECTIVE', allowedActions: [], approvedBy: 'superior', approvedAt: '2026-10-11T12:00:00Z',
      effectiveAt: '2026-10-11T12:00:00Z', actorNames: { superior: 'Alice Johnson' },
      decisionHistory: [{ action: 'SUPERIOR_APPROVED', actor: 'superior', at: '2026-10-11T12:00:00Z', from: 'DRAFT', to: 'EFFECTIVE', reason: null }] });
    fixture.detectChanges(); const body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.textContent).toContain('Bob Smith'); expect(body.textContent).toContain('Alice Johnson');
    expect(body.textContent).toContain('KPI_REVISIONS.APPROVER'); expect(body.textContent).toContain('KPI_REVISIONS.EFFECTIVE_ON');
    expect(body.textContent).toContain('KPI_REVISIONS.DECISION.SUPERIOR_APPROVED');
  });
  it('blocks duplicate decisions while saving', () => {
    const pending = new Subject<KpiRevision>(); api.decide.and.returnValue(pending);
    page.open(2); page.act('CONFIRM'); confirm(); page.act('CONFIRM'); expect(api.decide).toHaveBeenCalledTimes(1);
    pending.next(revisionFixture({ status: 'EFFECTIVE', allowedActions: [] })); pending.complete(); expect(page.busy).toBeFalse();
  });
  it('protects unapplied edits and preserves them when leaving is cancelled', async () => {
    page.open(2); page.openItem(0); page.editorItems[0].target = '200 units'; const leaving = page.closeItem();
    const callback = modal.confirm.calls.mostRecent().args[0]?.nzOnCancel;
    if (callback && !(callback instanceof EventEmitter)) callback(undefined); await leaving;
    expect(page.editorVisible).toBeTrue(); expect(page.editorItems[0].target).toBe('200 units');
  });
  it('shows Target advisory on meaningful change only, with no additional scoring requirement', () => {
    const editorFixture = TestBed.createComponent(KpiItemEditorComponent); const editor = editorFixture.componentInstance;
    const item = revisionItem(); editor.items = [item]; editor.baselineItems = [revisionItem({ id: 10 })]; editor.singleItem = true;
    expect(editor.targetNeedsReview(item)).toBeFalse(); item.target = '200 units'; expect(editor.targetNeedsReview(item)).toBeTrue();
    item.target = ' 100  units '; expect(editor.targetNeedsReview(item)).toBeFalse();
    expect(item.scoringDefinitions[5]).toBe('Exceeds');
  });
});
