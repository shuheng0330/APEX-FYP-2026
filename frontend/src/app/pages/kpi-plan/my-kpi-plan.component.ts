import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { KpiItem, KpiItemErrors, KpiLevel, KpiPeriodContext, KpiPlan, emptyKpiItem, kpiItemErrors, kpiPlanComplete } from '../../models/kpi-plan.model';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { canResubmit } from '../../models/submission-revision.model';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzDrawerModule, NzModalModule, KpiItemEditorComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './my-kpi-plan.component.html'
})
export class MyKpiPlanComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  periods: KpiPeriodContext[] = [];
  plans: KpiPlan[] = [];
  assignedPlans: KpiPlan[] = [];
  periodId: number | null = null;
  plan: KpiPlan | null = null;
  items: KpiItem[] = [];
  busy = false;
  ready = false;
  error = '';
  success = '';
  drawerVisible = false;
  editingIndex: number | null = null;
  editorItems: KpiItem[] = [];
  itemScope: KpiLevel = 'INDIVIDUAL';
  viewOnly = false;
  fieldErrors: KpiItemErrors = {};
  itemError = '';

  constructor(private api: IndividualKpiPlanService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  load() {
    if (this.busy) return;
    this.busy = true; this.ready = false; this.error = '';
    forkJoin({ periods: this.api.periods(), plans: this.api.mine() }).pipe(
      switchMap(result => {
        this.periods = result.periods; this.plans = result.plans;
        this.periodId = this.periods.find(period => period.status === 'OPEN')?.id
          ?? this.periods.find(period => period.status === 'UPCOMING')?.id ?? this.periods[0]?.id ?? null;
        this.selectPlan();
        return this.periodId === null ? of([] as KpiPlan[]) : this.api.assigned(this.periodId);
      }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)
    ).subscribe({ next: assigned => { this.setAssigned(assigned); this.ready = true; }, error: error => this.fail(error) });
  }
  get selectedPeriod() { return this.periods.find(period => period.id === this.periodId); }
  get readonly() {
    return !this.ready || !this.selectedPeriod || !['UPCOMING', 'OPEN'].includes(this.selectedPeriod.status)
      || !this.selectedPeriod.participantsSnapshottedAt || (!!this.plan && !['DRAFT', 'RETURNED'].includes(this.plan.status));
  }
  get drawerReadonly() { return this.viewOnly || this.readonly; }
  get total() { return this.items.reduce((sum, item) => sum + Math.round((item.weightage ?? 0) * 100), 0) / 100; }
  get canSubmit() { return !this.readonly && !!this.plan && canResubmit(this.plan) && kpiPlanComplete(this.items); }
  get submitBlocker(): { key: string; params?: { amount: number } } | null {
    if (this.readonly) return null;
    if (!canResubmit(this.plan)) return { key: 'APPROVAL_REVISION.CHANGE_REQUIRED' };
    if (!this.items.length) return { key: 'INDIVIDUAL_KPI.ADD_BEFORE_SUBMIT' };
    if (this.items.some(item => Object.keys(kpiItemErrors(item)).length)) return { key: 'INDIVIDUAL_KPI.COMPLETE_BEFORE_SUBMIT' };
    if (new Set(this.items.map(item => item.name?.trim().toLowerCase())).size !== this.items.length) return { key: 'INDIVIDUAL_KPI.DUPLICATE_NAME' };
    if (this.total < 100) return { key: 'INDIVIDUAL_KPI.WEIGHT_REMAINING', params: { amount: Math.round((100 - this.total) * 100) / 100 } };
    if (this.total > 100) return { key: 'INDIVIDUAL_KPI.WEIGHT_EXCESS', params: { amount: Math.round((this.total - 100) * 100) / 100 } };
    return null;
  }
  changePeriod(id: number) {
    if (this.busy || this.drawerVisible || !this.periods.some(period => period.id === id)) return;
    this.periodId = id; this.selectPlan(); this.assignedPlans = []; this.ready = false; this.busy = true;
    this.api.assigned(id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assigned => { this.setAssigned(assigned); this.ready = true; }, error: error => this.fail(error)
    });
  }
  private selectPlan() {
    this.plan = this.plans.find(plan => plan.reviewPeriodId === this.periodId) ?? null;
    this.items = structuredClone(this.plan?.items ?? []); this.error = ''; this.success = '';
  }
  private setAssigned(plans: KpiPlan[]) { this.assignedPlans = plans.filter(plan => plan.level !== 'INDIVIDUAL'); }
  openItem(index: number | null, viewOnly = false) {
    if (this.busy || !this.ready || (index === null && this.readonly)) return;
    this.editingIndex = index; this.itemScope = 'INDIVIDUAL'; this.viewOnly = viewOnly;
    this.openEditor(index === null ? emptyKpiItem() : this.items[index]);
  }
  viewAssigned(item: KpiItem, scope: KpiLevel) {
    if (this.busy || !this.ready) return;
    this.itemScope = scope; this.viewOnly = true; this.editingIndex = null; this.openEditor(item);
  }
  private openEditor(item: KpiItem) {
    this.fieldErrors = {}; this.itemError = ''; this.editorItems = [structuredClone(item)]; this.drawerVisible = true;
  }
  applyItem() {
    if (this.drawerReadonly || this.busy) return;
    const item = this.editorItems[0]; if (!item) return;
    this.fieldErrors = kpiItemErrors(item);
    if (item.name?.trim() && this.items.some((existing, index) => index !== this.editingIndex &&
      existing.name?.trim().toLowerCase() === item.name?.trim().toLowerCase())) this.fieldErrors['name'] = 'INDIVIDUAL_KPI.DUPLICATE_NAME';
    if (Object.keys(this.fieldErrors).length) { this.itemError = this.translate.instant('KPI_PLAN.FIX_ITEM'); return; }
    const items = this.editingIndex === null ? [...this.items, structuredClone(item)]
      : this.items.map((existing, index) => index === this.editingIndex ? structuredClone(item) : existing);
    this.persistDraft(items, () => this.drawerVisible = false);
  }
  removeItem(index: number) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('INDIVIDUAL_KPI.REMOVE_TITLE'),
      nzContent: this.translate.instant('INDIVIDUAL_KPI.REMOVE_HELP'),
      nzOnOk: () => { if (!this.busy && !this.readonly) this.persistDraft(this.items.filter((_, itemIndex) => index !== itemIndex)); } });
  }
  private persistDraft(items: KpiItem[], onSaved?: () => void) {
    if (this.readonly || this.busy || this.periodId === null) return;
    this.busy = true; this.error = ''; this.success = ''; this.itemError = '';
    const request = { reviewPeriodId: this.periodId, items };
    const operation = this.plan ? this.api.update(this.plan.id, request) : this.api.create(request);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); onSaved?.(); this.success = this.translate.instant('INDIVIDUAL_KPI.SAVED'); },
      error: error => { if (this.drawerVisible) this.itemError = this.errorMessage(error); else this.fail(error); }
    });
  }
  confirmSubmit() {
    if (!this.canSubmit || this.busy || this.drawerVisible) return;
    this.modal.confirm({ nzTitle: this.translate.instant('INDIVIDUAL_KPI.SUBMIT'),
      nzContent: this.translate.instant('INDIVIDUAL_KPI.SUBMIT_CONFIRM'), nzOnOk: () => this.submitPlan() });
  }
  submitPlan() {
    if (!this.canSubmit || this.busy || this.drawerVisible || !this.plan) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.submit(this.plan.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); this.success = this.translate.instant('INDIVIDUAL_KPI.SUBMITTED'); }, error: error => this.fail(error)
    });
  }
  private acceptPlan(plan: KpiPlan) {
    this.plan = plan; this.plans = [...this.plans.filter(existing => existing.id !== plan.id), plan]; this.items = structuredClone(plan.items);
  }
  private errorMessage(error: { error?: { message?: string } }) {
    const message = error.error?.message;
    return message?.includes('publication-time participant snapshot') ? this.translate.instant('INDIVIDUAL_KPI.PERIOD_UNAVAILABLE')
      : message || this.translate.instant('KPI_PLAN.ERROR');
  }
  private fail(error: { error?: { message?: string } }) { this.error = this.errorMessage(error); }
}
