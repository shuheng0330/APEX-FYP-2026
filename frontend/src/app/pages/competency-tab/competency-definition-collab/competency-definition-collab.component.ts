import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzSpaceModule } from 'ng-zorro-antd/space';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';

import { AddCompetencyDefinitionCollabComponent } from './add-competency-definition-collab/add-competency-definition-collab.component';
import { EditCompetencyDefinitionCollabComponent } from './edit-competency-definition-collab/edit-competency-definition-collab.component';
import { ViewCompetencyDefinitionCollabComponent } from './view-competency-definition-collab/view-competency-definition-collab.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { CompetencyDefinitionCollaborationService } from '../../../services/competency-definition-collaboration.service';

import { CompetencyCollaborationOverviewData, ProposalParticipantId } from '../../../models/competency-collaboration.model';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-competency-definition-collab',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, NzSpaceModule, AddCompetencyDefinitionCollabComponent, NzTagModule,
    EditCompetencyDefinitionCollabComponent, ViewCompetencyDefinitionCollabComponent,
    NzAvatarModule, NzToolTipModule, NzGridModule, TablePageSizeSelectorComponent,
    NzCardModule, NzEmptyModule, NzTabsModule, NzBadgeModule, NzCheckboxModule],
  templateUrl: './competency-definition-collab.component.html',
  styleUrl: './competency-definition-collab.component.scss'
})
export class CompetencyDefinitionCollabComponent {
  @ViewChild(AddCompetencyDefinitionCollabComponent) addCompetencyDefinitionCollabComponent!: AddCompetencyDefinitionCollabComponent;
  @ViewChild(EditCompetencyDefinitionCollabComponent) editCompetencyDefinitionCollabComponent!: EditCompetencyDefinitionCollabComponent;
  @ViewChild(ViewCompetencyDefinitionCollabComponent) viewCompetencyDefinitionCollabComponent!: ViewCompetencyDefinitionCollabComponent;

  private readonly titleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.TITLE';
  private readonly searchAnyKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.ANY';
  private readonly searchIdKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.ID';
  private readonly searchCollabCompetencyNameKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.NAME';
  private readonly searchDescriptionKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.DESC';
  private readonly searchTagKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.TAG';
  private readonly searchCollaboratorNameKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.COLLABORATOR.NAME';
  private readonly searchCollaboratorEmailKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.SEARCH.FIELD.COLLABORATOR.EMAIL';
  private readonly rejectConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.REJECT.CONFIRM.TITLE';
  private readonly rejectConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.REJECT.CONFIRM.CONTENT';
  private readonly rejectConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.REJECT.CONFIRM.OK';
  private readonly bulkRejectConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_REJECT.CONFIRM.TITLE';
  private readonly bulkRejectConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_REJECT.CONFIRM.CONTENT';
  private readonly bulkRejectConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_REJECT.CONFIRM.OK';
  private readonly approveConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.APPROVE.CONFIRM.TITLE';
  private readonly approveConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.APPROVE.CONFIRM.CONTENT';
  private readonly approveConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.APPROVE.CONFIRM.OK';
  private readonly bulkApproveConfirmTitleKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_APPROVE.CONFIRM.TITLE';
  private readonly bulkApproveConfirmContentKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_APPROVE.CONFIRM.CONTENT';
  private readonly bulkApproveConfirmOkKey = 'PAGE.COMPETENCY.COLLAB.COMPETENCY.BULK_APPROVE.CONFIRM.OK';

  optionList: Option[] = []
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  setOfCheckedId = new Set<ProposalParticipantId>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  searchValue?: string;
  searchField: number = 0;

  fullDataList: CompetencyCollaborationOverviewData[] = [];
  reviewList: CompetencyCollaborationOverviewData[] = []; // Tab 1
  proposalList: CompetencyCollaborationOverviewData[] = []; // Tab 2
  selectedTabIndex = 0;

  confirmModal?: NzModalRef;

  currentUserId: string | null = null;

  constructor(private translateService: TranslateService, private title: Title,
    private competencyDefinitionCollaborationService: CompetencyDefinitionCollaborationService,
    private authService: AuthService, private loadingService: LoadingService, private modal: NzModalService,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchCollabCompetencyOverview();
  }

