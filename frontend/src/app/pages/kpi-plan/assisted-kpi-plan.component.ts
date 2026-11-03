import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, of, switchMap, tap } from 'rxjs';
import { KpiAssistance } from '../../models/kpi-assistance.model';
import { KpiItem, KpiItemErrors, KpiPlan, emptyKpiItem, kpiItemErrors, kpiPlanComplete } from '../../models/kpi-plan.model';
import { KpiAssistanceService } from '../../services/kpi-assistance.service';
import { AuthService } from '../../services/auth.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';

@Component({
  selector: 'app-assisted-kpi-plan', standalone: true,
  imports: [CommonModule, TranslateModule, NzButtonModule, NzDrawerModule, KpiItemEditorComponent, KpiScoringGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './assisted-kpi-plan.component.html'
})
export class AssistedKpiPlanComponent implements OnInit {
  @Input({ required: true }) assistance!: KpiAssistance;
  @Output() back = new EventEmitter<void>();
  @Output() changed = new EventEmitter<KpiAssistance>();
  private readonly destroyRef = inject(DestroyRef);
  plan: KpiPlan | null = null;
  items: KpiItem[] = [];
  ready = false; busy = false; accessChanged = false;
  error = ''; success = ''; itemError = '';
  drawerVisible = false; viewOnly = false; editingIndex: number | null = null;
  editorItems: KpiItem[] = []; fieldErrors: KpiItemErrors = {};
  constructor(private api: KpiAssistanceService, private auth: AuthService,
    private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get readonly() {
    return !this.ready || this.accessChanged || !this.auth.hasRole('CAN_REVIEW_INDIVIDUAL_KPI')
      || this.assistance.superiorId !== this.auth.userId || this.assistance.status !== 'AUTHORIZED'
      || !['UPCOMING', 'OPEN'].includes(this.assistance.reviewPeriodStatus)
      || (!!this.plan && this.plan.status !== 'DRAFT');
  }
  get total() { return this.items.reduce((sum, item) => sum + Math.round((item.weightage ?? 0) * 100), 0) / 100; }
  get canConfirm() { return !this.readonly && !!this.plan && kpiPlanComplete(this.items); }
  get blocker() {
    if (this.items.some(item => Object.keys(kpiItemErrors(item)).length)) return 'KPI_ASSISTANCE.COMPLETE_ITEMS';
    if (new Set(this.items.map(item => item.name?.trim().toLowerCase())).size !== this.items.length) return 'INDIVIDUAL_KPI.DUPLICATE_NAME';
    return this.total > 100 ? 'KPI_ASSISTANCE.REDUCE_WEIGHT' : 'KPI_ASSISTANCE.COMPLETE_WEIGHT';
  }
  load() {
    if (this.busy || this.drawerVisible) return;
    this.busy = true; this.ready = false; this.error = '';
    this.api.get(this.assistance.id).pipe(
      tap(value => { this.assistance = value; this.changed.emit(value); }),
      switchMap(value => value.planId ? this.api.plan(value.id) : of(null)),
      finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)
    ).subscribe({ next: plan => { this.plan = plan; this.items = structuredClone(plan?.items ?? []); this.ready = true; }, error: error => this.fail(error) });
  }
  openItem(index: number | null, viewOnly = false) {
    if (this.busy || !this.ready || (!viewOnly && this.readonly)) return;
    this.editingIndex = index; this.viewOnly = viewOnly; this.fieldErrors = {}; this.itemError = '';
    this.editorItems = [structuredClone(index === null ? emptyKpiItem() : this.items[index])]; this.drawerVisible = true;
  }
  applyItem() {
    if (this.readonly || this.viewOnly || this.busy) return;
    const item = this.editorItems[0]; if (!item) return;
    this.fieldErrors = kpiItemErrors(item);
    if (item.name?.trim() && this.items.some((existing, index) => index !== this.editingIndex && existing.name?.trim().toLowerCase() === item.name?.trim().toLowerCase()))
      this.fieldErrors['name'] = 'INDIVIDUAL_KPI.DUPLICATE_NAME';
    if (Object.keys(this.fieldErrors).length) { this.itemError = this.translate.instant('KPI_PLAN.FIX_ITEM'); return; }
    const items = this.editingIndex === null ? [...this.items, structuredClone(item)]
      : this.items.map((existing, index) => index === this.editingIndex ? structuredClone(item) : existing);
    this.persist(items, () => this.drawerVisible = false);
  }
  removeItem(index: number) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('INDIVIDUAL_KPI.REMOVE_TITLE'), nzContent: this.translate.instant('INDIVIDUAL_KPI.REMOVE_HELP'),
      nzOnOk: () => this.persist(this.items.filter((_, i) => i !== index)) });
  }
  private persist(items: KpiItem[], saved?: () => void) {
    if (this.readonly || this.busy) return;
    this.busy = true; this.error = ''; this.itemError = ''; this.success = '';
    const operation = this.plan ? this.api.updatePlan(this.assistance.id, items) : this.api.createPlan(this.assistance.id, items);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.accept(plan); saved?.(); this.success = this.translate.instant('INDIVIDUAL_KPI.SAVED'); }, error: error => this.fail(error)
    });
  }
  confirm() {
    if (!this.canConfirm || this.busy || this.drawerVisible) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.CONFIRM_PLAN'), nzContent: this.translate.instant('KPI_ASSISTANCE.CONFIRM_HELP'), nzOnOk: () => this.confirmPlan() });
  }
  confirmPlan() {
    if (!this.canConfirm || this.busy || this.drawerVisible) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.confirmPlan(this.assistance.id).pipe(tap(plan => this.accept(plan)), switchMap(() => this.api.get(this.assistance.id)),
      finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.assistance = value; this.changed.emit(value); this.success = this.translate.instant('KPI_ASSISTANCE.PLAN_CONFIRMED'); },
      error: error => this.fail(error)
    });
  }
  private accept(plan: KpiPlan) {
    this.plan = plan; this.items = structuredClone(plan.items);
    this.assistance = { ...this.assistance, planId: plan.id }; this.changed.emit(this.assistance);
  }
  private fail(error: { status?: number; error?: { message?: string } }) {
    if (error.status === 403) this.accessChanged = true;
    const message = error.status === 403 ? this.translate.instant('KPI_ASSISTANCE.ACCESS_CHANGED') : error.error?.message || this.translate.instant('KPI_PLAN.ERROR');
    if (this.drawerVisible) this.itemError = message; else this.error = message;
  }
}
