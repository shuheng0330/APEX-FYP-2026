import { Component, DestroyRef, Input, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, of, switchMap, throwError } from 'rxjs';
import { KpiAssistance, KpiAssistanceEmployee, KpiAssistanceStatus } from '../../models/kpi-assistance.model';
import { KpiAssistanceService } from '../../services/kpi-assistance.service';
import { AuthService } from '../../services/auth.service';
import { AssistedKpiPlanComponent } from './assisted-kpi-plan.component';

@Component({
  selector: 'app-kpi-assistance', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzSelectModule, NzModalModule, AssistedKpiPlanComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'], templateUrl: './kpi-assistance.component.html'
})
export class KpiAssistanceComponent implements OnInit {
  @Input() hr = false;
  @ViewChild(AssistedKpiPlanComponent) planWorkspace?: AssistedKpiPlanComponent;
  private readonly destroyRef = inject(DestroyRef);
  readonly statuses: KpiAssistanceStatus[] = ['REQUESTED', 'AUTHORIZED', 'REJECTED', 'CONSUMED'];
  filter: KpiAssistanceStatus = 'REQUESTED';
  requestedDateOrder: 'ascending' | 'descending' = 'descending';
  cases: KpiAssistance[] = []; employees: KpiAssistanceEmployee[] = [];
  selected: KpiAssistance | null = null; workspace: KpiAssistance | null = null;
  loading = false; busy = false; ready = false; accessChanged = false;
  error = ''; success = ''; search = ''; periodId: number | null = null;
  drawerVisible = false; rejectMode = false; reason = ''; reasonError = false;
  requestVisible = false; requestPeriodId: number | null = null; participantId: number | null = null; requestError = '';
  requestReason = ''; requestReasonError = false;
  requestValidation = false;
  get requestPeriodError() { return this.requestValidation && !this.requestPeriods.some(period => period.id === this.requestPeriodId); }
  get requestEmployeeError() { return this.requestValidation && !this.requestEmployees.some(employee => employee.ownerParticipantId === this.participantId); }
  constructor(private api: KpiAssistanceService, private auth: AuthService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get locked() { return this.loading || this.busy || this.drawerVisible || this.requestVisible || !!this.planWorkspace?.busy || !!this.planWorkspace?.drawerVisible; }
  get allowed() { return this.auth.hasRole(this.hr ? 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE' : 'CAN_REVIEW_INDIVIDUAL_KPI'); }
  get mine() { return this.hr ? this.cases : this.cases.filter(value => value.superiorId === this.auth.userId); }
  get periods() { return [...new Map(this.mine.map(value => [value.reviewPeriodId, { id: value.reviewPeriodId, name: value.reviewPeriodName }])).values()]; }
  private get filtered() {
    const search = this.search.trim().toLowerCase();
    return this.mine.filter(value => (this.periodId === null || value.reviewPeriodId === this.periodId)
      && (!search || `${value.employeeName} ${value.superiorName} ${value.departmentName ?? ''}`.toLowerCase().includes(search)));
  }
  get visibleCases() {
    const direction = this.requestedDateOrder === 'ascending' ? 1 : -1;
    return this.filtered.filter(value => value.status === this.filter)
      .sort((a, b) => direction * (Date.parse(a.requestedAt) - Date.parse(b.requestedAt) || a.id - b.id));
  }
  toggleRequestedDateOrder() { this.requestedDateOrder = this.requestedDateOrder === 'ascending' ? 'descending' : 'ascending'; }
  count(status: KpiAssistanceStatus) { return this.filtered.filter(value => value.status === status).length; }
  get eligible() {
    return this.employees.filter(employee => !this.mine.some(value => value.ownerParticipantId === employee.ownerParticipantId && value.status !== 'REJECTED'));
  }
  get requestPeriods() { return [...new Map(this.eligible.map(value => [value.reviewPeriodId, { id: value.reviewPeriodId, name: value.reviewPeriodName }])).values()]; }
  get requestEmployees() { return this.eligible.filter(value => value.reviewPeriodId === this.requestPeriodId); }
  get canDecide() { return this.allowed && this.hr && !this.accessChanged && this.selected?.status === 'REQUESTED' && ['UPCOMING', 'OPEN'].includes(this.selected.reviewPeriodStatus); }
  canPrepare(value: KpiAssistance) {
    return this.allowed && !this.hr && !this.accessChanged && value.superiorId === this.auth.userId
      && ((value.status === 'AUTHORIZED' && ['UPCOMING', 'OPEN'].includes(value.reviewPeriodStatus)) || !!value.planId);
  }
  canRequestAgain(value: KpiAssistance) { return this.allowed && !this.hr && value.status === 'REJECTED' && this.eligible.some(employee => employee.ownerParticipantId === value.ownerParticipantId); }
  load() {
    if (!this.allowed || this.busy || this.loading) return;
    this.loading = true; this.error = ''; this.accessChanged = false;
    forkJoin({ cases: this.api.list(), employees: this.hr ? of([] as KpiAssistanceEmployee[]) : this.api.employees() })
      .pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => { this.cases = result.cases; this.employees = result.employees; this.ready = true; }, error: error => this.fail(error)
      });
  }
  requestPermission(again?: KpiAssistance) {
    if (!this.allowed || this.hr || this.busy || this.loading) return;
    this.loading = true; this.error = ''; this.success = '';
    forkJoin({ cases: this.api.list(), employees: this.api.employees() }).pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.cases = result.cases; this.employees = result.employees;
        const target = again ? this.eligible.find(value => value.ownerParticipantId === again.ownerParticipantId) : undefined;
        if (again && !target) { this.error = this.translate.instant('KPI_ASSISTANCE.NOT_ELIGIBLE'); return; }
        this.requestPeriodId = target?.reviewPeriodId ?? this.requestPeriods[0]?.id ?? null;
        this.participantId = target?.ownerParticipantId ?? null; this.requestError = ''; this.requestReason = ''; this.requestReasonError = false; this.requestValidation = false;
        this.drawerVisible = false; this.requestVisible = true;
      }, error: error => this.fail(error)
    });
  }
  sendRequest() {
    if (!this.allowed || this.hr || this.busy || this.loading) return;
    this.requestValidation = true;
    const employee = this.requestEmployees.find(value => value.ownerParticipantId === this.participantId);
    const reason = this.requestReason.trim();
    this.requestReasonError = !reason || this.requestReason.length > 10000;
    if (!employee || this.requestPeriodError || this.requestReasonError) { this.requestError = this.translate.instant('FORM_VALIDATION.FIX_FIELDS'); return; }
    this.busy = true; this.requestError = '';
    this.api.request(employee.ownerParticipantId, reason).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.replace(value); this.requestVisible = false; this.filter = 'REQUESTED'; this.periodId = value.reviewPeriodId; this.search = ''; this.success = this.translate.instant('KPI_ASSISTANCE.REQUEST_SENT'); },
      error: error => { this.requestError = this.message(error); }
    });
  }
  open(value: KpiAssistance, prepare = false) {
    if (!this.allowed || this.busy || this.loading) return;
    this.loading = true; this.error = ''; this.success = ''; this.reason = ''; this.reasonError = false; this.rejectMode = false;
    this.api.get(value.id).pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: fresh => { this.replace(fresh); this.selected = fresh;
        if (prepare && this.canPrepare(fresh)) { this.drawerVisible = false; this.workspace = fresh; }
        else { this.workspace = null; this.drawerVisible = true; }
      }, error: error => this.fail(error)
    });
  }
  close() { if (!this.busy && !this.loading) { this.drawerVisible = false; this.rejectMode = false; } }
  approve() {
    if (!this.canDecide || this.busy || this.loading) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.APPROVE_REQUEST'), nzContent: this.translate.instant('KPI_ASSISTANCE.APPROVE_HELP'), nzOnOk: () => this.decide(false) });
  }
  reject() {
    if (!this.canDecide || this.busy || this.loading) return;
    if (!this.reason.trim() || this.reason.trim().length > 10000) { this.reasonError = true; return; }
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.REJECT_REQUEST'), nzContent: this.translate.instant('KPI_ASSISTANCE.REJECT_HELP'), nzOnOk: () => this.decide(true) });
  }
  decide(reject: boolean) {
    if (!this.canDecide || this.busy || this.loading || !this.selected) return;
    const reason = this.reason.trim();
    if (reject && (!reason || reason.length > 10000)) { this.reasonError = true; return; }
    const id = this.selected.id; this.busy = true; this.error = ''; this.success = '';
    this.api.get(id).pipe(switchMap(fresh => {
      this.selected = fresh; this.replace(fresh);
      if (!this.canDecide) return throwError(() => ({ error: { message: this.translate.instant('KPI_ASSISTANCE.DECISION_CHANGED') } }));
      return reject ? this.api.reject(id, reason) : this.api.approve(id);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.selected = value; this.replace(value); this.rejectMode = false; this.reason = ''; this.success = this.translate.instant(reject ? 'KPI_ASSISTANCE.REQUEST_REJECTED' : 'KPI_ASSISTANCE.REQUEST_APPROVED'); },
      error: error => this.fail(error)
    });
  }
  replace(value: KpiAssistance) { this.cases = [...this.cases.filter(existing => existing.id !== value.id), value].sort((a, b) => b.requestedAt.localeCompare(a.requestedAt) || b.id - a.id); }
  closeWorkspace() { this.workspace = null; this.load(); }
  private message(error: { status?: number; error?: { message?: string } }) {
    if (error.status === 403) { this.accessChanged = true; return this.translate.instant('KPI_ASSISTANCE.ACCESS_CHANGED'); }
    return error.error?.message || this.translate.instant('KPI_PLAN.ERROR');
  }
  private fail(error: { status?: number; error?: { message?: string } }) { this.error = this.message(error); }
}
