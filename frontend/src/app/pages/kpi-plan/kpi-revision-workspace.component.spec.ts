import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventEmitter } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
    expect(page.baseVersionNumber).toBe(1); expect(page.fullPlanVisible).toBeTrue();
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
    page.activityVisible = true;
    fixture.detectChanges(); const body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.textContent).toContain('Bob Smith'); expect(body.textContent).toContain('Alice Johnson');
    expect(body.querySelector('.revision-audit')).toBeNull();
    expect(body.querySelectorAll('.activity-timeline li').length).toBe(1);
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
  it('puts changes before the full plan and collapses that plan for reviewers', () => {
    api.get.and.callFake(id => of(id === 1 ? revisionFixture({ id: 1, baseVersionId: null, versionNumber: 1 })
      : revisionFixture({ level: 'DEPARTMENT', status: 'PENDING_MD_APPROVAL', allowedActions: ['APPROVE', 'RETURN'] })));
    page.open(2); fixture.detectChanges(); expect(page.fullPlanVisible).toBeFalse();
    let body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.querySelector('.revision-comparison')).not.toBeNull(); expect(body.querySelector('.revision-items')).toBeNull();
    page.fullPlanVisible = true; fixture.detectChanges(); body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    expect(body.querySelector('.revision-comparison')!.compareDocumentPosition(body.querySelector('.revision-items')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(body.querySelector('.revision-activity button')?.getAttribute('aria-expanded')).toBe('false');
    expect(body.querySelector('app-kpi-revision-activity')).toBeNull();
  });
  it('uses accessible View/Hide actions and resets activity when reopening without changing editor defaults', async () => {
    page.open(2); fixture.detectChanges();
    const body = document.querySelector('.ant-drawer-open .ant-drawer-body')!;
    const plan = body.querySelector<HTMLButtonElement>('.comparison-heading button')!;
    expect(plan.textContent).toContain('KPI_REVISIONS.HIDE_FULL_PLAN'); expect(plan.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById(plan.getAttribute('aria-controls')!)).not.toBeNull();
    const activity = body.querySelector<HTMLButtonElement>('.revision-activity button')!;
    expect(activity.type).toBe('button'); expect(activity.textContent).toContain('KPI_REVISIONS.VIEW_ACTIVITY');
    activity.click(); fixture.detectChanges();
    expect(activity.textContent).toContain('KPI_REVISIONS.HIDE_ACTIVITY'); expect(activity.getAttribute('aria-expanded')).toBe('true');
    expect(body.querySelector('app-kpi-revision-activity')).not.toBeNull();
    activity.click(); fixture.detectChanges(); expect(page.activityVisible).toBeFalse();
    page.activityVisible = true; await page.close(); page.open(2);
    expect(page.activityVisible).toBeFalse(); expect(page.fullPlanVisible).toBeTrue();
  });
  it('assigns unique disclosure targets to each workspace instance', () => {
    const other = TestBed.createComponent(KpiRevisionWorkspaceComponent);
    expect(other.componentInstance.disclosureId).not.toBe(page.disclosureId);
    other.destroy();
  });
  const weightageBlocker = 'KPI item weightages within this plan must total exactly 100%';
  function showWeightage(total: number, level: KpiRevision['level'] = 'COMPANY') {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { KPI_REVISIONS: {
      WEIGHTAGE_WARNING: 'Weightage needs adjustment', WEIGHTAGE_CURRENT: 'Current total',
      WEIGHTAGE_REDUCE: 'Please reduce the total by', WEIGHTAGE_INCREASE: 'Please increase the total by',
      WEIGHTAGE_FINALISE: 'before finalising the revision.', WEIGHTAGE_ACCESSIBLE: '{{total}}% out of 100%'
    } });
    translate.use('en');
    page.visible = true; page.fullPlanVisible = true;
    page.revision = revisionFixture({ level, totalWeightage: total, items: total === 0 ? [] : [revisionItem()],
      allowedActions: ['EDIT', 'CANCEL'], blockers: total === 100 ? [] : [weightageBlocker] });
    fixture.detectChanges();
    return document.querySelector<HTMLElement>('.ant-drawer-open .ant-drawer-body')!;
  }
  const weightageCases = [
    { total: 0, state: 'under', difference: '100%', color: 'rgb(183, 121, 0)' },
    { total: 90, state: 'under', difference: '10%', color: 'rgb(183, 121, 0)' },
    { total: 100, state: 'valid', difference: '', color: 'rgb(15, 159, 143)' },
    { total: 110, state: 'over', difference: '10%', color: 'rgb(209, 67, 67)' },
    { total: 99.99, state: 'under', difference: '0.01%', color: 'rgb(183, 121, 0)' },
    { total: 100.01, state: 'over', difference: '0.01%', color: 'rgb(209, 67, 67)' }
  ];
  for (const level of ['COMPANY', 'DEPARTMENT', 'INDIVIDUAL'] as const) {
    for (const example of weightageCases) {
      it(`shows the correct ${example.total}% weightage state for ${level} revisions`, () => {
        const body = showWeightage(example.total, level);
        const bar = body.querySelector<HTMLElement>('.revision-weight-progress')!;
        const fill = bar.querySelector<HTMLElement>('span')!;
        const warning = body.querySelector<HTMLElement>('.revision-weightage-warning');
        expect(bar.dataset['state']).toBe(example.state);
        expect(getComputedStyle(fill).backgroundColor).toBe(example.color);
        expect(bar.getAttribute('aria-valuenow')).toBe(String(Math.min(example.total, 100)));
        expect(fill.style.width).toBe(`${Math.min(example.total, 100)}%`);
        expect(bar.getAttribute('aria-valuetext')).toBe(`${example.total}% out of 100%`);
        expect(body.querySelector('.plan-summary .help')!.textContent).toContain(`${example.total}% / 100%`);
        if (example.state === 'valid') {
          expect(warning).toBeNull(); expect(bar.hasAttribute('aria-describedby')).toBeFalse();
        } else {
          expect(warning).not.toBeNull(); expect(warning!.getAttribute('role')).toBe('status');
          expect(bar.getAttribute('aria-describedby')).toBe(warning!.id);
          expect(warning!.previousElementSibling).toBe(body.querySelector('.plan-summary .help'));
          expect(warning!.textContent).toContain('Weightage needs adjustment');
          expect(warning!.querySelectorAll('p strong')[0].textContent).toBe(`${example.total}%`);
          expect(warning!.querySelectorAll('p strong')[1].textContent).toBe(example.difference);
          expect(warning!.textContent).toContain(example.state === 'over' ? 'Please reduce the total by' : 'Please increase the total by');
          expect(warning!.textContent).toContain('before finalising the revision.');
        }
        if (example.total === 0) expect(getComputedStyle(bar).backgroundColor).toBe('rgb(255, 245, 223)');
      });
    }
  }
  it('suppresses only the duplicated weightage blocker while the inline warning is visible', () => {
    const body = showWeightage(110);
    const unrelated = 'Weightage changes require meaningful edits before resubmitting';
    const blockers = [weightageBlocker, unrelated, 'Revisions disabled'];
    page.revision = { ...page.revision!, blockers }; fixture.detectChanges();
    expect(page.visibleBlockers).toEqual([unrelated, 'Revisions disabled']);
    expect(body.textContent).not.toContain(weightageBlocker); expect(body.textContent).toContain(unrelated);
    expect(page.revision.blockers).toEqual(blockers);
    const toggle = body.querySelector<HTMLButtonElement>('.comparison-heading button')!;
    toggle.click(); fixture.detectChanges();
    expect(body.querySelector('.revision-weightage-warning')).toBeNull(); expect(body.textContent).toContain(weightageBlocker);
    expect(page.visibleBlockers).toEqual(blockers);
    toggle.click(); fixture.detectChanges(); expect(body.textContent).not.toContain(weightageBlocker);
    page.revision = { ...page.revision!, totalWeightage: 100 }; fixture.detectChanges();
    expect(page.visibleBlockers).toEqual(blockers);
  });
  it('updates the state after applying an edit and keeps finalisation controlled by the response', () => {
    const body = showWeightage(110);
    page.revision = { ...page.revision!, items: [revisionItem({ weightage: 50 }), revisionItem({ id: 21, name: 'Profit', weightage: 60 })] };
    expect(page.can('CONFIRM')).toBeFalse();
    page.openItem(0); page.editorItems[0].weightage = 40;
    api.save.and.callFake((revision, items) => of({ ...revision, items, totalWeightage: 100, blockers: [], allowedActions: ['EDIT', 'CONFIRM', 'CANCEL'] }));
    page.applyItem(); fixture.detectChanges();
    expect(api.save).toHaveBeenCalledOnceWith(jasmine.anything(), jasmine.anything());
    expect(page.editorVisible).toBeFalse(); expect(page.can('CONFIRM')).toBeTrue();
    expect(body.querySelector('.revision-weightage-warning')).toBeNull();
    expect(body.querySelector('.revision-weight-progress')!.getAttribute('data-state')).toBe('valid');
  });
  it('updates the warning after removal, including removal of the final item', () => {
    const body = showWeightage(110);
    page.revision = { ...page.revision!, items: [revisionItem(), revisionItem({ id: 21, name: 'Profit', weightage: 10 })] };
    api.save.and.callFake((revision, items) => of({ ...revision, items, totalWeightage: items.reduce((sum, item) => sum + (item.weightage ?? 0), 0) }));
    page.removeItem(1); confirm(); fixture.detectChanges();
    expect(body.querySelector('.revision-weightage-warning')).toBeNull();
    page.removeItem(0); confirm(); fixture.detectChanges();
    expect(body.querySelector('.revision-weight-progress')!.getAttribute('data-state')).toBe('under');
    expect(body.querySelector('.revision-weightage-warning')!.textContent).toContain('100%');
    expect(page.revision?.items).toEqual([]);
  });
  it('allows valid item edits to persist in an incomplete Draft without granting finalisation', () => {
    const body = showWeightage(90);
    page.openItem(0); page.editorItems[0].weightage = 90; page.applyItem(); fixture.detectChanges();
    expect(api.save).toHaveBeenCalled(); expect(page.editorVisible).toBeFalse(); expect(page.can('CONFIRM')).toBeFalse();
    expect(body.querySelector('.revision-weightage-warning')!.textContent).toContain('Please increase the total by');
  });
  it('removes the global Target reminder but retains the edit-form reminder and its reversion behaviour', () => {
    const body = showWeightage(100);
    page.revision = { ...page.revision!, targetChangedItemIds: [20], items: [revisionItem({ target: '200 units' })] };
    page.baselineItems = [revisionItem({ id: 10 })]; fixture.detectChanges();
    expect(body.textContent).not.toContain('KPI_REVISIONS.TARGET_REMINDER');
    page.openItem(0); fixture.detectChanges();
    const editor = document.querySelector('app-kpi-item-editor')!;
    expect(editor.textContent).toContain('KPI_REVISIONS.TARGET_REMINDER');
    page.editorItems[0].target = ' 100  units '; fixture.detectChanges();
    expect(editor.textContent).not.toContain('KPI_REVISIONS.TARGET_REMINDER');
  });
});
