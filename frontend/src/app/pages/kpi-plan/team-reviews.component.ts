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
import { KpiPeriodContext, KpiPlan, KpiPlanStatus } from '../../models/kpi-plan.model';
import { TeamReviewPeriodService } from '../../services/team-review-period.service';
import { KpiReturnFeedbackComponent } from './kpi-return-feedback.component';
import { IndividualKpiPlanService } from '../../services/individual-kpi-plan.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';
import { KpiAssistanceComponent } from './kpi-assistance.component';
import { AuthService } from '../../services/auth.service';
import { KpiAssessmentReviewsComponent } from '../kpi-assessment/kpi-assessment-reviews.component';
import { AttitudeAssessmentReviewsComponent } from '../kpi-assessment/attitude-assessment-reviews.component';
import { KpiRevisionsComponent } from './kpi-revisions.component';
import { ActivatedRoute, Router } from '@angular/router';
import { TeamReviewTab, teamReviewTabs } from './review-task-directory';
import { ReviewTabNavigation } from './review-tab-navigation';
import { ReviewTaskGuideComponent } from './review-task-guide.component';

type ReviewStatus = Extract<KpiPlanStatus, 'PENDING_APPROVAL' | 'APPROVED' | 'RETURNED'>;
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzSelectModule, NzModalModule, KpiItemEditorComponent, KpiAssistanceComponent, KpiScoringGuideComponent, KpiAssessmentReviewsComponent, AttitudeAssessmentReviewsComponent, KpiReturnFeedbackComponent, KpiRevisionsComponent, ReviewTaskGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './team-reviews.component.html'
})
export class TeamReviewsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  tab: TeamReviewTab | null = null;
  readonly navigation = new ReviewTabNavigation(inject(Router), inject(ActivatedRoute), this.destroyRef,
    () => teamReviewTabs(permission => this.auth.hasRole(permission)), () => this.tab,
    tab => { this.tab = tab; if (tab === 'reviews' && !this.plansLoaded) this.load(); }, () => this.canChangeTab());
  private plansLoaded = false;
  @ViewChild(KpiRevisionsComponent) revisionWorkspace?: KpiRevisionsComponent;
  @ViewChild(KpiAssistanceComponent) assistanceWorkspace?: KpiAssistanceComponent;
  @ViewChild(KpiAssessmentReviewsComponent) assessmentWorkspace?: KpiAssessmentReviewsComponent;
  @ViewChild(AttitudeAssessmentReviewsComponent) attitudeWorkspace?: AttitudeAssessmentReviewsComponent;
  readonly statuses: ReviewStatus[] = ['PENDING_APPROVAL', 'APPROVED', 'RETURNED'];
  filter: ReviewStatus = 'PENDING_APPROVAL';
  periodId: number | null = null;
  periods: KpiPeriodContext[] = [];
  periodLoading = false;
  periodError = '';
  periodsReady = false;
  periodChanging = false;
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

  constructor(private api: IndividualKpiPlanService, private translate: TranslateService, private modal: NzModalService, private auth: AuthService,
    private periodApi: TeamReviewPeriodService) { this.tab = this.navigation.initial(); }
  get canReviewPlans() { return this.auth.hasRole('CAN_REVIEW_INDIVIDUAL_KPI'); }
  get canReviewAssessments() { return this.auth.hasRole('CAN_REVIEW_KPI_ASSESSMENT'); }
  get canReviewAttitude() { return this.auth.hasRole('CAN_REVIEW_ATTITUDE_EVALUATION'); }
  ngOnInit() {
    this.tab = this.navigation.initial();
    this.navigation.connect();
    this.loadPeriods();
    if (this.tab === 'reviews') this.load();
  }
  selectTab(tab: TeamReviewTab) { this.navigation.select(tab); }
  private canChangeTab(): boolean | Promise<boolean> {
    if (this.periodChanging || this.busy || this.loading || this.periodLoading || this.drawerVisible) return false;
    if (this.assistanceWorkspace?.locked || this.assessmentWorkspace?.locked || this.attitudeWorkspace?.locked || this.revisionWorkspace?.locked) {
      this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION'), nzContent: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION_HELP'), nzCancelText: null }); return false;
    }
    return this.revisionWorkspace?.canLeave() ?? this.attitudeWorkspace?.canLeave() ?? this.assessmentWorkspace?.canLeave() ?? true;
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.navigation.pending || this.periodChanging || this.busy || this.loading || this.periodLoading || this.drawerVisible || this.assistanceWorkspace?.locked) return false;
    return this.revisionWorkspace?.canLeave() ?? this.attitudeWorkspace?.canLeave() ?? this.assessmentWorkspace?.canLeave() ?? true;
  }
  get selectedPeriod() { return this.periods.find(period => period.id === this.periodId); }
  loadPeriods() {
    if (this.periodLoading || this.busy || !(this.canReviewPlans || this.canReviewAssessments || this.canReviewAttitude)) return;
    this.periodLoading = true; this.periodError = '';
    this.periodApi.periods().pipe(finalize(() => this.periodLoading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.periods = result.periods;
        const remembered = this.auth.userId ? Number(sessionStorage.getItem('team-review-period:' + this.auth.userId)) : null;
        this.periodId = this.periods.find(period => period.id === this.periodId)?.id
          ?? this.periods.find(period => period.id === remembered)?.id ?? result.defaultPeriodId;
        this.periodsReady = true;
      }, error: () => { this.periodError = this.translate.instant('REVIEW_FEEDBACK.PERIOD_ERROR'); }
    });
  }
  async changePeriod(id: number | null) {
    if (this.navigation.pending || this.periodChanging || id === this.periodId || this.periodLoading || this.busy || this.loading || !this.periods.some(period => period.id === id)) return;
    if (this.assistanceWorkspace?.locked || this.drawerVisible) {
      this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION'), nzContent: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION_HELP'), nzCancelText: null }); return;
    }
    this.periodChanging = true;
    try {
      if (this.revisionWorkspace) {
        if (this.revisionWorkspace.busy) return;
        if (this.revisionWorkspace.workspace) await this.revisionWorkspace.workspace.close();
        if (this.revisionWorkspace.workspace?.visible) return;
      }
      const assessment = this.attitudeWorkspace ?? this.assessmentWorkspace;
      if (assessment && !(await assessment.canLeave())) return;
      if (assessment) { assessment.drawerVisible = false; assessment.selected = null; assessment.items = []; }
      if (this.assistanceWorkspace) this.assistanceWorkspace.workspace = null;
      this.periodId = id;
      if (this.auth.userId) sessionStorage.setItem('team-review-period:' + this.auth.userId, String(id));
    } finally { this.periodChanging = false; }
  }
  private get filteredPlans() {
    const search = this.search.trim().toLowerCase();
    return this.plans.filter(plan => this.periodId !== null && plan.reviewPeriodId === this.periodId
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
      next: plans => { this.plansLoaded = true; this.plans = plans.filter(plan => plan.level === 'INDIVIDUAL' && this.statuses.includes(plan.status as ReviewStatus)); },
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
