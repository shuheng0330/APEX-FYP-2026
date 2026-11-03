import { CommonModule, DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { forkJoin, Observable, of, switchMap, finalize, throwError } from 'rxjs';
import { KpiItem, KpiPeriodContext } from '../../models/kpi-plan.model';
import {
  CentralResult, CentralResultCheckpoint, CentralResultEvidence, CentralResultItem,
  CentralResultLevel, CentralResultOption, CentralResultRequest, CompanyResultDelegate
} from '../../models/kpi-central-result.model';
import { StaffOverviewData } from '../../models/staff.model';
import { AuthService } from '../../services/auth.service';
import { AuthorityService } from '../../services/authority.service';
import { KpiCentralResultService } from '../../services/kpi-central-result.service';
import { StaffService } from '../../services/staff.service';
import { KpiItemEditorComponent } from './kpi-item-editor.component';
import { notificationTarget } from '../../services/notification-navigation';
import { NotificationTargetComponent } from '../../components/notification-bell/notification-target.component';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, NzButtonModule, NzDrawerModule, NzInputModule,
    NzModalModule, NzSelectModule, KpiItemEditorComponent, NotificationTargetComponent],
  templateUrl: './kpi-central-results.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss', './kpi-central-results.component.scss']
})
export class KpiCentralResultsComponent implements OnInit {
  readonly target = notificationTarget();
  private readonly destroyRef = inject(DestroyRef);
  private readonly datePipe = new DatePipe('en');
  readonly points = [1, 2, 3, 4, 5];
  periods: KpiPeriodContext[] = [];
  options: CentralResultOption[] = [];
  checkpoints: CentralResultCheckpoint[] = [];
  results: CentralResult[] = [];
  periodId: number | null = null;
  level: CentralResultLevel | null = null;
  versionId: number | null = null;
  checkpointId: number | null = null;
  result: CentralResult | null = null;
  items: CentralResultItem[] = [];
  busy = false;
  ready = false;
  error = '';
  success = '';
  showValidation = false;
  draftWarnings = false;
  criteriaVisible = false;
  criteriaItems: KpiItem[] = [];
  historyVisible = false;
  history: CentralResult[] = [];
  historyDetail: CentralResult | null = null;
  correctionVisible = false;
  correctionReason = '';
  correctionError = '';
  delegates: CompanyResultDelegate[] = [];
  staff: StaffOverviewData[] = [];
  staffError = false;
  private entrantPermissionByRole = new Map<number, boolean>();
  selectedStaffId: string | null = null;
  private savedAnswers = '';

  constructor(
    private api: KpiCentralResultService, private staffApi: StaffService, private authorityApi: AuthorityService,
    public auth: AuthService,
    private translate: TranslateService, private modal: NzModalService
  ) {}

  ngOnInit() { this.load(); }
  get selectedPeriod() { return this.periods.find(p => p.id === this.periodId); }
  get selectedOption() { return this.options.find(o => o.planVersionId === this.versionId); }
  get selectedCheckpoint() { return this.checkpoints.find(c => c.id === this.checkpointId); }
  get levels(): CentralResultLevel[] {
    return (['COMPANY', 'DEPARTMENT'] as const).filter(level => this.options.some(o => o.level === level));
  }
  get versions() { return this.options.filter(o => o.level === this.level); }
  get editable() { return !!this.result && this.result.status === 'DRAFT' && this.result.allowedActions.includes('UPDATE_DRAFT'); }
  get dirty() { return this.editable && this.answerSignature() !== this.savedAnswers; }
  get activeDelegates() { return this.delegates.filter(d => !d.revokedAt); }
  get canViewAccessControl() {
    return this.auth.hasRole('CAN_VIEW_ACCESS_CONTROL') || this.auth.hasRole('CAN_MANAGE_ACCESS_CONTROL');
  }
  entrantPermission(staffId: string): 'GRANTED' | 'MISSING' | 'UNKNOWN' {
    const roleId = this.staff.find(member => member.staffId === staffId)?.roleId;
    if (roleId == null || !this.entrantPermissionByRole.has(roleId)) return 'UNKNOWN';
    return this.entrantPermissionByRole.get(roleId) ? 'GRANTED' : 'MISSING';
  }
  get eligibleStaff() {
    const assigned = new Set(this.activeDelegates.map(d => d.staffId));
    return this.staff.filter(s => s.accountStatus && !assigned.has(s.staffId));
  }
  get canStart() {
    return !this.result && !!this.selectedCheckpoint && this.selectedPeriod?.status === 'OPEN'
      && this.isOpen(this.selectedCheckpoint);
  }
  get canCorrect() { return !!this.result?.allowedActions.includes('CREATE_CORRECTION'); }
  get publicationReasons(): string[] {
    if (!this.result || this.result.status !== 'DRAFT') return [];
    const reasons = this.result.publicationBlockers.filter(reason =>
      !reason.startsWith('Enter Actual Achievement for ') && !reason.startsWith('Select a Result Point for '));
    for (const item of this.items) {
      if (!item.actualAchievement?.trim()) reasons.push(this.translate.instant('KPI_RESULTS.ACHIEVEMENT_MISSING', { name: item.kpi.name }));
      if (item.point == null) reasons.push(this.translate.instant('KPI_RESULTS.POINT_MISSING', { name: item.kpi.name }));
      else if (!Number.isInteger(item.point) || item.point < 1 || item.point > 5)
        reasons.push(this.translate.instant('KPI_RESULTS.POINT_INVALID', { name: item.kpi.name }));
      if ((item.actualAchievement?.length ?? 0) > 10000 || (item.comment?.length ?? 0) > 10000)
        reasons.push(this.translate.instant('KPI_RESULTS.TEXT_TOO_LONG', { name: item.kpi.name }));
    }
    return [...new Set(reasons)];
  }
  get canPublish() { return this.editable && !this.busy && !this.publicationReasons.length; }

