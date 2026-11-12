import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, TemplateRef, ViewChild } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { Subscription, finalize } from 'rxjs';
import { ANNUAL_REVIEW_PERMISSION, AnnualReviewPeriod, ReviewPeriodClosure, ReviewPeriodClosureBlocker, ReviewPeriodAttitudeConfigurationOption } from '../../models/annual-review-period.model';
import { TablePageSizeSelectorComponent } from '../../components/table-page-size-selector/table-page-size-selector.component';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-review-period-closure', standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule, NzButtonModule, NzPaginationModule, TablePageSizeSelectorComponent],
  templateUrl: './review-period-closure.component.html',
  styleUrls: ['./review-period.scss', './review-period-closure.component.scss']
})
export class ReviewPeriodClosureComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) period!: AnnualReviewPeriod;
  @Input() attitudeConfigurations: ReviewPeriodAttitudeConfigurationOption[] | null = null;
  @Output() closed = new EventEmitter<ReviewPeriodClosure>();
  @Output() viewConfiguration = new EventEmitter<void>();
  @ViewChild('confirmation', { static: true }) confirmation!: TemplateRef<unknown>;
  readiness: ReviewPeriodClosure | null = null;
  loading = false;
  closing = false;
  confirming = false;
  detailsVisible = false;
  employeePage = 1;
  employeePageSize = 10;
  readonly employeePageSizes = [10, 20, 50];
  error = '';
  private denied = false;
  private request?: Subscription;
  private dialog?: NzModalRef;
  private destroyed = false;

  constructor(private api: AnnualReviewPeriodService, public auth: AuthService,
    private modal: NzModalService, private translate: TranslateService) {}

  get authorised() { return !this.denied && this.auth.hasRole(ANNUAL_REVIEW_PERMISSION); }
  get isClosed() { return this.period.status === 'CLOSED' || this.readiness?.status === 'CLOSED'; }
  get visible() { return this.authorised && (this.period.status === 'OPEN' || this.isClosed); }
  get locked() { return this.loading || this.closing || this.confirming; }
  get canClose() { return this.authorised && !this.locked && !this.isClosed && this.readiness?.status === 'OPEN' && this.readiness.canClose; }
  get closedAt() { return this.readiness?.closedAt ?? this.period.closedAt; }
  get closedByName() { return this.readiness?.closedByName ?? this.period.closedByName; }
  // Configuration issues keep their recorded employee context, but are not assessment tasks.
  private isPeriodRequirement(blocker: ReviewPeriodClosureBlocker) { return blocker.participantId == null || blocker.code === 'CONFIGURATION'; }
  get periodRequirements() { return this.readiness?.blockers.filter(blocker => this.isPeriodRequirement(blocker)) ?? []; }
  get groupedPeriodRequirements() {
    const groups = new Map<string, { blocker: ReviewPeriodClosureBlocker; members: ReviewPeriodClosureBlocker[] }>();
    this.periodRequirements.forEach((blocker, index) => {
      const key = blocker.code === 'CONFIGURATION' ? `${blocker.code}:${blocker.message ?? ''}` : String(index);
      const group = groups.get(key);
      if (group) group.members.push(blocker); else groups.set(key, { blocker, members: [blocker] });
    });
    return [...groups.values()];
  }
  get assignedAttitudeConfiguration() { return this.attitudeConfigurations?.find(c => c.id === this.period.attitudeConfigurationId); }
  get missingAttitudeRoles() { return this.assignedAttitudeConfiguration
    ? this.period.roleConfigurations.filter(r => !this.assignedAttitudeConfiguration!.coveredRoleIds.includes(r.roleId)) : []; }
  get attitudeSetupParams() { return { reviewPeriodId: this.period.id, configurationId: this.period.attitudeConfigurationId ?? null }; }
  get employeeRequirements() { return this.readiness?.blockers.filter(blocker => !this.isPeriodRequirement(blocker)) ?? []; }
  get visibleEmployeeRequirements() { return this.employeeRequirements.slice((this.employeePage - 1) * this.employeePageSize, this.employeePage * this.employeePageSize); }
  get hasConfigurationRequirement() { return this.periodRequirements.some(blocker => blocker.code === 'CONFIGURATION'); }
  changeEmployeePage(page: number) {
    if (!Number.isInteger(page)) return;
    this.employeePage = Math.max(1, Math.min(page, Math.ceil(this.employeeRequirements.length / this.employeePageSize) || 1));
  }
  changeEmployeePageSize(size: number) {
    if (!this.employeePageSizes.includes(size)) return;
    this.employeePageSize = size; this.employeePage = 1;
  }
  requirementTitle(blocker: ReviewPeriodClosureBlocker): string {
    const key = `REVIEW_PERIOD.CLOSURE.REASONS.${blocker.code}`;
    const title = this.translate.instant(key);
    return title === key ? this.translate.instant('REVIEW_PERIOD.CLOSURE.REQUIREMENT') : title;
  }
  configurationDetail(blocker: ReviewPeriodClosureBlocker): string | null {
    const detail = blocker.message?.trim();
    return detail && detail.replace(/[.!]$/, '').toLowerCase() !== this.requirementTitle(blocker).toLowerCase() ? detail : null;
  }

  ngOnChanges(changes?: SimpleChanges) {
    if (changes && !changes['period']) return;
    this.request?.unsubscribe(); this.dialog?.destroy(); this.dialog = undefined;
    this.readiness = null; this.loading = false; this.closing = false; this.confirming = false;
    this.error = ''; this.detailsVisible = false; this.denied = false; this.employeePage = 1; this.employeePageSize = 10;
    if (this.visible && !this.isClosed) this.refresh();
  }
  ngOnDestroy() { this.destroyed = true; this.request?.unsubscribe(); this.dialog?.destroy(); }

  refresh() {
    if (!this.visible || this.locked || this.isClosed || !this.period.id) return;
    this.loading = true; this.error = ''; this.readiness = null;
    this.request = this.api.closureReadiness(this.period.id).pipe(finalize(() => this.loading = false)).subscribe({
      next: value => this.accept(value),
      error: error => this.fail(error, 'LOAD_ERROR')
    });
  }
  confirmClose() {
    if (!this.canClose) return;
    const id = this.period.id!;
    this.confirming = true;
    this.dialog = this.modal.confirm({
      nzTitle: this.translate.instant('REVIEW_PERIOD.CLOSURE.CONFIRM_TITLE'),
      nzContent: this.confirmation,
      nzOkText: this.translate.instant('REVIEW_PERIOD.CLOSURE.CLOSE'),
      nzCancelText: this.translate.instant('REVIEW_PERIOD.CANCEL'),
      nzOnCancel: () => { this.confirming = false; },
      nzOnOk: () => {
        this.confirming = false;
        if (this.destroyed || this.period.id !== id || !this.canClose) return;
        this.closing = true; this.error = '';
        this.request = this.api.close(id).pipe(finalize(() => this.closing = false)).subscribe({
          next: value => this.accept(value),
          error: error => {
            if (error.status === 409 && this.isReadiness(error.error) && error.error.reviewPeriodId === id) {
              this.accept(error.error);
              if (!this.isClosed) { this.error = 'CHANGED'; this.detailsVisible = true; }
            } else this.fail(error, 'CLOSE_ERROR');
          }
        });
      }
    });
  }
  private accept(value: ReviewPeriodClosure) {
    if (value.reviewPeriodId !== this.period.id) { this.readiness = null; this.error = 'LOAD_ERROR'; return; }
    this.readiness = value;
    this.employeePage = 1;
    if (value.status === 'CLOSED') this.closed.emit(value);
  }
  private fail(error: { status?: number }, fallback: string) {
    this.readiness = null;
    this.denied = error.status === 403 || error.status === 401;
    this.error = this.denied ? 'NO_ACCESS' : fallback;
  }
  // A blocked Close returns the readiness DTO directly, unlike ordinary API errors.
  private isReadiness(value: unknown): value is ReviewPeriodClosure {
    if (!value || typeof value !== 'object') return false;
    const data = value as Partial<ReviewPeriodClosure>;
    const count = (item: unknown) => !!item && typeof item === 'object' && 'required' in item && 'completed' in item
      && typeof item.required === 'number' && typeof item.completed === 'number';
    return typeof data.reviewPeriodId === 'number' && ['DRAFT', 'UPCOMING', 'OPEN', 'CLOSED'].includes(data.status ?? '')
      && typeof data.canClose === 'boolean' && typeof data.checkedAt === 'string'
      && typeof data.totalEmployees === 'number' && typeof data.completedEmployees === 'number'
      && count(data.kpiReviews) && count(data.attitudeEvaluations) && Array.isArray(data.blockers)
      && data.blockers.every(item => item && typeof item.code === 'string' && (item.message == null || typeof item.message === 'string'));
  }
}
