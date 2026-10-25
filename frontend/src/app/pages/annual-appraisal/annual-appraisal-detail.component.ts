import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzIconModule, NzIconService } from 'ng-zorro-antd/icon';
import { NzPopoverModule } from 'ng-zorro-antd/popover';
import { InfoCircleOutline } from '@ant-design/icons-angular/icons';
import { EMPTY, Observable, Subject, catchError, finalize, map, merge, of, switchMap, tap, throwError } from 'rxjs';
import { AnnualAppraisal, AnnualAppraisalAction, AnnualAppraisalActivity, AnnualAppraisalCandidate, AnnualAppraisalReadiness,
  AnnualRecommendation, AnnualPerformanceHistory, annualJustificationError, annualRecommendations } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { AnnualAppraisalPerformanceComponent } from './annual-appraisal-performance.component';
import { AnnualPerformanceTrendComponent } from './annual-performance-trend.component';
import { AnnualAppraisalNavigationState } from './annual-appraisal-navigation-state.service';
import { appraisalReturnComparison } from './annual-appraisal-return-comparison';

interface AppraisalDetail {
  period: KpiPeriodContext; row: AnnualAppraisalCandidate; value: AnnualAppraisal | AnnualAppraisalReadiness;
}
type HrDecision = 'APPROVE' | 'OVERRIDE_AND_APPROVE' | 'RETURN';
@Component({
  selector: 'app-annual-appraisal-detail', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzRadioModule,
    NzModalModule, NzDrawerModule, NzIconModule, NzPopoverModule, AnnualAppraisalPerformanceComponent, AnnualPerformanceTrendComponent],
  templateUrl: './annual-appraisal-detail.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss', './annual-appraisals.component.scss', './annual-appraisal-detail.component.scss']
})
export class AnnualAppraisalDetailComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly route = inject(ActivatedRoute);
  private readonly reload = new Subject<ParamMap>();
  readonly hrReview = this.route.snapshot.data['hrReview'] === true;
  readonly recommendations = annualRecommendations;
  readonly hrActions: readonly HrDecision[] = ['APPROVE', 'OVERRIDE_AND_APPROVE', 'RETURN'];
  readonly queuePath = this.hrReview ? '/appraisals/review' : '/team-performance/team-appraisals';
  period: KpiPeriodContext | null = null;
  periodId: number | null = null;
  loading = false;
  busy = false;
  confirming = false;
  navigating = false;
  error = '';
  success = '';
  selected: AnnualAppraisal | null = null;
  candidate: AnnualAppraisalCandidate | null = null;
  readiness: AnnualAppraisalReadiness | null = null;
  recommendation: AnnualRecommendation | null = null;
  justification = '';
  private savedForm = '';
  accessChanged = false;
  showValidation = false;
  draftWarnings = false;
  decision: HrDecision | null = null;
  infoAction: HrDecision | null = null;
  decisionError = '';
  reason = '';
  finalDecision: AnnualRecommendation | null = null;
  decisionValidation = false;
  activityVisible = false;
  showAllActivity = false;
  expandedEvent: number | null = null;
  expandedPerformanceEvent: number | null = null;
  historyVisible = false;
  historyLoaded = false;
  history: AnnualAppraisal[] = [];
  historySelection: number | null = null;
  historyLoading = false;
  historyError = '';
  private historyRequest = 0;
  performanceHistory: AnnualPerformanceHistory[] = [];
  performanceHistoryLoading = false;
  performanceHistoryError = '';
  private performanceRequest = 0;

  constructor(private api: AnnualAppraisalService, public auth: AuthService, private translate: TranslateService,
    private modal: NzModalService, private router: Router, private navigation: AnnualAppraisalNavigationState) {
    inject(NzIconService).addIcon(InfoCircleOutline);
  }
  ngOnInit() {
    document.querySelector('.main-content')?.scrollTo({ top: 0 });
    const header = document.querySelector('app-top-header .top-header');
    if (header && typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(() => this.host.nativeElement.style.setProperty('--appraisal-header-offset', `${header.getBoundingClientRect().height}px`));
      observer.observe(header); this.destroyRef.onDestroy(() => observer.disconnect());
    }
    merge(this.route.paramMap, this.reload).pipe(switchMap(params => {
      this.resetDetail();
      if (!this.authorised) { this.error = this.translate.instant('ANNUAL_APPRAISAL.NO_ACCESS'); return EMPTY; }
      this.loading = true;
      return this.resolveDetail(params).pipe(catchError(() => {
        this.error = this.translate.instant('ANNUAL_APPRAISAL.DETAIL_UNAVAILABLE'); return EMPTY;
      }), finalize(() => this.loading = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(({ period, row, value }) => {
      this.period = period; this.periodId = period.id; this.candidate = row;
      if ('id' in value) this.accept(value); else this.readiness = value;
      this.savedForm = this.formSignature();
      this.navigation.rememberPeriod(this.auth.userId, this.hrReview, period.id);
      this.loadHistory();
      this.loadPerformanceHistory();
    });
  }
  get authorised() { return this.auth.hasRole(this.hrReview ? 'CAN_REVIEW_ANNUAL_APPRAISAL' : 'CAN_SUBMIT_ANNUAL_APPRAISAL'); }
  get titleKey() { return this.hrReview ? 'ANNUAL_APPRAISAL.REVIEW_DETAIL_TITLE' : 'ANNUAL_APPRAISAL.DETAIL_TITLE'; }
  get locked() { return this.busy || this.loading || this.confirming || this.navigating; }
  get readonly() {
    return this.hrReview || this.accessChanged || !this.authorised || this.period?.status !== 'OPEN' || (this.selected
      ? !this.selected.allowedActions.includes('SAVE_DRAFT') : !this.candidate);
  }
  get showRecommendationForm() {
    return !this.hrReview && (this.selected ? ['DRAFT', 'RETURNED'].includes(this.selected.status)
      : this.period?.status === 'OPEN' && !!this.candidate);
  }
  get dirty() { return !!this.candidate && (this.formSignature() !== this.savedForm || !!this.reason || this.finalDecision !== null); }
  get performance() {
    return this.selected && ['PENDING_REVIEW', 'APPROVED'].includes(this.selected.status) ? this.selected.snapshot : this.readiness?.performance;
  }
  get frozen() { return this.selected?.status === 'PENDING_REVIEW' || this.selected?.status === 'APPROVED'; }
  get overdue() {
    return this.selected && (this.hrReview ? this.selected.status === 'PENDING_REVIEW' && this.selected.hrReviewOverdue
      : ['DRAFT', 'RETURNED'].includes(this.selected.status) && this.selected.recommendationOverdue);
  }
  get nextStepKey() {
    if (this.period?.status !== 'OPEN') return 'ANNUAL_APPRAISAL.NEXT.CLOSED';
    if (this.accessChanged) return 'ANNUAL_APPRAISAL.NEXT.REFRESH';
    if (this.selected?.status === 'DRAFT' && this.readiness?.ready) return 'ANNUAL_APPRAISAL.NEXT.DRAFT_READY';
    return 'ANNUAL_APPRAISAL.NEXT.' + (this.selected?.status ?? (this.readiness?.ready ? 'READY' : 'NOT_READY'));
  }
  get currentStatus() { return this.selected?.status ?? (this.readiness?.ready ? 'READY' : 'NOT_READY'); }
  can(action: AnnualAppraisalAction) { return !this.accessChanged && this.selected?.allowedActions.includes(action) === true; }
  get justificationError() { return annualJustificationError(this.justification, this.showValidation); }
  get reasonError() { return annualJustificationError(this.reason, this.decisionValidation); }
  get canStartSubmit() { return !this.readonly && !this.locked && this.readiness?.ready === true; }
  get showHrDecisions() { return this.hrReview && this.selected?.status === 'PENDING_REVIEW'; }
  get returnComparison() { return this.hrReview ? appraisalReturnComparison(this.selected) : null; }
  canReview(action: HrDecision) { return this.authorised && this.showHrDecisions && this.can(action); }
  get hasHrActions() { return this.hrActions.some(action => this.canReview(action)); }
  get displayedBlockers() {
    if (this.hrReview) return this.selected?.status === 'PENDING_REVIEW' ? this.selected.reviewBlockers : [];
    return this.selected?.status === 'DRAFT' || this.selected?.status === 'RETURNED' ? this.selected.submissionBlockers : this.readiness?.blockers ?? [];
  }
  private resetDetail() {
    this.candidate = null; this.selected = null; this.period = null; this.periodId = null; this.readiness = null;
    this.recommendation = null; this.justification = ''; this.savedForm = this.formSignature();
    this.error = ''; this.success = ''; this.accessChanged = false;
    this.history = []; this.historyLoaded = false; this.historyVisible = false; this.historyRequest++;
    this.historyLoading = false; this.historyError = ''; this.historySelection = null;
    this.performanceHistory = []; this.performanceRequest++; this.performanceHistoryLoading = false; this.performanceHistoryError = '';
    this.activityVisible = false; this.showAllActivity = false; this.expandedEvent = null; this.expandedPerformanceEvent = null;
    this.clearDecision(); this.showValidation = false; this.draftWarnings = false;
  }
  private resolveDetail(params: ParamMap): Observable<AppraisalDetail> {
    const positiveId = (key: string) => {
      const raw = params.get(key); const value = Number(raw);
      return raw && /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(value) ? value : null;
    };
    const appraisalId = positiveId('appraisalId'), periodId = positiveId('reviewPeriodId'), participantId = positiveId('participantId');
    if (this.hrReview ? appraisalId === null : periodId === null || participantId === null) return throwError(() => new Error('Invalid detail identifier'));
    return this.api.periods(this.hrReview).pipe(switchMap(periods => {
      if (this.hrReview) return this.api.get(appraisalId!).pipe(map(value => {
        const period = periods.find(p => p.id === value.reviewPeriodId);
        if (!period || value.id !== appraisalId) throw new Error('Unavailable appraisal');
        return { period, row: { ...value, appraisalId: value.id }, value };
      }));
      const period = periods.find(p => p.id === periodId);
      if (!period) return throwError(() => new Error('Unavailable period'));
      return this.api.team(period.id).pipe(switchMap(rows => {
        const row = rows.find(r => r.participantId === participantId);
        if (!row) return throwError(() => new Error('Unavailable team participant'));
        const request: Observable<AnnualAppraisal | AnnualAppraisalReadiness> = row.appraisalId !== null
          ? this.api.get(row.appraisalId) : this.api.readiness(row.participantId);
        return request.pipe(map(value => {
          const result = 'id' in value ? value : value.performance;
          if (result.participantId !== participantId || result.reviewPeriodId !== period.id
            || result.employeeId !== row.employeeId
            || ('id' in value && value.id !== row.appraisalId)) throw new Error('Mismatched appraisal context');
          return { period, row, value };
        }));
      }));
    }));
  }
  private formSignature() { return JSON.stringify([this.recommendation, this.justification]); }
  private accept(value: AnnualAppraisal) {
    this.selected = value; this.readiness = value.readiness;
    this.recommendation = value.recommendation; this.justification = value.justification ?? '';
    this.savedForm = this.formSignature(); this.showValidation = false;
    if (this.period) this.period = { ...this.period, status: value.reviewPeriodStatus };
  }
  back() {
    if (!this.locked) void this.router.navigateByUrl(this.queuePath);
  }
  canLeave(): boolean | Promise<boolean> {
    return this.locked ? false : this.confirmDiscard();
  }
  private leaveConfirmation: Promise<boolean> | null = null;
  private confirmDiscard(): boolean | Promise<boolean> {
    if (!this.dirty) return true;
    if (this.leaveConfirmation) return this.leaveConfirmation;
    const answer = new Promise<boolean>(resolve => this.modal.confirm({
      nzTitle: this.translate.instant('MY_ASSESSMENTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.DISCARD_HELP'),
      nzOnOk: () => resolve(!this.busy), nzOnCancel: () => resolve(false)
    }));
    this.leaveConfirmation = answer;
    void answer.then(() => this.leaveConfirmation = null);
    return answer;
  }
  private persist(): Observable<AnnualAppraisal> {
    const request = { participantId: this.candidate!.participantId, recommendation: this.recommendation,
      justification: this.justification, ...(this.selected ? { expectedLockVersion: this.selected.lockVersion } : {}) };
    return (this.selected ? this.api.update(this.selected.id, request) : this.api.create(request)).pipe(tap(value => this.accept(value)));
  }
  saveDraft() {
    if (this.readonly || this.locked || annualJustificationError(this.justification)) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.draftWarnings = !this.recommendation || !this.justification.trim(); this.success = this.translate.instant('ANNUAL_APPRAISAL.SAVED'); },
      error: error => this.fail(error)
    });
  }
  requestSubmit() {
    this.showValidation = true;
    if (!this.canStartSubmit || !this.recommendation || this.justificationError) return;
    this.busy = true; this.error = ''; this.success = '';
    // Save first, then use authoritative blockers, lateness and the fresh concurrency token.
    const saved = !this.selected || this.formSignature() !== this.savedForm ? this.persist() : of(this.selected);
    saved.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => {
        if (!value.allowedActions.includes('SUBMIT')) {
          this.error = value.submissionBlockers.join('; ') || this.translate.instant('ANNUAL_APPRAISAL.NOT_READY_HELP'); return;
        }
        this.confirming = true;
        this.modal.confirm({ nzTitle: this.translate.instant('ANNUAL_APPRAISAL.SUBMIT'),
          nzContent: this.translate.instant(value.recommendationOverdue ? 'ANNUAL_APPRAISAL.SUBMIT_LATE_HELP' : 'ANNUAL_APPRAISAL.SUBMIT_HELP',
            { deadline: value.recommendationDeadline }),
          nzOkText: this.translate.instant(value.recommendationOverdue ? 'ANNUAL_APPRAISAL.SUBMIT_LATE' : 'ANNUAL_APPRAISAL.SUBMIT'),
          nzOnOk: () => { this.confirming = false; this.submit(); }, nzOnCancel: () => { this.confirming = false; } });
      }, error: error => this.fail(error)
    });
  }
  submit() {
    if (this.locked || this.hrReview || !this.can('SUBMIT') || !this.selected) return;
    this.write(this.api.submit(this.selected.id, { expectedLockVersion: this.selected.lockVersion }), 'SUBMITTED');
  }
  beginDecision(action: HrDecision) {
    if (this.locked || this.decision || !this.canReview(action)) return;
    this.clearDecision(); this.decision = action; this.success = '';
  }
  infoVisibility(action: HrDecision, visible: boolean) {
    if (visible) this.infoAction = action;
    else if (this.infoAction === action) this.infoAction = null;
  }
  cancelDecision() { if (!this.busy) this.clearDecision(); }
  private clearDecision() {
    this.decision = null; this.reason = ''; this.finalDecision = null; this.decisionValidation = false; this.decisionError = ''; this.infoAction = null;
  }
  confirmDecision(action: HrDecision) {
    if (this.locked || this.decision !== action || !this.canReview(action) || !this.selected) return;
    this.decisionValidation = true;
    if (action !== 'APPROVE' && this.reasonError) return;
    if (action === 'OVERRIDE_AND_APPROVE' && (!this.finalDecision || this.finalDecision === this.selected.recommendation)) return;
    const request = { expectedLockVersion: this.selected.lockVersion, reason: this.reason, finalDecision: this.finalDecision };
    const result = action === 'APPROVE' ? this.api.approve(this.selected.id, { expectedLockVersion: request.expectedLockVersion })
      : action === 'RETURN' ? this.api.returnForRevision(this.selected.id, { expectedLockVersion: request.expectedLockVersion, reason: request.reason })
      : this.api.overrideApprove(this.selected.id, request);
    this.write(result, action === 'RETURN' ? 'RETURNED' : 'APPROVED');
  }
  private write(request: Observable<AnnualAppraisal>, message: string) {
    this.busy = true; this.error = ''; this.decisionError = ''; this.success = '';
    request.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.accept(value); this.clearDecision(); this.historyLoaded = false;
        this.success = this.translate.instant('ANNUAL_APPRAISAL.SUCCESS.' + message); this.loadHistory(true); this.loadPerformanceHistory(); }, error: error => this.fail(error)
    });
  }
  async refreshDetail() {
    if (this.locked) return;
    this.navigating = true;
    try {
      if (!(await this.confirmDiscard())) return;
      this.navigating = false;
      this.reload.next(this.route.snapshot.paramMap);
    } finally { this.navigating = false; }
  }
  toggleHistory() {
    if (this.locked || !this.candidate) return;
    this.historyVisible = !this.historyVisible;
    if (this.historyVisible) this.loadHistory();
  }
  loadHistory(force = false) {
    if (!this.candidate || (!force && (this.historyLoading || this.historyLoaded))) return;
    const request = ++this.historyRequest;
    if (force) this.historySelection = null;
    this.historyLoading = true; this.historyError = '';
    this.api.history(this.candidate.participantId).pipe(finalize(() => { if (request === this.historyRequest) this.historyLoading = false; }), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: rows => {
        if (request !== this.historyRequest) return;
        this.history = rows.filter(row => row.status === 'APPROVED' && row.employeeId === this.candidate?.employeeId); this.historyLoaded = true;
      }, error: () => {
        if (request !== this.historyRequest) return;
        this.history = []; this.historyLoaded = false; this.historyError = this.translate.instant('ANNUAL_APPRAISAL.HISTORY_ERROR');
      }
    });
  }
  loadPerformanceHistory() {
    if (!this.candidate) return;
    const request = ++this.performanceRequest;
    this.performanceHistoryLoading = true; this.performanceHistoryError = '';
    this.api.performanceHistory(this.candidate.participantId).pipe(
      finalize(() => { if (request === this.performanceRequest) this.performanceHistoryLoading = false; }),
      takeUntilDestroyed(this.destroyRef)).subscribe({
        next: rows => { if (request === this.performanceRequest) this.performanceHistory = rows; },
        error: () => {
          if (request !== this.performanceRequest) return;
          this.performanceHistory = []; this.performanceHistoryError = this.translate.instant('ANNUAL_TREND.UNAVAILABLE');
        }
      });
  }
  get historicalAppraisal() { return this.history.find(row => row.id === this.historySelection) ?? null; }
  openHistory(id: number) {
    if (!this.locked && this.history.some(row => row.id === id)) this.historySelection = id;
  }
  get events() { return [...(this.selected?.activity ?? [])].sort((a, b) => Date.parse(a.occurredAt) - Date.parse(b.occurredAt) || a.id - b.id); }
  toggleActivity() {
    this.activityVisible = !this.activityVisible;
    if (!this.activityVisible) { this.expandedEvent = null; this.expandedPerformanceEvent = null; }
  }
  get hasDraftActivity() { return this.selected?.activity.some(event => event.action === 'DRAFT_CREATED' || event.action === 'DRAFT_SAVED') === true; }
  get visibleEvents() { return this.events.filter(event => this.showAllActivity || (event.action !== 'DRAFT_CREATED' && event.action !== 'DRAFT_SAVED')); }
  toggleSubmission(id: number) {
    if (this.locked) return;
    this.expandedEvent = this.expandedEvent === id ? null : id; this.expandedPerformanceEvent = null;
  }
  toggleEventPerformance(id: number) {
    if (!this.locked) this.expandedPerformanceEvent = this.expandedPerformanceEvent === id ? null : id;
  }
  showRecordedSubmission(event: AnnualAppraisalActivity): boolean {
    if ((event.action !== 'SUBMITTED' && event.action !== 'RESUBMITTED')
      || (!event.snapshot && !event.recommendation && event.justification == null)) return false;
    if (!event.snapshot) return true;
    const submissions = this.selected?.activity.filter(e => e.action === 'SUBMITTED' || e.action === 'RESUBMITTED') ?? [];
    if (!submissions.some(e => e.id === event.id)) return false;
    const latestId = Math.max(...submissions.map(e => e.id));
    if (event.id !== latestId || !this.frozen || !this.selected?.submittedAt || !event.recommendation || event.justification == null) return true;
    const snapshot = event.snapshot;
    if (snapshot.employeeId !== this.selected.employeeId || snapshot.participantId !== this.selected.participantId
      || snapshot.reviewPeriodId !== this.selected.reviewPeriodId) return true;
    return event.recommendation !== this.selected.recommendation || event.justification !== this.selected.justification
      || !this.sameRecordedContent(snapshot, this.performance);
  }
  // JSON snapshots may have different object key order; array order is retained conservatively.
  private sameRecordedContent(left: unknown, right: unknown): boolean {
    if (left === right) return left !== undefined;
    if (left === null || right === null || typeof left !== 'object' || typeof right !== 'object') return false;
    if (Array.isArray(left) || Array.isArray(right)) return Array.isArray(left) && Array.isArray(right)
      && left.length === right.length && left.every((value, index) => this.sameRecordedContent(value, right[index]));
    const a = Object.entries(left).sort(([a], [b]) => a.localeCompare(b));
    const b = Object.entries(right).sort(([a], [b]) => a.localeCompare(b));
    return a.length === b.length && a.every(([key, value], index) => key === b[index][0] && this.sameRecordedContent(value, b[index][1]));
  }
  actor(event: AnnualAppraisalActivity) {
    if (event.actorId === this.auth.userId) return this.translate.instant('ANNUAL_APPRAISAL.YOU');
    if (event.actorId === this.selected?.employeeId && this.selected.employeeName?.trim()) return this.selected.employeeName;
    if (['RETURNED', 'APPROVED', 'OVERRIDDEN'].includes(event.action)) return this.translate.instant('ANNUAL_APPRAISAL.ACTIVITY_HR_REVIEWER');
    if (event.actorId === this.selected?.superiorId || ['DRAFT_CREATED', 'DRAFT_SAVED', 'SUBMITTED', 'RESUBMITTED'].includes(event.action))
      return this.translate.instant('ANNUAL_APPRAISAL.ACTIVITY_SUPERIOR');
    return this.translate.instant('ANNUAL_APPRAISAL.ACTOR_UNAVAILABLE');
  }
  @HostListener('window:beforeunload', ['$event']) warnUnsaved(event: BeforeUnloadEvent) {
    if (this.dirty || this.busy) { event.preventDefault(); event.returnValue = ''; }
  }
  private fail(error: { status?: number; error?: { message?: string } }) {
    if (this.candidate && [403, 409].includes(error.status ?? 0)) this.accessChanged = true;
    const message = [403, 409].includes(error.status ?? 0)
      ? this.translate.instant(this.decision ? 'ANNUAL_APPRAISAL.DECISION.ACCESS_CHANGED' : 'ANNUAL_APPRAISAL.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('ANNUAL_APPRAISAL.ERROR');
    if (this.hrReview && this.decision) this.decisionError = message; else this.error = message;
  }
}
