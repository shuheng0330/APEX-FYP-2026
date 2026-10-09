import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { forkJoin, finalize } from 'rxjs';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { KpiPlan, KpiItem, KpiItemErrors, KpiPeriodContext, emptyKpiItem, kpiItemErrors, kpiPlanComplete } from '../../models/kpi-plan.model';
import { KpiPlanService } from '../../services/kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';
@Component({
  standalone: true, imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzModalModule, NzDrawerModule, KpiItemEditorComponent, KpiScoringGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './company-kpi-plan.component.html'
})
export class CompanyKpiPlanComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  periods: KpiPeriodContext[] = []; plans: KpiPlan[] = []; selectedId: number | null = null;
  plan: KpiPlan | null = null; items: KpiItem[] = []; busy = false; error = ''; success = '';
  drawerVisible = false; editingIndex: number | null = null; editorItems: KpiItem[] = []; itemError = '';
  viewOnly = false; fieldErrors: KpiItemErrors = {};
  constructor(private api: KpiPlanService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() {
    this.busy = true;
    forkJoin({ periods: this.api.periods(), plans: this.api.list() }).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => { this.periods = result.periods; this.plans = result.plans; this.selectedId = this.periods[0]?.id ?? null; this.selectPeriod(); },
      error: e => this.fail(e)
    });
  }
  get selectedPeriod() { return this.periods.find(p => p.id === this.selectedId); }
  get readonly() { return this.selectedPeriod?.status === 'CLOSED' || (!!this.plan && this.plan.status !== 'DRAFT'); }
  get drawerReadonly() { return this.readonly || this.viewOnly; }
  get hasMeaningfulItems() { return this.items.some(i => !!i.name?.trim()); }
  get canPublish() { return !this.readonly && this.plan?.status === 'DRAFT' && kpiPlanComplete(this.items) && !!this.selectedPeriod?.participantsSnapshottedAt &&
    ['UPCOMING', 'OPEN'].includes(this.selectedPeriod.status); }
  get total() { return Math.round(this.items.reduce((n, i) => n + (i.weightage ?? 0), 0) * 100) / 100; }
  get publishBlocker(): { key: string; params?: { amount: number } } | null {
    if (this.readonly || !this.selectedPeriod) return null;
    if (!this.items.length) return { key: 'KPI_PLAN.ADD_BEFORE_PUBLISH' };
    if (this.items.some(item => Object.keys(kpiItemErrors(item)).length > 0)) return { key: 'KPI_PLAN.COMPLETE_BEFORE_PUBLISH' };
    if (this.total < 100) return { key: 'KPI_PLAN.WEIGHT_REMAINING', params: { amount: Math.round((100 - this.total) * 100) / 100 } };
    if (this.total > 100) return { key: 'KPI_PLAN.WEIGHT_EXCESS', params: { amount: Math.round((this.total - 100) * 100) / 100 } };
    if (!kpiPlanComplete(this.items)) return { key: 'KPI_PLAN.COMPLETE_BEFORE_PUBLISH' };
    if (this.selectedPeriod.status === 'DRAFT') return { key: 'KPI_PLAN.PUBLISH_PERIOD_FIRST' };
    if (!this.selectedPeriod.participantsSnapshottedAt) return { key: 'KPI_PLAN.NO_ROSTER' };
    if (!this.plan) return { key: 'KPI_PLAN.SAVE_BEFORE_PUBLISH' };
    return null;
  }
  confirmPublish() {
    if (!this.canPublish || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.PUBLISH'), nzContent: this.translate.instant('KPI_PLAN.PUBLISH_CONFIRM'),
      nzOnOk: () => this.publishPlan() });
  }
  changePeriod(id: number) {
    if (id === this.selectedId || this.busy || this.drawerVisible) return;
    this.selectedId = id; this.selectPeriod();
  }
  selectPeriod() {
    this.plan = this.plans.find(p => p.reviewPeriodId === this.selectedId) ?? null;
    this.items = structuredClone(this.plan?.items ?? []);
    this.error = ''; this.success = ''; this.drawerVisible = false;
  }
  openItem(index: number | null, viewOnly = false) {
    if (this.busy || !this.selectedPeriod || (index === null && this.readonly)) return;
    this.editingIndex = index; this.itemError = ''; this.fieldErrors = {}; this.viewOnly = viewOnly;
    this.editorItems = [index === null ? emptyKpiItem() : structuredClone(this.items[index])];
    this.drawerVisible = true;
  }
  applyItem() {
    if (this.drawerReadonly || this.busy) return;
    const item = this.editorItems[0];
    if (!item) return;
    this.fieldErrors = kpiItemErrors(item);
    if (item.name?.trim() && this.items.some((i, index) => index !== this.editingIndex && i.name?.trim().toLowerCase() === item.name?.trim().toLowerCase())) {
      this.fieldErrors['name'] = 'KPI_PLAN.DUPLICATE_NAME';
    }
    if (Object.keys(this.fieldErrors).length) {
      this.itemError = this.translate.instant('KPI_PLAN.FIX_ITEM'); return;
    }
    const items = this.editingIndex === null ? [...this.items, structuredClone(item)]
      : this.items.map((i, index) => index === this.editingIndex ? structuredClone(item) : i);
    this.persistDraft(items, () => { this.drawerVisible = false; });
  }
  removeItem(index: number) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.REMOVE_TITLE'),
      nzContent: this.translate.instant('KPI_PLAN.REMOVE_HELP'),
      nzOnOk: () => { if (!this.busy && !this.readonly) this.persistDraft(this.items.filter((_, i) => i !== index)); } });
  }
  private persistDraft(items: KpiItem[], onSaved?: () => void) {
    if (this.busy || this.readonly || this.selectedId === null) return;
    this.busy = true; this.error = ''; this.success = '';
    this.itemError = '';
    const request = { reviewPeriodId: this.selectedId, items };
    const operation = this.plan ? this.api.update(this.plan.id, request) : this.api.create(request);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); onSaved?.(); this.success = this.translate.instant('KPI_PLAN.SAVED'); },
      error: e => {
        const message = this.errorMessage(e);
        if (this.drawerVisible) this.itemError = message;
        else this.error = message;
      }
    });
  }
  publishPlan() {
    if (this.busy || this.drawerVisible || !this.canPublish || !this.plan) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.publish(this.plan.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); this.success = this.translate.instant('KPI_PLAN.PUBLISHED'); },
      error: e => this.fail(e)
    });
  }
  private acceptPlan(plan: KpiPlan) {
    this.plan = plan; this.plans = [...this.plans.filter(p => p.id !== plan.id), plan];
    this.items = structuredClone(plan.items);
  }
  private errorMessage(error: { error?: { message?: string } }) {
    const message = error.error?.message;
    return message?.includes('publication-time participant snapshot')
      ? this.translate.instant('KPI_PLAN.NO_ROSTER') : message || this.translate.instant('KPI_PLAN.ERROR');
  }
  private fail(error: { error?: { message?: string } }) { this.error = this.errorMessage(error); }
}
