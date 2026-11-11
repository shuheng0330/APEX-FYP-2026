import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { Observable, Subscription, finalize, timeout, timer } from 'rxjs';
import { AppraisalInsight, InsightScenario } from '../../models/appraisal-insight.model';
import { AppraisalInsightService } from '../../services/appraisal-insight.service';
import { AuthService } from '../../services/auth.service';
import { AppraisalInsightSummaryComponent } from './appraisal-insight-summary.component';

interface PreviewTicket { scenario: string; requestKey: string; generationId: string | null; }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Component({
  selector: 'app-appraisal-insight-preview', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, AppraisalInsightSummaryComponent],
  templateUrl: './appraisal-insight-preview.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './appraisal-insight-preview.component.scss']
})
export class AppraisalInsightPreviewComponent implements OnInit {
  private readonly api = inject(AppraisalInsightService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly owner = this.auth.userId;
  private readonly storageKey = `ai-appraisal-preview:${this.owner}`;
  private poll?: Subscription;
  private pollStarted = 0;
  scenarios: InsightScenario[] = [];
  scenario: string | null = null;
  loading = false;
  requesting = false;
  unavailable = false;
  message = '';
  paused = false;
  supporting = false;
  current: AppraisalInsight | null = null;
  successful: AppraisalInsight | null = null;
  ticket: PreviewTicket | null = null;
  get authorised() { return !!this.owner && this.owner === this.auth.userId &&
    (this.auth.hasRole('CAN_SUBMIT_ANNUAL_APPRAISAL') || this.auth.hasRole('CAN_REVIEW_ANNUAL_APPRAISAL')); }
  get pending() { return !!this.ticket && (!this.current || this.current.status === 'QUEUED' || this.current.status === 'RUNNING'); }
  get displayed() { return this.current?.status === 'COMPLETED' ? this.current : this.successful; }
  get evidence() { return this.displayed?.evidence ?? this.current?.evidence; }
  title(id: string | undefined) { return this.scenarios.find(s => s.id === id)?.title ?? ''; }
  ngOnInit() { this.destroyRef.onDestroy(() => this.poll?.unsubscribe()); this.load(); }
  load() {
    if (!this.authorised || this.loading) return;
    this.loading = true; this.message = ''; this.unavailable = false;
    this.api.scenarios().pipe(timeout(30000), finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: scenarios => {
        if (!this.authorised) return;
        this.scenarios = scenarios;
        if (!scenarios.length) { this.unavailable = true; return; }
        try {
          const saved: unknown = JSON.parse(sessionStorage.getItem(this.storageKey) ?? 'null');
          if (this.validTicket(saved)) { this.ticket = saved; this.scenario = saved.scenario; }
        } catch { /* Storage may be unavailable; the current session still works. */ }
        if (this.ticket?.generationId) this.check();
        else if (this.ticket) { this.paused = true; this.message = 'RECOVER'; }
      },
      error: error => { this.unavailable = error.status === 400 || error.status === 403; this.message = this.unavailable ? 'UNAVAILABLE' : 'LOAD_ERROR'; }
    });
  }
  private validTicket(value: unknown): value is PreviewTicket {
    if (!value || typeof value !== 'object') return false;
    const v = value as Partial<PreviewTicket>;
    return typeof v.scenario === 'string' && this.scenarios.some(s => s.id === v.scenario) &&
      typeof v.requestKey === 'string' && UUID.test(v.requestKey) &&
      (v.generationId === null || typeof v.generationId === 'string' && UUID.test(v.generationId));
  }
  private remember() {
    try { if (this.ticket) sessionStorage.setItem(this.storageKey, JSON.stringify(this.ticket));
      else sessionStorage.removeItem(this.storageKey); } catch { /* No evidence is persisted. */ }
  }
  generate() {
    if (!this.authorised || this.unavailable || this.loading || this.requesting || this.pending || !this.scenarios.some(s => s.id === this.scenario)) return;
    this.current = null; this.supporting = false;
    this.ticket = { scenario: this.scenario!, requestKey: crypto.randomUUID(), generationId: null };
    this.remember(); this.check();
  }
  check() {
    if (!this.authorised || this.requesting || !this.ticket) return;
    this.poll?.unsubscribe(); this.pollStarted = Date.now(); this.paused = false; this.message = '';
    this.fetch();
  }
  private fetch() {
    if (!this.authorised || !this.ticket) return;
    this.requesting = true;
    const ticket = this.ticket;
    const creating = !ticket.generationId;
    const request: Observable<AppraisalInsight> = ticket.generationId ? this.api.get(ticket.generationId)
      : this.api.generate(ticket.scenario, ticket.requestKey);
    request.pipe(timeout(30000), finalize(() => this.requesting = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (!this.authorised) { this.current = null; this.successful = null; return; }
        if (result.synthetic !== true || result.scenario !== ticket.scenario || !UUID.test(result.id) ||
            (ticket.generationId !== null && result.id !== ticket.generationId) ||
            !['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'].includes(result.status) ||
            (result.status === 'COMPLETED' && !result.summary)) {
          this.message = 'INVALID'; this.unavailable = true; this.current = null; this.successful = null; return;
        }
        this.current = result; ticket.generationId = result.id; this.remember();
        if (result.status === 'COMPLETED') { this.successful = result; this.supporting = false; }
        else if (result.status === 'FAILED') this.message = result.errorCode === 'BUSY' ? 'BUSY'
          : result.errorCode === 'INTERRUPTED' ? 'INTERRUPTED' : 'FAILED';
        else this.schedule();
      },
      error: error => {
        this.paused = true; this.message = creating ? 'RECOVER' : 'STATUS_ERROR';
        if (error.status === 401 || error.status === 403 || (!creating && error.status === 404)) {
          this.unavailable = true; this.message = 'UNAVAILABLE'; this.current = null; this.successful = null;
        }
        if (creating && error.status >= 400 && error.status < 500 && error.status !== 408) {
          this.ticket = null; this.remember(); this.message = error.status === 409 ? 'BUSY' : this.unavailable ? 'UNAVAILABLE' : 'FAILED';
        }
      }
    });
  }
  private schedule() {
    const remaining = 120000 - (Date.now() - this.pollStarted);
    this.poll = timer(Math.min(3000, Math.max(0, remaining))).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (Date.now() - this.pollStarted >= 120000) { this.paused = true; this.message = 'WAITING'; }
      else this.fetch();
    });
  }
}
