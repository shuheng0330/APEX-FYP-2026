import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzGridModule } from 'ng-zorro-antd/grid';

import { RoleService } from '../../../services/role.service';
import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { CompetencyAssignmentCollaborationService } from '../../../services/competency-assignment-collaboration.service';

import { CompetencyAssignmentProposalOverviewData, RoleCompetencyProposalId } from '../../../models/competency-collaboration.model';

import { AddCompetencyAssignmentCollabComponent } from './add-competency-assignment-collab/add-competency-assignment-collab.component';
import { ViewCompetencyAssignmentCollabComponent } from './view-competency-assignment-collab/view-competency-assignment-collab.component';
import { EditCompetencyAssignmentCollabComponent } from './edit-competency-assignment-collab/edit-competency-assignment-collab.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-competency-assignment-collab',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, AddCompetencyAssignmentCollabComponent,
    ViewCompetencyAssignmentCollabComponent, EditCompetencyAssignmentCollabComponent,
    NzTagModule, NzAvatarModule, NzToolTipModule, TablePageSizeSelectorComponent,
    NzCardModule, NzEmptyModule, NzTabsModule, NzBadgeModule, NzCheckboxModule,
    NzGridModule],
  templateUrl: './competency-assignment-collab.component.html',
  styleUrl: './competency-assignment-collab.component.scss'
})
export class CompetencyAssignmentCollabComponent {
  @ViewChild(AddCompetencyAssignmentCollabComponent) addCompetencyAssignmentCollabComponent!: AddCompetencyAssignmentCollabComponent;
  @ViewChild(ViewCompetencyAssignmentCollabComponent) viewCompetencyAssignmentCollabComponent!: ViewCompetencyAssignmentCollabComponent;
  @ViewChild(EditCompetencyAssignmentCollabComponent) editCompetencyAssignmentCollabComponent!: EditCompetencyAssignmentCollabComponent;

  private readonly titleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.TITLE';
  private readonly searchAnyKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.ANY';
  private readonly searchIdKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.ID';
  private readonly searchDepartmentKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.ROLE';
  private readonly searchCompetencyKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.COMPETENCY';
  private readonly searchCollaboratorNameKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.COLLABORATOR.NAME';
  private readonly searchCollaboratorEmailKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.SEARCH.FIELD.COLLABORATOR.EMAIL';
  private readonly rejectConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.REJECT.CONFIRM.TITLE';
  private readonly rejectConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.REJECT.CONFIRM.CONTENT';
  private readonly rejectConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.REJECT.CONFIRM.OK';
  private readonly bulkRejectConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_REJECT.CONFIRM.TITLE';
  private readonly bulkRejectConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_REJECT.CONFIRM.CONTENT';
  private readonly bulkRejectConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_REJECT.CONFIRM.OK';
  private readonly approveConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.APPROVE.CONFIRM.TITLE';
  private readonly approveConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.APPROVE.CONFIRM.CONTENT';
  private readonly approveConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.APPROVE.CONFIRM.OK';
  private readonly bulkApproveConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_APPROVE.CONFIRM.TITLE';
  private readonly bulkApproveConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_APPROVE.CONFIRM.CONTENT';
  private readonly bulkApproveConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.ASSIGNMENT.BULK_APPROVE.CONFIRM.OK';

  optionList: Option[] = []
  fullDataList: CompetencyAssignmentProposalOverviewData[] = [];
  reviewList: CompetencyAssignmentProposalOverviewData[] = []; // Tab 1
  proposalList: CompetencyAssignmentProposalOverviewData[] = []; // Tab 2
  selectedTabIndex = 0;

  setOfCheckedId = new Set<RoleCompetencyProposalId>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  searchValue?: string;
  searchField: number = 0;
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  confirmModal?: NzModalRef;

  currentUserId: string | null = null;

  constructor(private translateService: TranslateService, private title: Title,
    private authService: AuthService, private loadingService: LoadingService,
    private competencyAssignmentCollaborationService: CompetencyAssignmentCollaborationService,
    private route: ActivatedRoute, private modal: NzModalService
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchCollabCompetencyAssignmentOverview();
  }