  checkpointLabel(checkpoint: CentralResultCheckpoint) {
    if (checkpoint.reviewFrequency === 'MONTHLY') return this.datePipe.transform(checkpoint.endDate, 'MMMM yyyy') ?? '';
    if (checkpoint.reviewFrequency === 'QUARTERLY')
      return this.translate.instant('KPI_RESULTS.QUARTER', { number: Math.ceil(Number(checkpoint.endDate.slice(5, 7)) / 3), year: checkpoint.endDate.slice(0, 4) });
    return this.translate.instant('KPI_RESULTS.YEAR', { year: checkpoint.endDate.slice(0, 4) });
  }
  openingDate(checkpoint: CentralResultCheckpoint) {
    const [year, month, day] = checkpoint.endDate.split('-').map(Number);
    return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10);
  }
  isOpen(checkpoint: CentralResultCheckpoint) {
    const today = new Date();
    const localDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return localDate >= this.openingDate(checkpoint);
  }
  private answerSignature() {
    return JSON.stringify(this.items.map(i => [i.kpi.id, i.actualAchievement ?? null, i.point ?? null, i.comment ?? null]));
  }
  private accept(result: CentralResult) {
    this.result = result; this.items = structuredClone(result.items);
    this.savedAnswers = this.answerSignature(); this.showValidation = false; this.draftWarnings = false;
    this.results = [...this.results.filter(r => r.id !== result.id), result];
  }
  private selectResult() {
    const sameScope = this.results.filter(r => r.planVersionId === this.versionId && r.checkpointId === this.checkpointId);
    const chosen = sameScope.find(r => r.status === 'DRAFT') ?? sameScope.find(r => r.status === 'PUBLISHED')
      ?? [...sameScope].sort((a, b) => b.editionNumber - a.editionNumber)[0] ?? null;
    this.result = null; this.items = []; this.savedAnswers = ''; this.showValidation = false; this.draftWarnings = false;
    if (chosen) this.accept(chosen);
    this.error = ''; this.success = '';
  }
  private preferredCheckpoint() {
    return [...this.checkpoints].reverse().find(c => this.results.some(r => r.planVersionId === this.versionId && r.checkpointId === c.id))
      ?? [...this.checkpoints].reverse().find(c => this.isOpen(c)) ?? this.checkpoints[0];
  }
  private loadVersion(preferredCheckpointId?: number | null): Observable<void> {
    if (this.versionId === null) { this.checkpoints = []; this.checkpointId = null; this.selectResult(); return of(void 0); }
    return this.api.checkpoints(this.versionId).pipe(switchMap(checkpoints => {
      this.checkpoints = checkpoints;
      this.checkpointId = this.target.choose(checkpoints, 'checkpointId', c => c.id,
        checkpoints.find(c => c.id === preferredCheckpointId)?.id ?? this.preferredCheckpoint()?.id ?? null);
      this.selectResult();
      return of(void 0);
    }));
  }
  private loadPeriod(preferredLevel?: CentralResultLevel | null, preferredVersionId?: number | null,
    preferredCheckpointId?: number | null): Observable<void> {
    if (this.periodId === null) { this.options = []; this.results = []; this.selectResult(); return of(void 0); }
    return forkJoin({ options: this.api.optionsForPeriod(this.periodId), results: this.api.list(this.periodId) }).pipe(
      switchMap(({ options, results }) => {
        this.options = options; this.results = results;
        const linkedVersion = this.target.take('planVersionId');
        if (linkedVersion !== null) {
          const option = options.find(o => o.planVersionId === linkedVersion);
          this.target.require(!!option); preferredLevel = option!.level; preferredVersionId = linkedVersion;
        }
        this.level = this.levels.includes(preferredLevel!) ? preferredLevel! : this.levels[0] ?? null;
        const versions = this.versions;
        this.versionId = versions.find(v => v.planVersionId === preferredVersionId)?.planVersionId
          ?? versions.find(v => v.currentVersion)?.planVersionId ?? versions[0]?.planVersionId ?? null;
        return this.loadVersion(preferredCheckpointId);
      })
    );
  }
  private run(operation: Observable<unknown>, onSuccess?: () => void) {
    this.busy = true; this.error = ''; this.success = '';
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.ready = true; onSuccess?.(); }, error: e => { this.ready = true; this.fail(e); }
    });
  }
  load() {
    if (this.busy) return;
    this.ready = false; this.busy = true; this.error = ''; this.success = '';
    this.api.periods().pipe(switchMap(periods => {
      this.periods = periods;
      this.periodId = this.target.choose(periods, 'reviewPeriodId', p => p.id,
        periods.find(p => p.id === this.periodId)?.id ?? periods.find(p => p.status === 'OPEN')?.id ?? periods[0]?.id ?? null);
      return this.loadPeriod(this.level, this.versionId, this.checkpointId);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.ready = true; this.loadDelegates(); }, error: e => { this.ready = true; this.fail(e); }
    });
  }
  refresh() {
    if (this.busy) return;
    this.confirmDiscard(() => this.load());
  }
  changePeriod(id: number) {
    if (this.busy || id === this.periodId || !this.periods.some(p => p.id === id)) return;
    this.confirmDiscard(() => {
      this.periodId = id; this.selectedStaffId = null; this.delegates = []; this.staff = [];
      this.entrantPermissionByRole.clear();
      this.run(this.loadPeriod(this.level), () => this.loadDelegates());
    });
  }
  changeLevel(level: CentralResultLevel) {
    if (this.busy || level === this.level || !this.levels.includes(level)) return;
    this.confirmDiscard(() => {
      this.level = level; const versions = this.versions;
      this.versionId = versions.find(v => v.currentVersion)?.planVersionId ?? versions[0]?.planVersionId ?? null;
      this.run(this.loadVersion());
    });
  }
  changeVersion(id: number) {
    if (this.busy || id === this.versionId || !this.versions.some(v => v.planVersionId === id)) return;
    this.confirmDiscard(() => { this.versionId = id; this.run(this.loadVersion()); });
  }
  changeCheckpoint(id: number) {
    if (this.busy || id === this.checkpointId || !this.checkpoints.some(c => c.id === id)) return;
    this.confirmDiscard(() => { this.checkpointId = id; this.selectResult(); });
  }
  private confirmDiscard(action: () => void) {
    if (!this.dirty) { action(); return; }
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_RESULTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_RESULTS.DISCARD_HELP'), nzOnOk: () => { if (!this.busy) action(); } });
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy) return false;
    if (!this.dirty) return true;
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('KPI_RESULTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_RESULTS.DISCARD_HELP'),
      nzOnOk: () => resolve(true), nzOnCancel: () => resolve(false) }));
  }
  @HostListener('window:beforeunload', ['$event']) warnUnsaved(event: BeforeUnloadEvent) {
    if (this.busy || this.dirty) { event.preventDefault(); event.returnValue = ''; }
  }
  private request(): CentralResultRequest {
    return { planVersionId: this.versionId!, checkpointId: this.checkpointId!, expectedLockVersion: this.result?.lockVersion,
      items: this.items.map(i => ({ kpiId: i.kpi.id, actualAchievement: i.actualAchievement, point: i.point, comment: i.comment })) };
  }
  private validDraft() {
    const invalid = this.items.some(i => (i.point != null && (!Number.isInteger(i.point) || i.point < 1 || i.point > 5))
      || (i.actualAchievement?.length ?? 0) > 10000 || (i.comment?.length ?? 0) > 10000);
    if (invalid) this.error = this.translate.instant('KPI_RESULTS.FIX_FIELDS');
    return !invalid;
  }
  private persist(): Observable<CentralResult> {
    const request = this.request();
    const operation = this.result ? this.api.update(this.result.id, request) : this.api.create(request);
    return operation.pipe(switchMap(result => { this.accept(result); return of(result); }));
  }
  startDraft() {
    if (!this.canStart || this.busy) return;
    this.run(this.persist(), () => this.success = this.translate.instant('KPI_RESULTS.DRAFT_STARTED'));
  }
  saveDraft() {
    if (!this.editable || this.busy || !this.validDraft()) return;
    this.run(this.persist(), () => { this.draftWarnings = true; this.success = this.translate.instant('KPI_RESULTS.SAVED'); });
  }
  confirmPublish() {
    this.showValidation = true;
    if (!this.canPublish || !this.validDraft()) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_RESULTS.PUBLISH'),
      nzContent: this.translate.instant('KPI_RESULTS.PUBLISH_CONFIRM'),
      nzOnOk: () => this.publish() });
  }
  publish() {
    if (!this.canPublish || !this.result || !this.validDraft()) return;
    const operation = (this.dirty ? this.persist() : of(this.result)).pipe(switchMap(saved => {
      if (!saved.allowedActions.includes('PUBLISH'))
        return throwError(() => ({ error: { message: saved.publicationBlockers.join('; ') || this.translate.instant('KPI_RESULTS.READINESS_CHANGED') } }));
      return this.api.publish(saved.id, saved.lockVersion);
    }), switchMap(published => {
      if (published.previousEditionId != null) {
        this.results = this.results.map(edition => edition.id === published.previousEditionId
          ? { ...edition, status: 'SUPERSEDED' as const } : edition);
      }
      this.accept(published);
      return of(published);
    }));
    this.run(operation, () => this.success = this.translate.instant('KPI_RESULTS.PUBLISHED'));
  }
  openCorrection() {
    if (!this.canCorrect || this.busy) return;
    this.correctionReason = ''; this.correctionError = ''; this.correctionVisible = true;
  }
  createCorrection() {
    if (!this.canCorrect || this.busy || !this.result) return;
    if (!this.correctionReason.trim() || this.correctionReason.trim().length > 10000) {
      this.correctionError = this.translate.instant('KPI_RESULTS.REASON_REQUIRED'); return;
    }
    this.run(this.api.correct(this.result.id, this.result.lockVersion, this.correctionReason.trim()).pipe(
      switchMap(draft => { this.accept(draft); return of(draft); })
    ), () => { this.correctionVisible = false; this.success = this.translate.instant('KPI_RESULTS.CORRECTION_STARTED'); });
  }
  continueCorrection() {
    const id = this.result?.workingCorrectionId;
    if (id == null || this.busy) return;
    this.run(this.api.get(id).pipe(switchMap(draft => { this.accept(draft); return of(draft); })));
  }
  viewCriteria(item: CentralResultItem) {
    this.criteriaItems = [structuredClone(item.kpi)]; this.criteriaVisible = true;
  }
  openHistory() {
    if (!this.result || this.busy) return;
    this.run(this.api.history(this.result.id).pipe(switchMap(history => {
      this.history = history; this.historyDetail = null; this.historyVisible = true; return of(history);
    })));
  }
  attachEvidence(item: CentralResultItem, event: Event) {
    const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
    if (!file || !this.editable || this.busy || !this.validDraft()) return;
    if (!file.size) { this.error = this.translate.instant('KPI_RESULTS.EMPTY_FILE'); return; }
    const operation = (this.dirty ? this.persist() : of(this.result!)).pipe(switchMap(saved => {
      const itemId = saved.items.find(i => i.kpi.id === item.kpi.id)?.id;
      return itemId == null ? throwError(() => ({ error: { message: this.translate.instant('KPI_RESULTS.READINESS_CHANGED') } }))
        : this.api.upload(itemId, saved.lockVersion, file);
    }), switchMap(mutation => {
      if (this.result) {
        this.result.lockVersion = mutation.resultLockVersion;
        const current = this.result.items.find(i => i.id === mutation.evidence.itemId);
        if (current) current.evidence = [...current.evidence, mutation.evidence];
        const edited = this.items.find(i => i.id === mutation.evidence.itemId);
        if (edited) edited.evidence = [...edited.evidence, mutation.evidence];
        this.results = this.results.map(r => r.id === this.result!.id ? this.result! : r);
      }
      return of(mutation);
    }));
    this.run(operation, () => this.success = this.translate.instant('KPI_RESULTS.EVIDENCE_SAVED'));
  }
  removeEvidence(item: CentralResultItem, evidence: CentralResultEvidence) {
    if (!this.editable || this.busy || !this.result) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_RESULTS.REMOVE_EVIDENCE'), nzContent: evidence.originalFilename,
      nzOnOk: () => {
        if (!this.editable || this.busy || !this.result) return;
        const operation = (this.dirty ? this.persist() : of(this.result)).pipe(switchMap(saved =>
          this.api.removeEvidence(evidence.id, saved.lockVersion)), switchMap(mutation => {
          if (this.result) {
            this.result.lockVersion = mutation.resultLockVersion;
            const current = this.result.items.find(i => i.kpi.id === item.kpi.id);
            if (current) current.evidence = current.evidence.filter(f => f.id !== evidence.id);
            const edited = this.items.find(i => i.kpi.id === item.kpi.id);
            if (edited) edited.evidence = edited.evidence.filter(f => f.id !== evidence.id);
          }
          return of(mutation);
        }));
        this.run(operation);
      } });
  }
  downloadEvidence(evidence: CentralResultEvidence) {
    if (this.busy) return;
    this.run(this.api.download(evidence.id).pipe(switchMap(blob => {
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = evidence.originalFilename; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      return of(blob);
    })));
  }
  private loadDelegates() {
    if (!this.auth.hasRole('CAN_MANAGE_COMPANY_KPI') || this.periodId === null) return;
    const periodId = this.periodId;
    this.entrantPermissionByRole.clear();
    this.api.delegates(periodId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: delegates => { if (this.periodId === periodId) this.delegates = delegates; }, error: e => this.fail(e)
    });
    this.staffApi.getStaffOverview().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: staff => { if (this.periodId === periodId) { this.staff = staff; this.staffError = false; } },
      error: () => { if (this.periodId === periodId) this.staffError = true; }
    });
    if (this.canViewAccessControl) {
      this.authorityApi.getStaffAccessControlOverview().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: overview => {
          if (this.periodId !== periodId) return;
          const permissionId = overview.permissions.find(p => p.name === 'CAN_ENTER_COMPANY_KPI_RESULTS')?.id;
          if (permissionId == null) return;
          for (const department of overview.departments) {
            for (const role of department.roles) {
              this.entrantPermissionByRole.set(role.roleId, role.authorityMap[permissionId] === true);
            }
          }
        },
        error: () => { if (this.periodId === periodId) this.entrantPermissionByRole.clear(); }
      });
    }
  }
  staffName(id: string) {
    const member = this.staff.find(s => s.staffId === id);
    return member ? `${member.staffName || member.staffEmail} (${member.staffEmail})` : id;
  }
  assignDelegate() {
    if (this.busy || this.periodId === null || this.selectedPeriod?.status === 'CLOSED' || !this.selectedStaffId
      || !this.eligibleStaff.some(s => s.staffId === this.selectedStaffId)) return;
    const periodId = this.periodId;
    this.run(this.api.assignDelegate(periodId, this.selectedStaffId).pipe(switchMap(delegate => {
      this.delegates = [delegate, ...this.delegates]; this.selectedStaffId = null; return of(delegate);
    })), () => this.success = this.translate.instant('KPI_RESULTS.DELEGATE_ASSIGNED'));
  }
  revokeDelegate(delegate: CompanyResultDelegate) {
    if (this.busy || this.periodId === null || this.selectedPeriod?.status === 'CLOSED') return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_RESULTS.REMOVE_ENTRANT'),
      nzContent: this.translate.instant('KPI_RESULTS.REMOVE_ENTRANT_CONFIRM', { name: this.staffName(delegate.staffId) }),
      nzOnOk: () => {
        if (this.busy || this.periodId === null) return;
        this.run(this.api.revokeDelegate(this.periodId, delegate.id).pipe(switchMap(updated => {
          this.delegates = this.delegates.map(d => d.id === updated.id ? updated : d); return of(updated);
        })), () => this.success = this.translate.instant('KPI_RESULTS.DELEGATE_REMOVED'));
      } });
  }
  private fail(error: { error?: { message?: string }; status?: number }) {
    this.error = error.status === 403 ? this.translate.instant('KPI_RESULTS.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('KPI_RESULTS.ERROR');
  }
}
