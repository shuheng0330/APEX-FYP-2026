import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { KpiVersionHistoryComponent } from './kpi-version-history.component';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { revisionFixture } from './kpi-revision.test-data';

describe('Read-only KPI version history', () => {
  let fixture: ComponentFixture<KpiVersionHistoryComponent>; let page: KpiVersionHistoryComponent; let api: jasmine.SpyObj<KpiRevisionService>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiRevisionService>('api', ['history']); api.history.and.returnValue(of([]));
    await TestBed.configureTestingModule({ imports: [KpiVersionHistoryComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: KpiRevisionService, useValue: api }] }).compileComponents();
    fixture = TestBed.createComponent(KpiVersionHistoryComponent); page = fixture.componentInstance; page.planId = 1; fixture.detectChanges();
  });
  it('does not load or mutate data before opening history', () => { expect(api.history).not.toHaveBeenCalled(); page.open(); expect(api.history).toHaveBeenCalledWith(1); });
  it('selects the effective version rather than an unfinished or cancelled proposal', () => {
    const current = revisionFixture({ id: 3, versionNumber: 3, status: 'EFFECTIVE', confirmedBy: 'md', baseVersionId: 1 });
    api.history.and.returnValue(of([revisionFixture({ id: 5, versionNumber: 5, status: 'CANCELLED' }), current,
      revisionFixture({ id: 1, versionNumber: 1, status: 'SUPERSEDED', baseVersionId: null })]));
    page.open(); expect(page.selected?.id).toBe(3); expect(page.baseNumber(current)).toBe(1); expect(page.actorName(current)).toBe('Managing Director');
  });
  it('retains only server-authorised history without requesting hidden versions', () => {
    api.history.and.returnValue(of([revisionFixture({ status: 'EFFECTIVE', allowedActions: [], decisionHistory: [] })]));
    page.open(); expect(page.versions.length).toBe(1); expect(api.history).toHaveBeenCalledTimes(1);
  });
  it('shows only confirmed history in My KPI Plan even for users with additional operator permissions', () => {
    page.effectiveOnly = true;
    api.history.and.returnValue(of([revisionFixture({ id: 2, status: 'DRAFT' }), revisionFixture({ id: 3, status: 'CANCELLED' }),
      revisionFixture({ id: 1, versionNumber: 1, status: 'SUPERSEDED' }), revisionFixture({ id: 4, versionNumber: 4, status: 'EFFECTIVE' })]));
    page.open(); expect(page.versions.map(v => v.id)).toEqual([4, 1]);
    expect(page.selected?.id).toBe(4);
  });
  it('shows a friendly API failure without stale prior history', () => {
    page.selected = revisionFixture(); api.history.and.returnValue(throwError(() => ({ error: { message: 'You cannot view this plan.' } })));
    page.open(); expect(page.selected).toBeNull(); expect(page.versions).toEqual([]); expect(page.error).toBe('You cannot view this plan.');
  });
  it('shows the approved Individual version and approval date without employee access to pending proposals', () => {
    page.effectiveOnly = true;
    api.history.and.returnValue(of([revisionFixture({ level: 'INDIVIDUAL', id: 5, status: 'PENDING_APPROVAL' }),
      revisionFixture({ level: 'INDIVIDUAL', id: 4, versionNumber: 4, status: 'EFFECTIVE', employeeName: 'Bob Smith',
        approvedBy: 'superior', approvedAt: '2026-10-11T12:00:00Z', effectiveAt: '2026-10-11T12:00:00Z', actorNames: { superior: 'Alice Johnson' } })]));
    page.open(); fixture.detectChanges();
    expect(page.versions.map(v => v.id)).toEqual([4]);
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.textContent).toContain('Bob Smith'); expect(body.textContent).toContain('Alice Johnson');
    expect(body.textContent).toContain('KPI_REVISIONS.APPROVER');
  });
  it('uses consistent blue button disclosures with unique valid targets and independent expansion', () => {
    api.history.and.returnValue(of([revisionFixture()])); page.open(); fixture.detectChanges();
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.querySelector('details')).toBeNull();
    expect(body.querySelector('.history-disclosures')).toBeNull();
    expect(getComputedStyle(body.querySelector('.history-version-heading')!).marginTop).toBe('28px');
    const plan = body.querySelector<HTMLButtonElement>('.history-plan > button')!;
    const activity = body.querySelector<HTMLButtonElement>('.revision-activity > button')!;
    expect(plan.parentElement!.compareDocumentPosition(activity.parentElement!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(plan.type).toBe('button'); expect(plan.textContent).toContain('KPI_REVISIONS.VIEW_FULL_PLAN');
    expect(plan.getAttribute('aria-expanded')).toBe('false');
    expect(document.getElementById(plan.getAttribute('aria-controls')!)?.hidden).toBeTrue();
    expect(activity.textContent).toContain('KPI_REVISIONS.VIEW_ACTIVITY');
    expect(plan.getAttribute('aria-controls')).not.toBe(activity.getAttribute('aria-controls'));
    plan.click(); fixture.detectChanges();
    expect(plan.textContent).toContain('KPI_REVISIONS.HIDE_FULL_PLAN'); expect(plan.getAttribute('aria-expanded')).toBe('true');
    const item = body.querySelector<HTMLButtonElement>('.history-item-heading button')!;
    expect(item.textContent).toContain('KPI_REVISIONS.VIEW_KPI_DETAILS'); expect(item.getAttribute('aria-expanded')).toBe('false');
    expect(document.getElementById(item.getAttribute('aria-controls')!)).not.toBeNull();
    item.click(); fixture.detectChanges();
    expect(item.textContent).toContain('KPI_REVISIONS.HIDE_KPI_DETAILS'); expect(item.getAttribute('aria-expanded')).toBe('true');
    expect(body.querySelector('app-kpi-item-editor')).not.toBeNull();
    activity.click(); fixture.detectChanges();
    expect(activity.textContent).toContain('KPI_REVISIONS.HIDE_ACTIVITY');
    expect(body.querySelector('app-kpi-revision-activity')).not.toBeNull();
    expect(page.fullPlanVisible).toBeTrue();
    item.click(); activity.click(); plan.click(); fixture.detectChanges();
    expect(body.querySelector('app-kpi-revision-activity')).toBeNull(); expect(page.expandedItems.size).toBe(0);
  });
  it('resets all expansions on version selection and reopening', () => {
    const original = revisionFixture({ id: 1, versionNumber: 1, status: 'SUPERSEDED' });
    api.history.and.returnValue(of([revisionFixture({ status: 'EFFECTIVE' }), original])); page.open();
    page.fullPlanVisible = true; page.activityVisible = true; page.toggleItem(0);
    page.selectVersion(original);
    expect(page.fullPlanVisible).toBeFalse(); expect(page.activityVisible).toBeFalse(); expect(page.expandedItems.size).toBe(0);
    page.fullPlanVisible = true; page.activityVisible = true; page.toggleItem(0); page.visible = false; page.open();
    expect(page.fullPlanVisible).toBeFalse(); expect(page.activityVisible).toBeFalse(); expect(page.expandedItems.size).toBe(0);
  });
  it('keeps internal activity hidden from employees even when operator events are present', () => {
    page.effectiveOnly = true;
    api.history.and.returnValue(of([revisionFixture({ status: 'EFFECTIVE', decisionHistory: [
      { action: 'RETURNED', actor: 'hr', at: '2026-10-11T12:00:00Z', from: 'PENDING_APPROVAL', to: 'RETURNED', reason: 'Internal feedback' }] })]));
    page.open(); page.activityVisible = true; fixture.detectChanges();
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.querySelector('.revision-activity')).toBeNull(); expect(body.textContent).not.toContain('Internal feedback');
    expect(body.querySelector('.history-plan > button')).not.toBeNull();
  });
  it('allows viewing legacy metadata when no events were recorded and does not duplicate the summary', () => {
    api.history.and.returnValue(of([revisionFixture({ approvedBy: 'md', approvedAt: '2026-10-11T12:00:00Z' })]));
    page.open(); page.activityVisible = true; fixture.detectChanges();
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.querySelector('.version-summary')!.textContent).not.toContain('KPI_REVISIONS.APPROVER');
    expect(body.querySelector('app-kpi-revision-activity')!.textContent).toContain('KPI_REVISIONS.APPROVER');
    expect(body.querySelector('.activity-timeline')).toBeNull();
  });
  it('does not reuse disclosure IDs across history instances', () => {
    const other = TestBed.createComponent(KpiVersionHistoryComponent);
    expect(other.componentInstance.disclosureId).not.toBe(page.disclosureId); other.destroy();
  });
});