  loadOptionList(): void {
    if (this.hasAccess(['CAN_MANAGE_ROLE'])) {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 1, name: this.translateService.instant(this.searchIdKey) },
        { id: 2, name: this.translateService.instant(this.searchDepartmentKey) },
        { id: 3, name: this.translateService.instant(this.searchRoleKey) },
        { id: 4, name: this.translateService.instant(this.searchCompetencyKey) },
        { id: 5, name: this.translateService.instant(this.searchCollaboratorNameKey) },
        { id: 6, name: this.translateService.instant(this.searchCollaboratorEmailKey) },
      ]
    } else {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 2, name: this.translateService.instant(this.searchDepartmentKey) },
        { id: 3, name: this.translateService.instant(this.searchRoleKey) },
        { id: 4, name: this.translateService.instant(this.searchCompetencyKey) }
      ]
    }

  }

  fetchCollabCompetencyAssignmentOverview(): void {
    forkJoin({
      overview: this.competencyAssignmentCollaborationService.getOverviewData(),
    }).subscribe({
      next: ({ overview }) => {
        this.fullDataList = overview;
        this.processDataBuckets();

        this.currentUserId = this.authService.userId;

        this.isLoaded = true;

        const tabIndexParam = this.route.snapshot.queryParamMap.get('tabIndex');
        const proposalIdParam = this.route.snapshot.queryParamMap.get('proposalId');
        const staffIdParam = this.route.snapshot.queryParamMap.get('staffId');
        if (tabIndexParam !== null && tabIndexParam == 'assignment' && proposalIdParam !== null && proposalIdParam !== null) {
          const record = this.fullDataList.find(data =>
            data.id.proposalId.toString() === proposalIdParam
            && data.id.staffId === staffIdParam
          );
          this.viewCompetencyAssignmentCollabComponent.open(record);
        }
      }
    })
  }


  processDataBuckets(): void {
    this.loadingService.show();
    let filtered = this.fullDataList;
    if (this.searchValue) {
      const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";
      filtered = this.fullDataList.filter((item: CompetencyAssignmentProposalOverviewData) => {
        const matchesID = item.id.proposalId.toString().includes(searchVal);
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
        const matchesCompetency = item.assignedCompetencies?.some(competency =>
          competency.id.proposal ? competency.competencyProposal?.name.toLowerCase().includes(searchVal) :
            competency.competency?.name.toLowerCase().includes(searchVal)
        );
        const matchesCollaboratorName = (item.collaboratorName ?? '').toLowerCase().includes(searchVal);
        const matchesCollaboratorEmail = (item.collaboratorEmail).toLowerCase().includes(searchVal);

        if (this.searchField === 0 && this.hasAccess(['CAN_MANAGE_ROLE'])) { // ANY (FULL ACCESS)
          return matchesID || matchesDepartment || matchesRole || matchesCompetency || matchesCollaboratorName || matchesCollaboratorEmail;
        } else if (this.searchField === 0 && !this.hasAccess(['CAN_MANAGE_ROLE'])) { // DEPARTMENT
          return matchesDepartment || matchesRole || matchesCompetency;
        } else if (this.searchField === 1) { // ID
          return matchesID;
        } else if (this.searchField === 2) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 3) { // ROLE
          return matchesRole;
        } else if (this.searchField === 4) { // COMPETENCY
          return matchesCompetency;
        } else if (this.searchField === 5) { // COLLABORATOR NAME
          return matchesCollaboratorName;
        } else if (this.searchField === 6) { // COLLABORATOR EMAIL
          return matchesCollaboratorEmail;
        }
        return false;
      });
    }
    this.loadingService.hide();

    this.reviewList = filtered.filter(item => item.reviewer === true);
    this.proposalList = filtered.filter(item => item.id.staffId == this.authService.userId);

    this.refreshCheckedStatus();
  }

  search(): void {
    this.processDataBuckets();
  }

  resetSearch(): void {
    this.searchValue = '';
    this.processDataBuckets();
  }

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();
  }

  addCollabCompetencyAssignment(): void {
    this.addCompetencyAssignmentCollabComponent.initialize();
    this.addCompetencyAssignmentCollabComponent.open();
  }

  viewRolesCompetencyAssignment(row: CompetencyAssignmentProposalOverviewData) {
    this.viewCompetencyAssignmentCollabComponent.open(row);
  }

  hasNoAccessToReview(): boolean {
    const selecteRoleCompetencyProposalIds = Array.from(this.setOfCheckedId);

    const selecteRoleCompetencyProposal = this.fullDataList
      .filter(c => selecteRoleCompetencyProposalIds.includes(c.id));

    return selecteRoleCompetencyProposal.some(c => !c.reviewer);
  }

  bulkRejectCollabCompetencyAssignment(): void {
    const confirmTitle = this.translateService.instant(this.bulkRejectConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkRejectConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkRejectConfirmOkKey);

    const selectedRoleCompetencyProposalIds = Array.from(this.setOfCheckedId);

    const selectedCompetencyDefinitions = this.fullDataList
      .filter(c => selectedRoleCompetencyProposalIds.includes(c.id));

    const confirmCompetencyAssignment = selectedCompetencyDefinitions
      .map(c => `<li><b>${c.roleName}</b> proposed by <b>${c.collaboratorEmail}</b>`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ol>${confirmCompetencyAssignment}</ol>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyAssignmentCollaborationService
          .bulkRejectCollaboration(selectedRoleCompetencyProposalIds).subscribe({
            complete: () => {
              this.loadList();
            }
          })
      }
    });
  }

  bulkApproveCollabCompetencyAssignment(): void {
    const confirmTitle = this.translateService.instant(this.bulkApproveConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkApproveConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkApproveConfirmOkKey);

    const selectedRoleCompetencyProposalIds = Array.from(this.setOfCheckedId);

    const selectedCompetencyDefinitions = this.fullDataList
      .filter(c => selectedRoleCompetencyProposalIds.includes(c.id));

    const confirmCompetencyAssignment = selectedCompetencyDefinitions
      .map(c => `<li><b>${c.roleName}</b> proposed by <b>${c.collaboratorEmail}</b>`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ol>${confirmCompetencyAssignment}</ol>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyAssignmentCollaborationService
          .bulkApproveCollaboration(selectedRoleCompetencyProposalIds).subscribe({
            complete: () => {
              this.loadList();
            }
          })
      }
    });
  }

  editCollabCompetencyAssignment(row: CompetencyAssignmentProposalOverviewData): void {
    this.editCompetencyAssignmentCollabComponent.initialize(row);
    this.editCompetencyAssignmentCollabComponent.open();
  }

  rejectCollabCompetencyAssignment(row: CompetencyAssignmentProposalOverviewData): void {
    const confirmTitle = this.translateService.instant(this.rejectConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.rejectConfirmContentKey);
    const confirmOk = this.translateService.instant(this.rejectConfirmOkKey);

    const confirmProposal = `<b>${row.roleName}</b> proposed by <b>${row.collaboratorEmail}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmProposal}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyAssignmentCollaborationService.rejectCollaboration(row.id).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  approveCollabCompetencyAssignment(row: CompetencyAssignmentProposalOverviewData): void {
    const confirmTitle = this.translateService.instant(this.approveConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.approveConfirmContentKey);
    const confirmOk = this.translateService.instant(this.approveConfirmOkKey);

    const confirmProposal = `<b>${row.roleName}</b> proposed by <b>${row.collaboratorEmail}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmProposal}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyAssignmentCollaborationService.approveCollaboration(row.id).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  updateCheckedSet(id: RoleCompetencyProposalId, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
  }

  onAllChecked(checked: boolean): void {
    if (this.selectedTabIndex !== 0 || !this.hasAccess(['CAN_MANAGE_ROLE'])) return;

    this.reviewList.forEach(item => {
      if (checked) {
        this.setOfCheckedId.add(item.id);
      } else {
        this.setOfCheckedId.delete(item.id);
      }
    });
    this.refreshCheckedStatus();
  }

  onItemChecked(id: any, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
    this.refreshCheckedStatus();
  }

  refreshCheckedStatus(): void {
    const allEnabled = this.reviewList.length > 0;
    const allChecked = this.reviewList.every(item => this.setOfCheckedId.has(item.id));
    const noneChecked = this.reviewList.every(item => !this.setOfCheckedId.has(item.id));

    this.checked = allEnabled && allChecked;
    this.indeterminate = !allChecked && !noneChecked;
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
