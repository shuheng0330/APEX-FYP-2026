import { Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { KpiPlan, KpiPlanStatus } from '../../models/kpi-plan.model';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';
import { KpiAssistanceComponent } from './kpi-assistance.component';
import { AuthService } from '../../services/auth.service';
import { KpiAssessmentReviewsComponent } from '../kpi-assessment/kpi-assessment-reviews.component';

type ReviewStatus = Extract<KpiPlanStatus, 'PENDING_APPROVAL' | 'APPROVED' | 'RETURNED'>;
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzSelectModule, NzModalModule, KpiItemEditorComponent, KpiAssistanceComponent, KpiScoringGuideComponent, KpiAssessmentReviewsComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './team-reviews.component.html'
})
export class TeamReviewsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  tab: 'reviews' | 'assistance' | 'assessments' = 'reviews';
  @ViewChild(KpiAssistanceComponent) assistanceWorkspace?: KpiAssistanceComponent;
  @ViewChild(KpiAssessmentReviewsComponent) assessmentWorkspace?: KpiAssessmentReviewsComponent;
  readonly statuses: ReviewStatus[] = ['PENDING_APPROVAL', 'APPROVED', 'RETURNED'];
  filter: ReviewStatus = 'PENDING_APPROVAL';
  periodId: number | null = null;
  search = '';
  plans: KpiPlan[] = [];
  selected: KpiPlan | null = null;
  drawerVisible = false;
  loading = false;
  busy = false;
  returnMode = false;
  returnReason = '';
  reasonError = false;
  accessChanged = false;
  error = '';
  success = '';

  constructor(private api: IndividualKpiPlanService, private translate: TranslateService, private modal: NzModalService, private auth: AuthService) {}
  get canReviewPlans() { return this.auth.hasRole('CAN_REVIEW_INDIVIDUAL_KPI'); }
  get canReviewAssessments() { return this.auth.hasRole('CAN_REVIEW_KPI_ASSESSMENT'); }
  ngOnInit() { if (this.canReviewPlans) this.load(); else if (this.canReviewAssessments) this.tab = 'assessments'; }
  selectTab(tab: 'reviews' | 'assistance' | 'assessments') {
    if (tab === 'assessments' ? !this.canReviewAssessments : !this.canReviewPlans) return;
    if (this.busy || this.drawerVisible) return;
    if (this.assistanceWorkspace?.locked || this.assessmentWorkspace?.locked) {
      this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION'), nzContent: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION_HELP'), nzCancelText: null }); return;
    }
    this.tab = tab;
  }
  canLeave(): boolean | Promise<boolean> { return this.assessmentWorkspace?.canLeave() ?? !this.busy; }
  get periods() {
    return [...new Map(this.plans.map(plan => [plan.reviewPeriodId, { id: plan.reviewPeriodId, name: plan.reviewPeriodName }])).values()];
  }
  private get filteredPlans() {
    const search = this.search.trim().toLowerCase();
    return this.plans.filter(plan => (this.periodId === null || plan.reviewPeriodId === this.periodId)
      && (!search || `${plan.employeeName ?? ''} ${plan.departmentName ?? ''}`.toLowerCase().includes(search)));
  }
  get visiblePlans() { return this.filteredPlans.filter(plan => plan.status === this.filter); }
  count(status: ReviewStatus) { return this.filteredPlans.filter(plan => plan.status === status).length; }
  get canDecide() { return !this.accessChanged && this.selected?.status === 'PENDING_APPROVAL' && this.selected.reviewPeriodStatus !== 'CLOSED'; }
  get canApprove() { return this.canDecide && ['UPCOMING', 'OPEN'].includes(this.selected!.reviewPeriodStatus); }
  load() {
    if (!this.canReviewPlans || this.loading || this.busy) return;
    this.loading = true; this.error = '';
    this.api.reviews().pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plans => this.plans = plans.filter(plan => plan.level === 'INDIVIDUAL' && this.statuses.includes(plan.status as ReviewStatus)),
      error: error => this.fail(error)
    });
  }
  open(id: number) {
    if (this.busy || this.loading) return;
    this.loading = true; this.error = ''; this.success = ''; this.returnMode = false; this.returnReason = ''; this.reasonError = false;
    this.accessChanged = false;
    this.api.get(id).pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.selected = plan; this.drawerVisible = true; this.replace(plan); }, error: error => this.fail(error)
    });
  }
  close() { if (!this.busy) { this.drawerVisible = false; this.returnMode = false; } }
  confirmApprove() {
    if (!this.canApprove || this.busy || !this.selected) return;
    this.modal.confirm({ nzTitle: this.translate.instant('TEAM_REVIEWS.APPROVE'),
      nzContent: this.translate.instant('TEAM_REVIEWS.APPROVE_CONFIRM'), nzOnOk: () => this.approve() });
  }
  approve() {
    if (!this.canApprove || this.busy || !this.selected) return;
    this.busy = true; this.error = ''; this.success = '';
    this.api.approve(this.selected.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.selected = plan; this.replace(plan); this.success = this.translate.instant('TEAM_REVIEWS.APPROVED'); },
      error: error => this.fail(error)
    });
  }
  beginReturn() { if (this.canDecide && !this.busy) { this.returnMode = true; this.returnReason = ''; this.reasonError = false; this.success = ''; } }
  cancelReturn() { this.returnMode = false; this.returnReason = ''; this.reasonError = false; }
  returnForRevision() {
    if (!this.canDecide || this.busy || !this.selected || !this.returnMode) return;
    const reason = this.returnReason.trim();
    if (!reason || reason.length > 10000) { this.reasonError = true; return; }
    this.busy = true; this.error = ''; this.success = '';
    this.api.returnForRevision(this.selected.id, reason).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: plan => { this.selected = plan; this.replace(plan); this.returnMode = false; this.returnReason = '';
        this.success = this.translate.instant('TEAM_REVIEWS.RETURNED'); }, error: error => this.fail(error)
    });
  }
  private replace(plan: KpiPlan) { this.plans = this.plans.map(existing => existing.id === plan.id ? plan : existing); }
  private fail(error: { status?: number; error?: { message?: string } }) {
    const message = error.error?.message;
    if (error.status === 403) { this.accessChanged = true; this.returnMode = false; }
    this.error = error.status === 403 ? this.translate.instant('TEAM_REVIEWS.ACCESS_CHANGED')
      : message?.includes('publication-time participant snapshot') ? this.translate.instant('INDIVIDUAL_KPI.PERIOD_UNAVAILABLE')
      : message || this.translate.instant('KPI_PLAN.ERROR');
  }
}
