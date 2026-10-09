import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { KpiPlan, KpiPlanStatus } from '../../models/kpi-plan.model';
import { DepartmentKpiPlanService } from '../../services/department-kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';

type ReviewStatus = Extract<KpiPlanStatus, 'PENDING_APPROVAL' | 'APPROVED' | 'RETURNED'>;

@Component({
  selector: 'app-department-kpi-review',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzModalModule, KpiItemEditorComponent, KpiScoringGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './department-kpi-review.component.html'
})
export class DepartmentKpiReviewComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  readonly statuses: ReviewStatus[] = ['PENDING_APPROVAL', 'APPROVED', 'RETURNED'];
  filter: ReviewStatus = 'PENDING_APPROVAL';
  plans: KpiPlan[] = [];
  selected: KpiPlan | null = null;
  drawerVisible = false;
  loading = false;
  busy = false;
  returnMode = false;
  returnReason = '';
  reasonError = false;
  error = '';
  success = '';

  constructor(private api: DepartmentKpiPlanService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get visiblePlans() { return this.plans.filter(plan => plan.status === this.filter); }
  count(status: ReviewStatus) { return this.plans.filter(plan => plan.status === status).length; }
  get canDecide() { return this.selected?.status === 'PENDING_APPROVAL' && this.selected.reviewPeriodStatus !== 'CLOSED'; }
  get canApprove() { return this.canDecide && ['UPCOMING', 'OPEN'].includes(this.selected!.reviewPeriodStatus); }

  load() {
    this.loading = true; this.error = '';
    this.api.list().pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plans => this.plans = plans.filter(plan => ['PENDING_APPROVAL', 'APPROVED', 'RETURNED'].includes(plan.status)),
      error: error => this.fail(error)
    });
  }
  open(id: number) {
    if (this.busy || this.loading) return;
    this.loading = true; this.error = ''; this.success = ''; this.returnMode = false; this.returnReason = ''; this.reasonError = false;
    this.api.get(id).pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.selected = plan; this.drawerVisible = true; this.replace(plan); },
      error: error => this.fail(error)
    });
  }
  close() { if (!this.busy) { this.drawerVisible = false; this.returnMode = false; } }
  confirmApprove() {
    if (!this.canApprove || this.busy || !this.selected) return;
    this.modal.confirm({ nzTitle: this.translate.instant('DEPARTMENT_REVIEW.APPROVE'),
      nzContent: this.translate.instant('DEPARTMENT_REVIEW.APPROVE_CONFIRM'), nzOnOk: () => this.approve() });
  }
  approve() {
    if (!this.canApprove || this.busy || !this.selected) return;
    this.busy = true; this.error = '';
    this.api.approve(this.selected.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.selected = plan; this.replace(plan); this.success = this.translate.instant('DEPARTMENT_REVIEW.APPROVED'); },
      error: error => this.fail(error)
    });
  }
  beginReturn() { if (this.canDecide && !this.busy) { this.returnMode = true; this.returnReason = ''; this.reasonError = false; } }
  cancelReturn() { this.returnMode = false; this.returnReason = ''; this.reasonError = false; }
  returnForRevision() {
    if (!this.canDecide || this.busy || !this.selected || !this.returnMode) return;
    const reason = this.returnReason.trim();
    if (!reason || reason.length > 10000) { this.reasonError = true; return; }
    this.busy = true; this.error = '';
    this.api.returnForRevision(this.selected.id, reason).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => {
        this.selected = plan; this.replace(plan); this.returnMode = false; this.returnReason = '';
        this.success = this.translate.instant('DEPARTMENT_REVIEW.RETURNED');
      },
      error: error => this.fail(error)
    });
  }
  private replace(plan: KpiPlan) { this.plans = [...this.plans.filter(existing => existing.id !== plan.id), plan]; }
  private fail(error: { error?: { message?: string } }) {
    const message = error.error?.message;
    this.error = message?.includes('publication-time participant snapshot')
      ? this.translate.instant('DEPARTMENT_REVIEW.NO_ROSTER') : message || this.translate.instant('KPI_PLAN.ERROR');
  }
}
