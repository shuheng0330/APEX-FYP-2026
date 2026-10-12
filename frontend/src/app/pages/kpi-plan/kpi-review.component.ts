import { Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { AuthService } from '../../services/auth.service';
import { DepartmentKpiReviewComponent } from './department-kpi-review.component';
import { KpiAssistanceComponent } from './kpi-assistance.component';
import { KpiRevisionsComponent } from './kpi-revisions.component';
import { revisionAuthorities } from '../../models/kpi-revision.model';
import { OrganisationReviewTab, organisationReviewTabs } from './review-task-directory';
import { ReviewTabNavigation } from './review-tab-navigation';
import { ReviewTaskGuideComponent } from './review-task-guide.component';

@Component({
  standalone: true, imports: [TranslateModule, DepartmentKpiReviewComponent, KpiAssistanceComponent, KpiRevisionsComponent, ReviewTaskGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  template: `
    <main class="review-page">
      <div class="page-heading"><div><h2>{{ 'KPI_ASSISTANCE.REVIEW_TITLE' | translate }}</h2>
        <p class="help">{{ 'REVIEW_NAV.ORGANISATION_HELP' | translate }}</p></div>
        <app-review-task-guide [disabled]="navigation.pending" />
      </div>
      <nav class="workflow-tabs" [attr.aria-label]="'KPI_ASSISTANCE.REVIEW_TITLE' | translate">
        @if (canReviewDepartments) { <button type="button" [class.active]="tab === 'department'" [attr.aria-pressed]="tab === 'department'" (click)="selectTab('department')">{{ 'DEPARTMENT_REVIEW.TITLE' | translate }}</button> }
        @if (canAuthorize) { <button type="button" [class.active]="tab === 'assistance'" [attr.aria-pressed]="tab === 'assistance'" (click)="selectTab('assistance')">{{ 'KPI_ASSISTANCE.HR_TITLE' | translate }}</button> }
        @if (canRevise) { <button type="button" [class.active]="tab === 'revisions'" [attr.aria-pressed]="tab === 'revisions'" (click)="selectTab('revisions')">{{ 'KPI_REVISIONS.TITLE' | translate }}</button> }
      </nav>
      @if (tab === null) { <p class="notice">{{ 'REVIEW_NAV.NO_TABS' | translate }}</p> }
      @if (tab === 'department' && canReviewDepartments) { <app-department-kpi-review class="embedded-review" /> }
      @if (tab === 'assistance' && canAuthorize) { <app-kpi-assistance [hr]="true" /> }
      @if (tab === 'revisions' && canRevise) { <app-kpi-revisions /> }
    </main>
  `
})
export class KpiReviewComponent implements OnInit {
  @ViewChild(KpiAssistanceComponent) assistanceWorkspace?: KpiAssistanceComponent;
  @ViewChild(DepartmentKpiReviewComponent) departmentWorkspace?: DepartmentKpiReviewComponent;
  @ViewChild(KpiRevisionsComponent) revisionWorkspace?: KpiRevisionsComponent;
  tab: OrganisationReviewTab | null = null;
  readonly navigation = new ReviewTabNavigation(inject(Router), inject(ActivatedRoute), inject(DestroyRef),
    () => organisationReviewTabs(permission => this.auth.hasRole(permission)), () => this.tab,
    tab => this.tab = tab, () => this.canChangeTab());
  constructor(private auth: AuthService, private modal: NzModalService, private translate: TranslateService) {
    this.tab = this.navigation.initial();
  }
  ngOnInit() { this.navigation.connect(); }
  private canChangeTab(): boolean | Promise<boolean> {
    if (this.assistanceWorkspace?.locked || this.departmentWorkspace?.drawerVisible || this.departmentWorkspace?.busy || this.departmentWorkspace?.loading) {
      this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION'), nzContent: this.translate.instant('KPI_ASSISTANCE.FINISH_ACTION_HELP'), nzCancelText: null }); return false;
    }
    if (this.revisionWorkspace?.locked) return false;
    return this.revisionWorkspace?.canLeave() ?? true;
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.navigation.pending || this.assistanceWorkspace?.locked || this.departmentWorkspace?.drawerVisible
      || this.departmentWorkspace?.busy || this.departmentWorkspace?.loading) return false;
    return this.revisionWorkspace?.canLeave() ?? true;
  }
  selectTab(tab: OrganisationReviewTab) { this.navigation.select(tab); }
  get canReviewDepartments() { return this.auth.hasRole('CAN_APPROVE_DEPARTMENT_KPI'); }
  get canAuthorize() { return this.auth.hasRole('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'); }
  get canRevise() { return revisionAuthorities.some(permission => this.auth.hasRole(permission)); }
}
