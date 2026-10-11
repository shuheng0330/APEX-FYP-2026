import { Component, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { AuthService } from '../../services/auth.service';
import { DepartmentKpiReviewComponent } from './department-kpi-review.component';
import { KpiAssistanceComponent } from './kpi-assistance.component';
import { KpiRevisionsComponent } from './kpi-revisions.component';
import { revisionAuthorities } from '../../models/kpi-revision.model';

@Component({
  standalone: true, imports: [TranslateModule, DepartmentKpiReviewComponent, KpiAssistanceComponent, KpiRevisionsComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  template: `
    <main class="review-page">
      <div class="page-heading"><h2>{{ 'KPI_ASSISTANCE.REVIEW_TITLE' | translate }}</h2></div>
      <nav class="workflow-tabs" [attr.aria-label]="'KPI_ASSISTANCE.REVIEW_TITLE' | translate">
        @if (canReviewDepartments) { <button type="button" [class.active]="tab === 'department'" [attr.aria-pressed]="tab === 'department'" (click)="selectTab('department')">{{ 'DEPARTMENT_REVIEW.TITLE' | translate }}</button> }
        @if (canAuthorize) { <button type="button" [class.active]="tab === 'assistance'" [attr.aria-pressed]="tab === 'assistance'" (click)="selectTab('assistance')">{{ 'KPI_ASSISTANCE.HR_TITLE' | translate }}</button> }
        @if (canRevise) { <button type="button" [class.active]="tab === 'revisions'" [attr.aria-pressed]="tab === 'revisions'" (click)="selectTab('revisions')">{{ 'KPI_REVISIONS.TITLE' | translate }}</button> }
      </nav>
      @if (tab === 'department' && canReviewDepartments) { <app-department-kpi-review class="embedded-review" /> }
      @if (tab === 'assistance' && canAuthorize) { <app-kpi-assistance [hr]="true" /> }
      @if (tab === 'revisions' && canRevise) { <app-kpi-revisions /> }
    </main>
  `
})
export class KpiReviewComponent {
  @ViewChild(KpiAssistanceComponent) assistanceWorkspace?: KpiAssistanceComponent;
  @ViewChild(DepartmentKpiReviewComponent) departmentWorkspace?: DepartmentKpiReviewComponent;
  @ViewChild(KpiRevisionsComponent) revisionWorkspace?: KpiRevisionsComponent;
  tab: 'department' | 'assistance' | 'revisions';
  constructor(private auth: AuthService, private modal: NzModalService, private translate: TranslateService) { this.tab = this.canReviewDepartments ? 'department' : this.canAuthorize ? 'assistance' : 'revisions'; }
  canLeave() { return this.revisionWorkspace?.canLeave() ?? true; }
  async selectTab(tab: 'department' | 'assistance' | 'revisions') {
    if (tab === this.tab || (tab === 'revisions' && !this.canRevise)) return;
    if ((tab === 'department' && !this.canReviewDepartments) || (tab === 'assistance' && !this.canAuthorize)) return;
    if (this.assistanceWorkspace?.locked || this.departmentWorkspace?.drawerVisible || this.departmentWorkspace?.busy || this.departmentWorkspace?.loading) {
      this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION'), nzContent: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION_HELP'), nzCancelText: null }); return;
    }
    if (this.revisionWorkspace && (this.revisionWorkspace.locked || !await this.canLeave())) return;
    this.tab = tab;
  }
  get canReviewDepartments() { return this.auth.hasRole('CAN_APPROVE_DEPARTMENT_KPI'); }
  get canAuthorize() { return this.auth.hasRole('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'); }
  get canRevise() { return revisionAuthorities.some(permission => this.auth.hasRole(permission)); }
}
