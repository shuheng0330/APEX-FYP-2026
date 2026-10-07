import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { forkJoin, finalize, switchMap, tap } from 'rxjs';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { KpiPlan, KpiItem, KpiPeriodContext, emptyKpiItem, kpiPlanComplete } from '../../models/kpi-plan.model';
import { KpiPlanService } from '../../services/kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
@Component({
  standalone: true, imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzModalModule, NzDrawerModule, KpiItemEditorComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './company-kpi-plan.component.html'
})
export class CompanyKpiPlanComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  periods: KpiPeriodContext[] = []; plans: KpiPlan[] = []; selectedId: number | null = null;
  plan: KpiPlan | null = null; items: KpiItem[] = []; busy = false; error = ''; success = '';
  drawerVisible = false; editingIndex: number | null = null; editorItems: KpiItem[] = []; itemError = '';
  private savedItems = '[]';
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
  get canPublish() { return !this.readonly && kpiPlanComplete(this.items) && !!this.selectedPeriod?.participantsSnapshottedAt &&
    ['UPCOMING', 'OPEN'].includes(this.selectedPeriod.status); }
  get total() { return Math.round(this.items.reduce((n, i) => n + (i.weightage ?? 0), 0) * 100) / 100; }
  get dirty() { return JSON.stringify(this.items) !== this.savedItems; }
  confirmPublish() {
    if (!this.canPublish || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.PUBLISH'), nzContent: this.translate.instant('KPI_PLAN.PUBLISH_CONFIRM'),
      nzOnOk: () => this.save(true) });
  }
  changePeriod(id: number) {
    if (id === this.selectedId || this.busy || this.drawerVisible) return;
    const change = () => { this.selectedId = id; this.selectPeriod(); };
    if (this.dirty) this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_PLAN.DISCARD_HELP'), nzOnOk: change });
    else change();
  }
  selectPeriod() {
    this.plan = this.plans.find(p => p.reviewPeriodId === this.selectedId) ?? null;
    this.items = structuredClone(this.plan?.items ?? []); this.savedItems = JSON.stringify(this.items);
    this.error = ''; this.success = ''; this.drawerVisible = false;
  }
  openItem(index: number | null) {
    if (this.busy || !this.selectedPeriod || (index === null && this.readonly)) return;
    this.editingIndex = index; this.itemError = '';
    this.editorItems = [index === null ? emptyKpiItem() : structuredClone(this.items[index])];
    this.drawerVisible = true;
  }
  applyItem() {
    if (this.readonly || this.busy) return;
    const item = this.editorItems[0];
    if (!item) return;
    if (item.weightage !== null && (!Number.isFinite(item.weightage) || item.weightage < 0 || item.weightage > 100 ||
        Math.abs(item.weightage * 100 - Math.round(item.weightage * 100)) > 1e-7)) {
      this.itemError = this.translate.instant('KPI_PLAN.INVALID_WEIGHT'); return;
    }
    if (item.name?.trim() && this.items.some((i, index) => index !== this.editingIndex && i.name?.trim().toLowerCase() === item.name?.trim().toLowerCase())) {
      this.itemError = this.translate.instant('KPI_PLAN.DUPLICATE_NAME'); return;
    }
    if (this.editingIndex === null) this.items = [...this.items, structuredClone(item)];
    else this.items = this.items.map((i, index) => index === this.editingIndex ? structuredClone(item) : i);
    this.drawerVisible = false; this.success = '';
  }
  removeItem(index: number) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_PLAN.REMOVE_TITLE'),
      nzContent: this.translate.instant('KPI_PLAN.REMOVE_HELP'), nzOnOk: () => { this.items.splice(index, 1); this.success = ''; } });
  }
  save(publish = false) {
    if (this.busy || this.readonly || this.selectedId === null || this.drawerVisible || (publish && !this.canPublish)) return;
    this.busy = true; this.error = ''; this.success = '';
    const request = { reviewPeriodId: this.selectedId, items: this.items };
    const operation = this.plan ? this.api.update(this.plan.id, request) : this.api.create(request);
    operation.pipe(tap(plan => { this.acceptPlan(plan); }),
      switchMap(plan => publish ? this.api.publish(plan.id) : [plan]), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); this.success = this.translate.instant(publish ? 'KPI_PLAN.PUBLISHED' : 'KPI_PLAN.SAVED'); },
      error: e => this.fail(e)
    });
  }
  private acceptPlan(plan: KpiPlan) {
    this.plan = plan; this.plans = [...this.plans.filter(p => p.id !== plan.id), plan];
    this.items = structuredClone(plan.items); this.savedItems = JSON.stringify(this.items);
  }
  private fail(error: { error?: { message?: string } }) { this.error = error.error?.message || this.translate.instant('KPI_PLAN.ERROR'); }
}
