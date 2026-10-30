import { CommonModule } from '@angular/common';
import { Component, DestroyRef, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { KpiPlan } from '../../models/kpi-plan.model';
import { KpiRevision, KpiRevisionPreparationMode, unfinishedRevision, validRevisionReason } from '../../models/kpi-revision.model';
import { AuthService } from '../../services/auth.service';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { KpiRevisionWorkspaceComponent } from './kpi-revision-workspace.component';
import { KpiVersionHistoryComponent } from './kpi-version-history.component';

@Component({
  selector: 'app-kpi-revision-panel', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzInputModule, NzSelectModule, NzModalModule,
    KpiVersionHistoryComponent, KpiRevisionWorkspaceComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss', './kpi-revision.scss'],
  template: `
    <section class="revision-entry" [class.compact]="compact">
      @if (!compact) { <div><p class="help">{{ 'KPI_REVISIONS.CURRENT_VERSION' | translate:{ number: plan.currentVersionNumber || 1 } }}</p>
        @if (working) { <p class="help">{{ 'KPI_REVISIONS.VERSION' | translate:{ number: working.versionNumber } }}:
          <span class="status">{{ 'KPI_REVISIONS.STATUS.' + working.status | translate }}</span></p> }</div> }
      <div class="actions">
        <app-kpi-version-history [planId]="plan.id" [disabled]="locked || disabled" />
        @if (canOperate && (working || canStart)) { <button nz-button [disabled]="locked || disabled" (click)="manage()">
          {{ (working ? 'KPI_REVISIONS.CONTINUE' : startLabel) | translate }}
        </button> }
      </div>
    </section>
    @if (error) { <p class="error-box" role="alert">{{ error }}</p> }
    <nz-modal [nzVisible]="requestVisible" [nzTitle]="requestLabel | translate"
      [nzFooter]="requestFooter" [nzMaskClosable]="false" [nzClosable]="!busy" [nzKeyboard]="!busy" (nzOnCancel)="closeRequest()">
      <ng-container *nzModalContent>
        <p>{{ 'KPI_REVISIONS.CURRENT_UNCHANGED' | translate }}</p>
        <p class="help">{{ flowLabel | translate }}</p>
        @if (plan.level === 'INDIVIDUAL') {
          <p><strong>{{ plan.employeeName }}</strong></p>
          <div class="field revision-reason"><label for="revision-preparation">{{ 'KPI_REVISIONS.PREPARATION' | translate }} <span class="required-mark">*</span></label>
            <nz-select nzId="revision-preparation" [(ngModel)]="preparationMode" [nzDisabled]="busy">
              <nz-option nzValue="SUPERIOR" [nzLabel]="'KPI_REVISIONS.SUPERIOR_PREPARES' | translate" />
              <nz-option nzValue="HR_MD" [nzLabel]="'KPI_REVISIONS.HR_PREPARES' | translate" />
            </nz-select><p class="help">{{ 'KPI_REVISIONS.PREPARATION_HELP' | translate }}</p>
          </div>
        }
        <div class="field revision-reason"><label for="revision-request-reason">{{ 'KPI_REVISIONS.REASON' | translate }} <span class="required-mark">*</span></label>
          <textarea nz-input id="revision-request-reason" [(ngModel)]="reason" [disabled]="busy" maxlength="10000" [attr.aria-invalid]="reasonError"
            [placeholder]="'KPI_REVISIONS.REASON_PLACEHOLDER' | translate"></textarea>
          @if (reasonError) { <span class="field-error" role="alert">{{ 'KPI_REVISIONS.REASON_REQUIRED' | translate }}</span> }
        </div>
        @if (error) { <p class="error-box" role="alert">{{ error }}</p> }
      </ng-container>
    </nz-modal>
    <ng-template #requestFooter><button nz-button [disabled]="busy" (click)="closeRequest()">{{ 'REVIEW_PERIOD.CANCEL' | translate }}</button>
      <button nz-button nzType="primary" [disabled]="busy" [nzLoading]="busy" (click)="start()">
        {{ requestLabel | translate }}</button></ng-template>
    <app-kpi-revision-workspace (changed)="updated($event)" />
  `
})
export class KpiRevisionPanelComponent implements OnChanges {
  @Input({ required: true }) plan!: KpiPlan;
  @Input() disabled = false;
  @Input() historyOnly = false;
  @Input() compact = false;
  @Input() workingRevision: KpiRevision | null | undefined;
  @Output() changed = new EventEmitter<KpiRevision>();
  @ViewChild(KpiRevisionWorkspaceComponent) workspace?: KpiRevisionWorkspaceComponent;
  private readonly destroyRef = inject(DestroyRef);
  busy = false; requestVisible = false; reason = ''; reasonError = false; error = '';
  preparationMode: KpiRevisionPreparationMode = 'SUPERIOR';
  working: KpiRevision | null = null;
  constructor(private api: KpiRevisionService, private auth: AuthService, private modal: NzModalService, private translate: TranslateService) {}
  ngOnChanges(changes: SimpleChanges) {
    if (!changes['plan'] && !changes['workingRevision']) return;
    this.working = null; this.error = '';
    if (this.workingRevision !== undefined) { this.working = this.workingRevision; return; }
    if (this.canOperate) {
      this.busy = true;
      this.api.history(this.plan.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: versions => this.working = versions.find(unfinishedRevision) ?? null,
        error: e => this.error = e.error?.message || this.translate.instant('KPI_PLAN.ERROR')
      });
    }
  }
  updated(revision: KpiRevision) { this.working = unfinishedRevision(revision) ? revision : null; this.changed.emit(revision); }
  get startLabel() { return this.plan.level === 'COMPANY' ? 'KPI_REVISIONS.MANAGE_COMPANY' : this.plan.level === 'DEPARTMENT' ? 'KPI_REVISIONS.MANAGE_DEPARTMENT' : 'KPI_REVISIONS.REQUEST_INDIVIDUAL'; }
  get requestLabel() { return this.plan.level === 'COMPANY' ? 'KPI_REVISIONS.START_COMPANY' : this.plan.level === 'DEPARTMENT' ? 'KPI_REVISIONS.REQUEST_DEPARTMENT' : 'KPI_REVISIONS.REQUEST_INDIVIDUAL'; }
  get flowLabel() { return 'KPI_REVISIONS.' + this.plan.level + '_FLOW'; }
  get canOperate() {
    if (this.historyOnly) return false;
    if (this.plan.level === 'INDIVIDUAL') return ['CAN_MANAGE_KPI_AMENDMENTS', 'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS', 'CAN_REVIEW_INDIVIDUAL_KPI'].some(p => this.auth.hasRole(p));
    return this.auth.hasRole('CAN_MANAGE_KPI_AMENDMENTS') || (this.plan.level === 'COMPANY'
      ? this.auth.hasRole('CAN_REVISE_COMPANY_KPI') : ['CAN_MANAGE_DEPARTMENT_KPI', 'CAN_APPROVE_DEPARTMENT_KPI', 'CAN_PREPARE_DEPARTMENT_KPI_REVISION'].some(p => this.auth.hasRole(p)));
  }
  get canStart() {
    return this.plan.reviewPeriodStatus !== 'CLOSED' && (this.plan.level === 'COMPANY'
      ? this.auth.hasRole('CAN_REVISE_COMPANY_KPI') : this.plan.level === 'DEPARTMENT'
        ? this.auth.hasRole('CAN_MANAGE_DEPARTMENT_KPI') : this.auth.hasRole('CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'));
  }
  get locked() { return this.busy || this.requestVisible || !!this.workspace?.locked; }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent) {
    if (this.requestVisible && (this.reason.trim() || this.busy)) { event.preventDefault(); event.returnValue = ''; }
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy) return false;
    if (this.workspace?.visible) return this.workspace.canLeave();
    if (!this.reason.trim() || !this.requestVisible) return true;
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('KPI_REVISIONS.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_REVISIONS.DISCARD_HELP'), nzOnOk: () => resolve(true), nzOnCancel: () => resolve(false) }));
  }
  async closeRequest() { if (await this.canLeave()) { this.requestVisible = false; this.reason = ''; } }
  manage() {
    if (this.locked || this.disabled || !this.canOperate) return;
    this.busy = true; this.error = '';
    this.api.history(this.plan.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: versions => {
        const working = versions.find(unfinishedRevision);
        this.working = working ?? null;
        if (working) this.workspace?.open(working.id);
        else if (this.canStart) { this.reason = ''; this.preparationMode = 'SUPERIOR'; this.reasonError = false; this.requestVisible = true; }
        else this.error = this.translate.instant('KPI_REVISIONS.NO_WORKING_REVISION');
      }, error: e => this.error = e.error?.message || this.translate.instant('KPI_PLAN.ERROR')
    });
  }
  start() {
    if (this.busy || !this.requestVisible || !this.canStart) return;
    this.reasonError = !validRevisionReason(this.reason); if (this.reasonError) return;
    this.busy = true; this.error = '';
    const request = this.plan.level === 'INDIVIDUAL' ? this.api.start(this.plan, this.reason, this.preparationMode) : this.api.start(this.plan, this.reason);
    request.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: revision => { this.requestVisible = false; this.reason = ''; this.changed.emit(revision); this.workspace?.open(revision.id); },
      error: e => this.error = e.error?.message || this.translate.instant('KPI_PLAN.ERROR')
    });
  }
}