  loadOptionList(): void {
    if (this.hasAccess(['CAN_MANAGE_COMPETENCY'])) {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 1, name: this.translateService.instant(this.searchIdKey) },
        { id: 2, name: this.translateService.instant(this.searchCollabCompetencyNameKey) },
        { id: 3, name: this.translateService.instant(this.searchDescriptionKey) },
        { id: 4, name: this.translateService.instant(this.searchTagKey) },
        { id: 5, name: this.translateService.instant(this.searchCollaboratorNameKey) },
        { id: 6, name: this.translateService.instant(this.searchCollaboratorEmailKey) }
      ]
    } else {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 2, name: this.translateService.instant(this.searchCollabCompetencyNameKey) },
        { id: 3, name: this.translateService.instant(this.searchDescriptionKey) },
        { id: 4, name: this.translateService.instant(this.searchTagKey) }
      ]
    }
  }

  fetchCollabCompetencyOverview(): void {
    forkJoin({
      overviewData: this.competencyDefinitionCollaborationService.getOverviewData()
    }).subscribe({
      next: ({ overviewData }) => {
        this.fullDataList = overviewData;
        this.processDataBuckets();

        this.currentUserId = this.authService.userId;
        this.isLoaded = true;

        const tabIndexParam = this.route.snapshot.queryParamMap.get('tabIndex');
        const proposalIdParam = this.route.snapshot.queryParamMap.get('proposalId');
        const staffIdParam = this.route.snapshot.queryParamMap.get('staffId');
        if (tabIndexParam !== null && tabIndexParam == 'definition' && proposalIdParam !== null && proposalIdParam !== null) {
          this.viewProposalDetails(proposalIdParam, staffIdParam!);
        }
      }
    })
  }

  processDataBuckets(): void {
    this.loadingService.show();
    let filtered = this.fullDataList;
    if (this.searchValue) {
      const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";
      filtered = this.fullDataList.filter((item: CompetencyCollaborationOverviewData) => {
        const matchId = item.proposalParticipantId.proposalId.toString().includes(searchVal);
        const matchesCompetencyName = item.competencyName?.toLowerCase().includes(searchVal);
        const matchesCompetencyDescription = item.competencyDescription?.toLowerCase().includes(searchVal);
        const matchesCollaboratorName = item.collaboratorName?.toLowerCase().includes(searchVal);
        const matchesCollaboratorEmail = item.collaboratorEmail?.toLowerCase().includes(searchVal);
        const matchesTag = item.assignedCompTags?.some(compTag =>
          compTag.tag?.toLowerCase().includes(searchVal)
        );

        if (this.searchField === 0 && this.hasAccess(['CAN_MANAGE_COMPETENCY'])) { // ANY (FULL ACCESS)
          return matchId || matchesCompetencyName || matchesCompetencyDescription || matchesCollaboratorName
            || matchesCollaboratorEmail || matchesTag;
        } else if (this.searchField === 0 && !this.hasAccess(['CAN_MANAGE_COMPETENCY'])) { // ANY
          return matchesCompetencyName || matchesCompetencyDescription || matchesTag;
        } else if (this.searchField === 1 && this.hasAccess(['CAN_MANAGE_COMPETENCY'])) { // ID
          return matchId;
        } else if (this.searchField === 2) { // COMPETENCY NAME
          return matchesCompetencyName;
        } else if (this.searchField === 3) { // COMPETENCY DESC
          return matchesCompetencyDescription;
        } else if (this.searchField === 4) { // TAG
          return matchesTag;
        } else if (this.searchField === 5) { // STAFF NAME
          return matchesCollaboratorName;
        } else if (this.searchField === 6) { // STAFF EMAIL ADDRESS
          return matchesCollaboratorEmail;
        }
        return false;
      });
    }
    this.loadingService.hide();

    this.reviewList = filtered.filter(item => item.reviewer === true);
    this.proposalList = filtered.filter(item => item.proposalParticipantId.staffId == this.authService.userId);

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

  viewProposalDetails(proposalId: string, staffId: string): void {
    const record = this.fullDataList.find(data =>
      data.proposalParticipantId.proposalId.toString() === proposalId
      && data.proposalParticipantId.staffId === staffId
    );

    this.viewCompetencyDefinitionCollabComponent.open(record);
  }

  addCollabCompetency(): void {
    this.addCompetencyDefinitionCollabComponent.initialize();
    this.addCompetencyDefinitionCollabComponent.open();
  }

  hasNoAccessToReview(): boolean {
    const selectedProposalParticipantIds = Array.from(this.setOfCheckedId);

    const selectedCompetencyDefinitions = this.fullDataList
      .filter(c => selectedProposalParticipantIds.includes(c.proposalParticipantId));

    return selectedCompetencyDefinitions.some(c => !c.reviewer);
  }

  bulkRejectCollabCompetency(): void {
    const confirmTitle = this.translateService.instant(this.bulkRejectConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkRejectConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkRejectConfirmOkKey);

    const selectedProposalParticipantIds = Array.from(this.setOfCheckedId);

    const selectedCompetencyDefinitions = this.fullDataList
      .filter(c => selectedProposalParticipantIds.includes(c.proposalParticipantId));

    const confirmCompetencyDefinition = selectedCompetencyDefinitions
      .map(c => `<li><b>${c.competencyName}</b> proposed by <b>${c.collaboratorEmail}</b>`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ol>${confirmCompetencyDefinition}</ol>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyDefinitionCollaborationService
          .bulkRejectCollaboration(selectedProposalParticipantIds).subscribe({
            complete: () => {
              this.loadList();
            }
          })
      }
    });

  }

  bulkApproveCollabCompetency(): void {
    const confirmTitle = this.translateService.instant(this.bulkApproveConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkApproveConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkApproveConfirmOkKey);

    const selectedProposalParticipantIds = Array.from(this.setOfCheckedId);

    const selectCompetencyDefinitions = this.fullDataList
      .filter(c => selectedProposalParticipantIds.includes(c.proposalParticipantId));

    const confirmCompetencyDefinition = selectCompetencyDefinitions
      .map(c => `<li><b>${c.competencyName}</b> proposed by <b>${c.collaboratorEmail}</b>`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ol>${confirmCompetencyDefinition}</ol>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyDefinitionCollaborationService
          .bulkApproveCollaboration(selectedProposalParticipantIds).subscribe({
            complete: () => {
              this.loadList();
            }
          })
      }
    });
  }

  editCollabCompetency(row: CompetencyCollaborationOverviewData): void {
    this.editCompetencyDefinitionCollabComponent.initialize(row);
    this.editCompetencyDefinitionCollabComponent.open();
  }

  rejectCollabCompetency(row: CompetencyCollaborationOverviewData): void {
    const confirmTitle = this.translateService.instant(this.rejectConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.rejectConfirmContentKey);
    const confirmOk = this.translateService.instant(this.rejectConfirmOkKey);

    const confirmCompetencyName = `<b>${row.competencyName}</b> proposed by <b>${row.collaboratorEmail}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCompetencyName}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyDefinitionCollaborationService.rejectCollaboration(row.proposalParticipantId).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  approveCollabCompetency(row: CompetencyCollaborationOverviewData): void {
    const confirmTitle = this.translateService.instant(this.approveConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.approveConfirmContentKey);
    const confirmOk = this.translateService.instant(this.approveConfirmOkKey);

    const confirmCompetencyName = `<b>${row.competencyName}</b> proposed by <b>${row.collaboratorEmail}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCompetencyName}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyDefinitionCollaborationService.approveCollaboration(row.proposalParticipantId).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  updateCheckedSet(id: ProposalParticipantId, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
  }

  onAllChecked(checked: boolean): void {
    if (this.selectedTabIndex !== 0 || !this.hasAccess(['CAN_MANAGE_COMPETENCY'])) return;

    this.reviewList.forEach(item => {
      if (checked) {
        this.setOfCheckedId.add(item.proposalParticipantId);
      } else {
        this.setOfCheckedId.delete(item.proposalParticipantId);
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
    const allChecked = this.reviewList.every(item => this.setOfCheckedId.has(item.proposalParticipantId));
    const noneChecked = this.reviewList.every(item => !this.setOfCheckedId.has(item.proposalParticipantId));

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
