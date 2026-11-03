import { CommonModule } from '@angular/common';
import { Component, DestroyRef, EventEmitter, HostListener, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { finalize, map, of, switchMap } from 'rxjs';
import { KpiItem, KpiItemErrors, emptyKpiItem, kpiItemErrors } from '../../models/kpi-plan.model';
import { KpiRevision, KpiRevisionAction, KpiRevisionDecision, validRevisionReason } from '../../models/kpi-revision.model';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { KpiChangesComponent } from './kpi-changes.component';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiRevisionActivityComponent } from './kpi-revision-activity.component';
import { revisionSteps, revisionNextStep } from './kpi-revision-progress';

const weightageBlocker = 'KPI item weightages within this plan must total exactly 100%';

@Component({
  selector: 'app-kpi-revision-workspace', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzModalModule, NzInputModule,
    KpiChangesComponent, KpiItemEditorComponent, KpiRevisionActivityComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss', './kpi-revision.scss'],
  templateUrl: './kpi-revision-workspace.component.html'
})
export class KpiRevisionWorkspaceComponent {
  private static nextId = 0;
  readonly disclosureId = `revision-workspace-${KpiRevisionWorkspaceComponent.nextId++}`;
  @Output() changed = new EventEmitter<KpiRevision>();
  private readonly destroyRef = inject(DestroyRef);
  revision: KpiRevision | null = null; baselineItems: KpiItem[] = [];
  baseVersionNumber: number | null = null; fullPlanVisible = false; activityVisible = false;
  get steps() { return this.revision ? revisionSteps(this.revision) : []; }
  get nextStep() { return this.revision ? revisionNextStep(this.revision) : ''; }
  get weightageState() {
    const total = this.revision?.totalWeightage ?? 0;
    return total === 100 ? 'valid' : total > 100 ? 'over' : 'under';
  }
  get weightageDifference() { return Math.abs((this.revision?.totalWeightage ?? 0) - 100); }
  get weightageProgress() { return Math.min(100, Math.max(0, this.revision?.totalWeightage ?? 0)); }
  get visibleBlockers() {
    const blockers = this.revision?.blockers ?? [];
    // Replace only the known weightage notice while its inline explanation is visible.
    return this.fullPlanVisible && this.weightageState !== 'valid'
      ? blockers.filter(blocker => blocker !== weightageBlocker) : blockers;
  }
  visible = false; busy = false; error = ''; success = ''; stale = false;
  editorVisible = false; editorReadonly = false; editorItems: KpiItem[] = []; editorIndex: number | null = null;
  editorInitial = ''; fieldErrors: KpiItemErrors = {}; itemError = '';
  reasonAction: 'RETURN' | 'CANCEL' | null = null; reason = ''; reasonError = false;
  readonly transitions: KpiRevisionDecision[] = ['PREPARE', 'SUBMIT', 'APPROVE', 'RETURN', 'CONFIRM', 'CANCEL'];
  constructor(private api: KpiRevisionService, private modal: NzModalService, private translate: TranslateService) {}
  get locked() { return this.busy || this.visible; }
  can(action: KpiRevisionAction) { return !this.stale && !!this.revision?.allowedActions.includes(action); }
  label(action: KpiRevisionDecision) {
    if (this.revision?.level === 'INDIVIDUAL') {
      if (action === 'CONFIRM') return 'KPI_REVISIONS.APPROVE_APPLY_INDIVIDUAL';
      if (action === 'SUBMIT') return 'KPI_REVISIONS.SUBMIT_INDIVIDUAL';
      if (action === 'RETURN') return 'KPI_REVISIONS.RETURN_INDIVIDUAL';
    }
    if (action === 'CONFIRM') return this.revision?.level === 'COMPANY' ? 'KPI_REVISIONS.CONFIRM_COMPANY' : 'KPI_REVISIONS.CONFIRM_DEPARTMENT';
    if (action === 'CANCEL' && this.revision?.status === 'REQUESTED') return 'KPI_REVISIONS.WITHDRAW';
    return 'KPI_REVISIONS.ACTION.' + action;
  }
  help(action: KpiRevisionDecision) {
    return this.revision?.level === 'INDIVIDUAL' ? 'KPI_REVISIONS.INDIVIDUAL_CONFIRM_HELP.' + action : 'KPI_REVISIONS.CONFIRM_HELP.' + action;
  }
  get editorDirty() { return this.editorVisible && !this.editorReadonly && JSON.stringify(this.editorItems) !== this.editorInitial; }
  get dirty() { return this.editorDirty || !!this.reason.trim(); }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent) {
    if (this.visible && (this.dirty || this.busy)) { event.preventDefault(); event.returnValue = ''; }
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy) return false;
    if (!this.dirty) return true;
    return this.confirmDiscard();
  }
  private confirmDiscard(): Promise<boolean> {
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('KPI_REVISIONS.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_REVISIONS.DISCARD_HELP'), nzOnOk: () => resolve(true), nzOnCancel: () => resolve(false) }));
  }
  async close() {
    if (await this.canLeave()) { this.visible = false; this.editorVisible = false; this.reasonAction = null; this.reason = ''; }
  }
  open(id: number) {
    if (this.busy || this.visible) return;
    this.visible = true; this.revision = null; this.error = ''; this.success = ''; this.stale = false;
    this.activityVisible = false;
    this.reasonAction = null; this.reason = ''; this.load(id);
  }
  refresh() { if (!this.busy && !this.dirty && this.revision) this.load(this.revision.id); }
  private load(id: number) {
    this.busy = true; this.error = ''; this.success = '';
    this.api.get(id).pipe(switchMap(revision => revision.baseVersionId
      ? this.api.get(revision.baseVersionId).pipe(map(base => ({ revision, baselineItems: base.items, baseNumber: base.versionNumber })))
      : of({ revision, baselineItems: [] as KpiItem[], baseNumber: null })),
      finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.baselineItems = result.baselineItems; this.baseVersionNumber = result.baseNumber;
        this.accept(result.revision); this.fullPlanVisible = this.can('EDIT'); }, error: e => this.fail(e)
    });
  }
  openItem(index: number | null, readonly = false) {
    if (this.busy || this.editorVisible || !this.revision || (!readonly && !this.can('EDIT'))) return;
    this.editorIndex = index; this.editorReadonly = readonly;
    this.editorItems = [index === null ? emptyKpiItem() : structuredClone(this.revision.items[index])];
    this.editorInitial = JSON.stringify(this.editorItems); this.fieldErrors = {}; this.itemError = ''; this.editorVisible = true;
  }
  async closeItem() {
    if (this.busy) return;
    if (!this.editorDirty || await this.confirmDiscard()) { this.editorVisible = false; this.itemError = ''; }
  }
  applyItem() {
    if (this.busy || this.editorReadonly || !this.can('EDIT') || !this.revision || !this.editorItems[0]) return;
    const item = this.editorItems[0]; this.fieldErrors = kpiItemErrors(item);
    if (item.name?.trim() && this.revision.items.some((other, i) => i !== this.editorIndex && other.name?.trim().toLowerCase() === item.name?.trim().toLowerCase())) {
      this.fieldErrors['name'] = 'KPI_PLAN.DUPLICATE_NAME';
    }
    if (Object.keys(this.fieldErrors).length) { this.itemError = this.translate.instant('KPI_PLAN.FIX_ITEM'); return; }
    const items = structuredClone(this.revision.items);
    if (this.editorIndex === null) items.push(structuredClone(item)); else items[this.editorIndex] = structuredClone(item);
    this.save(items, true);
  }
  removeItem(index: number) {
    if (this.busy || !this.can('EDIT') || !this.revision) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.REMOVE_TITLE'), nzContent: this.translate.instant('KPI_PLAN.REMOVE_HELP'),
      nzOnOk: () => { if (!this.busy && this.can('EDIT') && this.revision) this.save(this.revision.items.filter((_, i) => i !== index)); } });
  }
  private save(items: KpiItem[], closeEditor = false) {
    if (!this.revision || this.busy) return;
    this.busy = true; this.error = ''; this.itemError = '';
    this.api.save(this.revision, items).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: revision => { this.accept(revision); if (closeEditor) this.editorVisible = false; this.success = this.translate.instant('KPI_REVISIONS.SAVED'); },
      error: e => this.fail(e)
    });
  }
  act(action: KpiRevisionDecision) {
    if (this.busy || this.editorVisible || !this.can(action)) return;
    if (action === 'RETURN' || action === 'CANCEL') { this.reasonAction = action; this.reason = ''; this.reasonError = false; return; }
    this.modal.confirm({ nzTitle: this.translate.instant(this.label(action)),
      nzContent: this.translate.instant(this.help(action)), nzOnOk: () => this.decide(action) });
  }
  submitReason() {
    if (!this.reasonAction || this.busy || !this.can(this.reasonAction)) return;
    this.reasonError = !validRevisionReason(this.reason);
    if (!this.reasonError) this.decide(this.reasonAction, this.reason);
  }
  private decide(action: KpiRevisionDecision, reason?: string) {
    if (!this.revision || this.busy || !this.can(action)) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.decide(this.revision, action, reason).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: revision => { this.accept(revision); this.reasonAction = null; this.reason = ''; this.success = this.translate.instant('KPI_REVISIONS.UPDATED'); },
      error: e => this.fail(e)
    });
  }
  private accept(revision: KpiRevision) {
    if (revision.allowedActions.includes('EDIT') && !this.revision?.allowedActions.includes('EDIT')) this.fullPlanVisible = true;
    this.revision = revision; this.stale = false; this.changed.emit(revision);
  }
  actorName(actor: string | null) { return actor ? this.revision?.actorNames?.[actor] || '-' : '-'; }
  private fail(e: { status?: number; error?: { message?: string } }) {
    const message = e.error?.message || this.translate.instant('KPI_PLAN.ERROR');
    if (e.status === 409 || e.status === 403) this.stale = true;
    if (this.editorVisible) this.itemError = message; else this.error = message;
  }
}
