import { CommonModule } from '@angular/common';
import { Component, DestroyRef, Input, OnChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { Subscription, finalize, timeout, timer } from 'rxjs';
import { EmployeeInsight, EmployeeInsightLimits, EmployeeInsightResult, EmployeeInsightState } from '../../models/appraisal-insight.model';
import { AppraisalInsightService } from '../../services/appraisal-insight.service';
import { AuthService } from '../../services/auth.service';
import { AppraisalInsightSummaryComponent } from './appraisal-insight-summary.component';

interface Ticket { operation: 'GENERATE' | 'REGENERATE'; requestKey: string | null; generationId: string | null; }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const active = (value: EmployeeInsight | null) => value?.status === 'QUEUED' || value?.status === 'RUNNING';

@Component({
  selector: 'app-employee-appraisal-insight', standalone: true,
  imports: [CommonModule, TranslateModule, NzButtonModule, AppraisalInsightSummaryComponent],
  templateUrl: './employee-appraisal-insight.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './appraisal-insight-preview.component.scss', './employee-appraisal-insight.component.scss']
})
export class EmployeeAppraisalInsightComponent implements OnChanges {
  @Input({ required: true }) participantId!: number;
  @Input() appraisalId: number | null = null;
  @Input() submissionActivityId: number | null = null;
  @Input() recordVersion: number | null = null;
  @Input() employeeName = '';
  @Input() reviewPeriodName = '';
  @Input() readOnly = false;
  @Input() submittedOnly = false;
  @Input() hrReview = false;
  @Input() disabled = false;
  private readonly api = inject(AppraisalInsightService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private owner: string | null = null;
  private context = '';
  private epoch = 0;
  private request?: Subscription;
  private poll?: Subscription;
  private countdown?: Subscription;
  private pollStarted = 0;
  private clockOffset = 0;
  private refreshedVersion: number | null = null;
  visible = false;
  trusted = false;
  loading = false;
  requesting = false;
  eligible = false;
  unavailableReason: string | null = null;
  message = '';
  paused = false;
  attempt: EmployeeInsight | null = null;
  displayed: EmployeeInsight | null = null;
  limits: EmployeeInsightLimits | null = null;
  ticket: Ticket | null = null;
  remainingSeconds = 0;
  retryAt: string | null = null;
  get authorised() { return !!this.owner && this.owner === this.auth.userId &&
    this.auth.hasRole(this.hrReview ? 'CAN_REVIEW_ANNUAL_APPRAISAL' : 'CAN_SUBMIT_ANNUAL_APPRAISAL'); }
  get submittedView() { return this.hrReview || this.submittedOnly; }
  get pending() { return !this.readOnly && !this.submittedView && (active(this.attempt) || !!this.ticket); }
  get canGenerate() { return this.authorised && !this.readOnly && !this.submittedView && this.eligible && !this.disabled && !this.loading && !this.requesting &&
    !this.pending && this.remainingSeconds === 0 && this.limits !== null && this.limits.remainingAttempts > 0; }
  get buttonKey() { return this.attempt?.status === 'FAILED' ? 'TRY_AGAIN' : this.displayed && !this.displayed.outdated ? 'REGENERATE' : 'GENERATE'; }
  get storageKey() { return `ai-appraisal-employee:${this.owner}:${this.participantId}`; }
  constructor() { this.destroyRef.onDestroy(() => this.stop()); }
  ngOnChanges() {
    const context = `${this.auth.userId}:${this.participantId}:${this.hrReview}:${this.readOnly}:${this.submittedView}:${this.submittedView ? this.appraisalId : ''}:${this.submittedView ? this.submissionActivityId : ''}`;
    if (context !== this.context) {
      this.stop(); this.epoch++; this.context = context; this.owner = this.auth.userId;
      this.visible = false; this.trusted = false; this.displayed = null; this.attempt = null; this.limits = null; this.ticket = null;
      this.message = ''; this.paused = false; this.eligible = false; this.remainingSeconds = 0; this.retryAt = null;
      this.pollStarted = 0;
      this.restore(); this.load();
    } else if (this.recordVersion !== this.refreshedVersion) this.load();
    this.refreshedVersion = this.recordVersion;
  }
  private stop() { this.request?.unsubscribe(); this.poll?.unsubscribe(); this.countdown?.unsubscribe(); }
  load() {
    if (!this.authorised || !Number.isSafeInteger(this.participantId) || this.participantId <= 0 || this.loading || this.requesting) return;
    const epoch = this.epoch; this.loading = true; this.message = ''; this.poll?.unsubscribe();
    if (this.submittedView) {
      if (!this.appraisalId) { this.loading = false; return; }
      this.request = this.api.submitted(this.appraisalId, this.submissionActivityId).pipe(timeout(30000),
        finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => {
          if (!this.current(epoch)) return;
          if (result.appraisalId !== this.appraisalId || (this.submissionActivityId !== null && result.submissionActivityId !== this.submissionActivityId) ||
              (result.insight && (!this.valid(result.insight) || result.insight.status !== 'COMPLETED'))) { this.invalid(); return; }
          this.displayed = result.insight; this.visible = !!result.insight; this.trusted = !!result.insight; this.message = '';
        }, error: error => this.loadError(error.status, epoch)
      });
      return;
    }
    this.request = this.api.employeeState(this.participantId).pipe(timeout(30000), finalize(() => this.loading = false),
      takeUntilDestroyed(this.destroyRef)).subscribe({
      next: state => {
        if (!this.current(epoch)) return;
        if (!this.validState(state)) { this.invalid(); return; }
        this.visible = state.unavailableReason !== 'DISABLED'; this.eligible = state.eligible; this.unavailableReason = state.unavailableReason;
        this.trusted = this.visible;
        this.attempt = state.latestAttempt;
        if (this.attempt?.status === 'FAILED') this.message = this.failureKey(this.attempt.errorCode);
        this.displayed = state.currentInsight ?? state.history.filter(v => v.status === 'COMPLETED')
          .sort((a, b) => (b.version ?? 0) - (a.version ?? 0))[0] ?? null;
        this.setLimits(state.limits);
        if (!this.visible) { this.ticket = null; this.remember(); return; }
        if (!this.readOnly && (this.ticket?.generationId || (!this.ticket && active(this.attempt)))) {
          this.ticket ??= { operation: 'GENERATE', requestKey: null, generationId: this.attempt!.id };
          this.remember(); if (!this.pollStarted) this.pollStarted = Date.now(); this.paused = false; this.schedule();
        } else if (this.ticket) { this.paused = true; this.message = 'RECOVER'; }
      }, error: error => this.loadError(error.status, epoch)
    });
  }
  generate() {
    if (!this.canGenerate) return;
    this.ticket = { operation: this.displayed && !this.displayed.outdated ? 'REGENERATE' : 'GENERATE', requestKey: crypto.randomUUID(), generationId: null };
    this.remember(); this.check();
  }
  check() {
    if (!this.authorised || this.readOnly || this.submittedView || this.requesting || !this.ticket || this.disabled) return;
    this.poll?.unsubscribe(); this.pollStarted = Date.now(); this.paused = false; this.message = ''; this.fetch();
  }
  private fetch() {
    if (!this.authorised || this.readOnly || this.submittedView || !this.ticket) return;
    const ticket = this.ticket, epoch = this.epoch, creating = !ticket.generationId;
    if (creating && !ticket.requestKey) { this.invalid(); return; }
    this.requesting = true;
    const request = ticket.generationId ? this.api.employeeGeneration(this.participantId, ticket.generationId)
      : this.api.generateEmployee(this.participantId, ticket.requestKey!, ticket.operation === 'REGENERATE');
    this.request = request.pipe(timeout(30000), finalize(() => this.requesting = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (!this.current(epoch)) return;
        if (!this.validResult(result) || (ticket.generationId !== null && result.generation.id !== ticket.generationId)) { this.invalid(); return; }
        this.attempt = result.generation; ticket.generationId = result.generation.id; this.setLimits(result.limits); this.remember();
        if (active(this.attempt)) { this.message = result.disposition === 'ALREADY_IN_PROGRESS' ? 'JOINED' : ''; this.schedule(); }
        else {
          if (this.attempt.status === 'COMPLETED' && (!this.displayed || (this.attempt.version ?? 0) >= (this.displayed.version ?? 0))) this.displayed = this.attempt;
          this.message = this.attempt.status === 'FAILED' ? this.failureKey(this.attempt.errorCode) : result.disposition === 'REUSED' ? 'REUSED' : 'READY';
          this.ticket = null; this.remember();
          if (this.attempt.outdated) this.poll = timer(0).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
        }
      },
      error: error => {
        if (!this.current(epoch)) return;
        if ([401, 403, 404].includes(error.status)) { this.conceal(); return; }
        this.paused = true;
        if (error.status >= 400 && error.status < 500 && error.status !== 408) {
          this.ticket = null; this.remember(); this.message = error.status === 409 ? 'CONFLICT' : error.status === 429 ? 'LIMITED' : 'FAILED';
          if (error.status === 429) {
            const limits = error.error?.limits;
            if (this.validLimits(limits)) this.setLimits(limits, error.error?.retryAt);
            else this.setRetry(error.error?.retryAt, Number(error.headers?.get('Retry-After')) || 60);
          }
        } else this.message = creating ? 'RECOVER' : 'STATUS_ERROR';
      }
    });
  }
  private schedule() {
    this.poll?.unsubscribe();
    const remaining = 120000 - (Date.now() - this.pollStarted);
    this.poll = timer(Math.min(3000, Math.max(0, remaining))).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (Date.now() - this.pollStarted >= 120000) { this.paused = true; this.message = 'WAITING'; }
      else this.fetch();
    });
  }
  private setLimits(limits: EmployeeInsightLimits, extraRetry?: string) {
    this.limits = limits; this.clockOffset = Date.parse(limits.serverTime) - Date.now();
    if (this.readOnly) { this.countdown?.unsubscribe(); this.remainingSeconds = 0; this.retryAt = null; return; }
    const times = [limits.cooldownUntil, limits.providerRetryAt, this.attempt?.retryAt, extraRetry,
      limits.remainingAttempts === 0 ? limits.resetAt : null].map(v => v ? Date.parse(v) : 0).filter(Number.isFinite);
    this.startCountdown(Math.max(0, ...times));
  }
  private setRetry(retry: unknown, seconds: number) {
    const timestamp = typeof retry === 'string' ? Date.parse(retry) : NaN;
    this.startCountdown(Number.isFinite(timestamp) ? timestamp : Date.now() + this.clockOffset + Math.max(1, seconds) * 1000);
  }
  private startCountdown(deadline: number) {
    this.countdown?.unsubscribe(); this.retryAt = deadline > Date.now() + this.clockOffset ? new Date(deadline).toISOString() : null;
    this.remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now() - this.clockOffset) / 1000));
    if (!this.remainingSeconds) return;
    this.countdown = timer(1000, 1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now() - this.clockOffset) / 1000));
      if (!this.remainingSeconds) { this.countdown?.unsubscribe(); this.load(); }
    });
  }
  private valid(value: EmployeeInsight) { return !!value && UUID.test(value.id) && value.fictional === true &&
    ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'].includes(value.status) &&
    (value.status !== 'COMPLETED' || (!!value.summary && !!value.evidence?.scores && Number.isInteger(value.version) && value.version! > 0)); }
  private validLimits(value: EmployeeInsightLimits | undefined): value is EmployeeInsightLimits { return !!value &&
    Number.isFinite(Date.parse(value.serverTime)) && Number.isFinite(Date.parse(value.resetAt)) &&
    Number.isInteger(value.dailyAllowance) && value.dailyAllowance > 0 && Number.isInteger(value.remainingAttempts) &&
    value.remainingAttempts >= 0 && value.remainingAttempts <= value.dailyAllowance; }
  private validState(value: EmployeeInsightState) { return value.participantId === this.participantId && this.validLimits(value.limits) &&
    (!value.currentInsight || (this.valid(value.currentInsight) && value.currentInsight.status === 'COMPLETED' && !value.currentInsight.outdated)) && (!value.latestAttempt || this.valid(value.latestAttempt)) &&
    Array.isArray(value.history) && value.history.every(v => this.valid(v)); }
  private validResult(value: EmployeeInsightResult) { return this.valid(value.generation) && this.validLimits(value.limits); }
  private current(epoch: number) { return epoch === this.epoch && this.authorised; }
  private failureKey(code: string | null) { return code === 'RATE_LIMITED' ? 'LIMITED' : code === 'BUSY' ? 'BUSY' : code === 'EVIDENCE_CHANGED' ? 'CHANGED' : 'FAILED'; }
  private loadError(status: number, epoch: number) {
    if (!this.current(epoch)) return;
    if ([400, 401, 403, 404].includes(status)) this.conceal();
    else { this.visible = true; this.eligible = false; this.message = 'LOAD_ERROR'; }
  }
  private conceal() { this.visible = false; this.trusted = false; this.displayed = null; this.attempt = null; this.ticket = null; this.eligible = false; this.remember(); this.stop(); }
  private invalid() { this.displayed = null; this.attempt = null; this.ticket = null; this.eligible = false; this.visible = true; this.message = 'INVALID'; this.remember(); this.stop(); }
  private restore() {
    if (this.readOnly || this.submittedView || !this.authorised) return;
    try {
      const value: unknown = JSON.parse(sessionStorage.getItem(this.storageKey) ?? 'null');
      if (!value || typeof value !== 'object') return;
      const v = value as Partial<Ticket>;
      if ((v.operation === 'GENERATE' || v.operation === 'REGENERATE') &&
        (v.requestKey === null || typeof v.requestKey === 'string' && UUID.test(v.requestKey)) &&
        (v.generationId === null || typeof v.generationId === 'string' && UUID.test(v.generationId)) && (v.requestKey || v.generationId)) this.ticket = v as Ticket;
    } catch { /* Generation remains usable when session storage is unavailable. */ }
  }
  private remember() {
    try { if (this.ticket) sessionStorage.setItem(this.storageKey, JSON.stringify(this.ticket)); else sessionStorage.removeItem(this.storageKey); }
    catch { /* Store identifiers only, never employee data or generated text. */ }
  }
}
