import { CommonModule } from '@angular/common';
import { Component, DestroyRef, HostListener, Input, OnChanges, OnInit, QueryList, SimpleChanges, ViewChild, ViewChildren, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { NzModalService } from 'ng-zorro-antd/modal';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { KpiLevel, KpiPeriodContext, KpiPlan } from '../../models/kpi-plan.model';
import { KpiAmendmentAccess, KpiRevision, KpiVersionStatus, unfinishedRevision } from '../../models/kpi-revision.model';
import { KpiRevisionGuideComponent } from './kpi-revision-guide.component';
import { KpiRevisionPanelComponent } from './kpi-revision-panel.component';
import { KpiRevisionWorkspaceComponent } from './kpi-revision-workspace.component';

@Component({
  selector: 'app-kpi-revisions', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzInputModule, NzPaginationModule,
    KpiRevisionGuideComponent, KpiRevisionPanelComponent, KpiRevisionWorkspaceComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss', './kpi-revision.scss'],
  templateUrl: './kpi-revisions.component.html'
})
export class KpiRevisionsComponent implements OnInit, OnChanges {
  @Input() sharedPeriod = false;
  @Input() superiorOnly = false;
  @Input() periodId: number | null = null;
  @ViewChild(KpiRevisionWorkspaceComponent) workspace?: KpiRevisionWorkspaceComponent;
  @ViewChildren(KpiRevisionPanelComponent) panels?: QueryList<KpiRevisionPanelComponent>;
  private readonly destroyRef = inject(DestroyRef);
  private initialized = false;
  periods: KpiPeriodContext[] = []; plans: KpiPlan[] = []; revisions: KpiRevision[] = [];
  level: KpiLevel | '' = ''; search = '';
  employeeSearch = ''; employeeDepartment = ''; employeeRole = ''; employeePage = 1;
  readonly employeePageSize = 10;
  filter: KpiVersionStatus | '' = ''; busy = false; error = ''; success = '';
  access: KpiAmendmentAccess | null = null; enabled = false; closeDate = '';
  readonly statuses: KpiVersionStatus[] = ['REQUESTED', 'DRAFT', 'RETURNED', 'PENDING_APPROVAL', 'PENDING_MD_APPROVAL', 'PENDING_HOD_CONFIRMATION', 'EFFECTIVE', 'SUPERSEDED', 'CANCELLED'];
  constructor(private api: KpiRevisionService, private auth: AuthService, private modal: NzModalService, private translate: TranslateService) {}
  get canManageAccess() { return !this.superiorOnly && this.auth.hasRole('CAN_MANAGE_KPI_AMENDMENTS'); }
  get selectedPeriod() { return this.periods.find(p => p.id === this.periodId); }
  get visibleRevisions() { return this.revisions.filter(r => this.matchesScope(r) && (!this.filter || r.status === this.filter)); }
  get summaryPlans() { return this.plans.filter(p => p.level !== 'INDIVIDUAL'); }
  get individualPlans() { return this.plans.filter(p => p.level === 'INDIVIDUAL'); }
  get employeeDepartments() { return [...new Set(this.individualPlans.map(p => p.departmentName).filter((v): v is string => !!v))].sort(); }
  get employeeRoles() { return [...new Set(this.individualPlans.map(p => p.roleName).filter((v): v is string => !!v))].sort(); }
  get filteredEmployees() {
    const search = this.employeeSearch.trim().toLowerCase();
    return this.individualPlans.filter(p => (!this.employeeDepartment || p.departmentName === this.employeeDepartment)
      && (!this.employeeRole || p.roleName === this.employeeRole)
      && (!search || `${p.employeeName ?? ''} ${p.departmentName ?? ''} ${p.roleName ?? ''}`.toLowerCase().includes(search)))
      .sort((a, b) => (a.employeeName ?? '').localeCompare(b.employeeName ?? '') || a.id - b.id);
  }
  get pagedEmployees() { return this.filteredEmployees.slice((this.employeePage - 1) * this.employeePageSize, this.employeePage * this.employeePageSize); }
  get employeeFiltered() { return !!(this.employeeSearch.trim() || this.employeeDepartment || this.employeeRole); }
  employeeFiltersChanged() { this.employeePage = 1; }
  workingFor(planId: number) { return this.revisions.find(r => r.planId === planId && unfinishedRevision(r)) ?? null; }
  private resetEmployeeFilters() { this.employeeSearch = ''; this.employeeDepartment = ''; this.employeeRole = ''; this.employeePage = 1; }
  get availableStatuses() { return this.statuses.filter(status => this.superiorOnly ? !['PENDING_MD_APPROVAL', 'PENDING_HOD_CONFIRMATION'].includes(status) : true); }
  private matchesScope(row: { level: KpiLevel; employeeName?: string | null; departmentName?: string | null; revisionSuperiorId?: string | null }) {
    const search = this.search.trim().toLowerCase();
    return (!this.superiorOnly || (row.level === 'INDIVIDUAL' && !!this.auth.userId && row.revisionSuperiorId === this.auth.userId)) && (!this.level || row.level === this.level)
      && (!search || `${row.employeeName ?? ''} ${row.departmentName ?? ''}`.toLowerCase().includes(search));
  }
  get locked() { return this.busy || !!this.workspace?.locked || !!this.panels?.some(p => p.locked); }
  get accessDirty() { return !!this.access && (this.enabled !== this.access.enabled || (this.closeDate || null) !== this.access.closeDate); }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent) {
    if (this.accessDirty) { event.preventDefault(); event.returnValue = ''; }
  }
  async canLeave(): Promise<boolean> {
    if (this.busy) return false;
    if (this.workspace && !await this.workspace.canLeave()) return false;
    for (const panel of this.panels ?? []) if (!await panel.canLeave()) return false;
    if (!this.accessDirty) return true;
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('KPI_REVISIONS.DISCARD_TITLE'),
      nzContent: this.translate.instant('KPI_REVISIONS.DISCARD_HELP'), nzOnOk: () => resolve(true), nzOnCancel: () => resolve(false) }));
  }
  ngOnInit() {
    this.initialized = true;
    if (this.sharedPeriod) { this.load(); return; }
    this.busy = true;
    this.api.periods().pipe(switchMap(periods => {
      this.periods = periods; this.periodId = periods.find(p => p.status === 'OPEN')?.id ?? periods[0]?.id ?? null;
      return this.periodId === null ? of({ plans: [], revisions: [], access: null }) : this.context(this.periodId);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => this.acceptContext(result), error: e => this.fail(e) });
  }
  ngOnChanges(changes: SimpleChanges) {
    if (this.initialized && this.sharedPeriod && changes['periodId']) { this.resetEmployeeFilters(); this.load(); }
  }
  async changePeriod(id: number) {
    if (this.locked || id === this.periodId || !await this.canLeave()) return;
    this.periodId = id; this.resetEmployeeFilters(); this.load();
  }
  load() {
    if (this.busy || this.periodId === null) return;
    this.busy = true; this.error = ''; this.success = ''; this.access = null; this.plans = []; this.revisions = [];
    this.context(this.periodId)
      .pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: result => this.acceptContext(result), error: e => this.fail(e)
      });
  }
  private context(periodId: number) {
    return forkJoin({ plans: this.superiorOnly ? of([] as KpiPlan[]) : this.api.plans(periodId),
      revisions: this.superiorOnly ? this.api.list(periodId, 'INDIVIDUAL') : this.api.list(periodId),
      access: this.canManageAccess ? this.api.access(periodId) : of(null) });
  }
  private acceptContext(result: { plans: KpiPlan[]; revisions: KpiRevision[]; access: KpiAmendmentAccess | null }) {
    this.plans = result.plans; this.revisions = result.revisions; this.access = result.access;
    this.enabled = result.access?.enabled ?? false; this.closeDate = result.access?.closeDate ?? '';
  }
  saveAccess() {
    if (this.locked || !this.canManageAccess || !this.access || this.access.periodStatus === 'CLOSED' || this.periodId === null) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_REVISIONS.SAVE_ACCESS'),
      nzContent: this.translate.instant('KPI_REVISIONS.ACCESS_CONFIRM'), nzOnOk: () => this.persistAccess() });
  }
  private persistAccess() {
    if (this.locked || !this.canManageAccess || this.periodId === null || this.access?.periodStatus === 'CLOSED') return;
    this.busy = true; this.error = '';
    this.api.saveAccess(this.periodId, this.enabled, this.closeDate || null).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: access => { this.access = access; this.enabled = access.enabled; this.closeDate = access.closeDate ?? ''; this.success = this.translate.instant('KPI_REVISIONS.ACCESS_SAVED'); },
      error: e => this.fail(e)
    });
  }
  updated(revision: KpiRevision) {
    this.revisions = [...this.revisions.filter(r => r.id !== revision.id), revision].sort((a, b) => b.id - a.id);
    // Preserve an open workspace; refresh the current plan summary only after finalisation.
    if (!this.superiorOnly && revision.status === 'EFFECTIVE' && this.periodId !== null) {
      this.api.plans(this.periodId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: plans => this.plans = plans, error: e => this.fail(e) });
    }
  }
  private fail(e: { error?: { message?: string } }) { this.error = e.error?.message || this.translate.instant('KPI_PLAN.ERROR'); }
}
