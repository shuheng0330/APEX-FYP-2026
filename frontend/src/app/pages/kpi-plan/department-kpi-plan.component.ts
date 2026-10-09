import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, finalize } from 'rxjs';
import { KpiItem, KpiItemErrors, KpiPlan, KpiPeriodContext, emptyKpiItem, kpiItemErrors, kpiPlanComplete } from '../../models/kpi-plan.model';
import { DepartmentKpiPlanService, KpiDepartmentOption } from '../../services/department-kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';
import { canResubmit } from '../../models/submission-revision.model';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzDrawerModule, NzModalModule, KpiItemEditorComponent, KpiScoringGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './department-kpi-plan.component.html'
})
export class DepartmentKpiPlanComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  periods: KpiPeriodContext[] = [];
  departments: KpiDepartmentOption[] = [];
  plans: KpiPlan[] = [];
  periodId: number | null = null;
  departmentId: number | null = null;
  plan: KpiPlan | null = null;
  items: KpiItem[] = [];
  busy = false;
  error = '';
  success = '';
  drawerVisible = false;
  editingIndex: number | null = null;
  editorItems: KpiItem[] = [];
  viewOnly = false;
  fieldErrors: KpiItemErrors = {};
  itemError = '';

  constructor(private api: DepartmentKpiPlanService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() {
    this.busy = true;
    forkJoin({ periods: this.api.periods(), departments: this.api.departments(), plans: this.api.list() })
      .pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => {
          this.periods = result.periods; this.departments = result.departments; this.plans = result.plans;
          this.periodId = this.periods[0]?.id ?? null;
          this.departmentId = this.departments[0]?.id ?? null;
          this.selectPlan();
        },
        error: error => this.fail(error)
      });
  }
  get selectedPeriod() { return this.periods.find(period => period.id === this.periodId); }
  get selectedDepartment() { return this.departments.find(department => department.id === this.departmentId); }
  get readonly() {
    return this.selectedPeriod?.status === 'CLOSED' || (!!this.plan && !['DRAFT', 'RETURNED'].includes(this.plan.status));
  }
  get drawerReadonly() { return this.viewOnly || this.readonly; }
  get hasMeaningfulItems() { return this.items.some(item => !!item.name?.trim()); }
  get total() { return Math.round(this.items.reduce((sum, item) => sum + (item.weightage ?? 0), 0) * 100) / 100; }
  get canSubmit() {
    return !this.readonly && !!this.plan && canResubmit(this.plan) && ['DRAFT', 'RETURNED'].includes(this.plan.status) && kpiPlanComplete(this.items);
  }
  get submitBlocker(): { key: string; params?: { amount: number } } | null {
    if (this.readonly || !this.selectedPeriod || !this.selectedDepartment) return null;
    if (!canResubmit(this.plan)) return { key: 'APPROVAL_REVISION.CHANGE_REQUIRED' };
    if (!this.items.length) return { key: 'DEPARTMENT_KPI.ADD_BEFORE_SUBMIT' };
    if (this.items.some(item => Object.keys(kpiItemErrors(item)).length)) return { key: 'DEPARTMENT_KPI.COMPLETE_BEFORE_SUBMIT' };
    if (this.total < 100) return { key: 'DEPARTMENT_KPI.WEIGHT_REMAINING', params: { amount: Math.round((100 - this.total) * 100) / 100 } };
    if (this.total > 100) return { key: 'DEPARTMENT_KPI.WEIGHT_EXCESS', params: { amount: Math.round((this.total - 100) * 100) / 100 } };
    if (!this.plan) return { key: 'DEPARTMENT_KPI.SAVE_BEFORE_SUBMIT' };
    return null;
  }
  changePeriod(id: number) { if (!this.busy && !this.drawerVisible) { this.periodId = id; this.selectPlan(); } }
  changeDepartment(id: number) { if (!this.busy && !this.drawerVisible) { this.departmentId = id; this.selectPlan(); } }
  selectPlan() {
    this.plan = this.plans.find(plan => plan.reviewPeriodId === this.periodId && plan.departmentId === this.departmentId) ?? null;
    this.items = structuredClone(this.plan?.items ?? []);
    this.error = ''; this.success = ''; this.drawerVisible = false;
  }
  openItem(index: number | null, viewOnly = false) {
    if (this.busy || !this.selectedPeriod || !this.selectedDepartment || (index === null && this.readonly)) return;
    this.editingIndex = index; this.viewOnly = viewOnly; this.fieldErrors = {}; this.itemError = '';
    this.editorItems = [index === null ? emptyKpiItem() : structuredClone(this.items[index])];
    this.drawerVisible = true;
  }
  applyItem() {
    if (this.drawerReadonly || this.busy) return;
    const item = this.editorItems[0];
    if (!item) return;
    this.fieldErrors = kpiItemErrors(item);
    if (item.name?.trim() && this.items.some((existing, index) => index !== this.editingIndex &&
      existing.name?.trim().toLowerCase() === item.name?.trim().toLowerCase())) {
      this.fieldErrors['name'] = 'DEPARTMENT_KPI.DUPLICATE_NAME';
    }
    if (Object.keys(this.fieldErrors).length) {
      this.itemError = this.translate.instant('KPI_PLAN.FIX_ITEM'); return;
    }
    const items = this.editingIndex === null ? [...this.items, structuredClone(item)]
      : this.items.map((existing, index) => index === this.editingIndex ? structuredClone(item) : existing);
    this.persistDraft(items, () => this.drawerVisible = false);
  }
  removeItem(index: number) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('DEPARTMENT_KPI.REMOVE_TITLE'),
      nzContent: this.translate.instant('DEPARTMENT_KPI.REMOVE_HELP'),
      nzOnOk: () => { if (!this.busy && !this.readonly) this.persistDraft(this.items.filter((_, itemIndex) => itemIndex !== index)); } });
  }
  private persistDraft(items: KpiItem[], onSaved?: () => void) {
    if (this.busy || this.readonly || this.periodId === null || this.departmentId === null) return;
    this.busy = true; this.error = ''; this.success = ''; this.itemError = '';
    const request = { reviewPeriodId: this.periodId, departmentId: this.departmentId, items };
    const operation = this.plan ? this.api.update(this.plan.id, request) : this.api.create(request);
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); onSaved?.(); this.success = this.translate.instant('DEPARTMENT_KPI.SAVED'); },
      error: error => {
        const message = this.errorMessage(error);
        if (this.drawerVisible) this.itemError = message;
        else this.error = message;
      }
    });
  }
  confirmSubmit() {
    if (!this.canSubmit || this.busy || this.drawerVisible) return;
    this.modal.confirm({ nzTitle: this.translate.instant('DEPARTMENT_KPI.SUBMIT'),
      nzContent: this.translate.instant('DEPARTMENT_KPI.SUBMIT_CONFIRM'), nzOnOk: () => this.submitPlan() });
  }
  submitPlan() {
    if (!this.canSubmit || this.busy || this.drawerVisible || !this.plan) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.submit(this.plan.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.acceptPlan(plan); this.success = this.translate.instant('DEPARTMENT_KPI.SUBMITTED'); },
      error: error => this.fail(error)
    });
  }
  private acceptPlan(plan: KpiPlan) {
    this.plan = plan; this.plans = [...this.plans.filter(existing => existing.id !== plan.id), plan];
    this.items = structuredClone(plan.items);
  }
  private errorMessage(error: { error?: { message?: string } }) { return error.error?.message || this.translate.instant('KPI_PLAN.ERROR'); }
  private fail(error: { error?: { message?: string } }) { this.error = this.errorMessage(error); }
}
