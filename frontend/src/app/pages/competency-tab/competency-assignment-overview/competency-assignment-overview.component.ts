import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCardModule } from 'ng-zorro-antd/card';

import { AddCompetencyAssignmentComponent } from './add-competency-assignment/add-competency-assignment.component';
import { EditCompetencyAssignmentComponent } from './edit-competency-assignment/edit-competency-assignment.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { ViewCompetencyAssignmentComponent } from './view-competency-assignment/view-competencya-assignment.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { CompetencyAssignmentOverviewData } from '../../../models/role-compotency.model';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { RoleCompetencyService } from '../../../services/role-competency.service';
import { HttpErrorResponse } from '@angular/common/http';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-competency-assignment-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule, FileUploadComponent,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, NzToolTipModule, AddCompetencyAssignmentComponent, EditCompetencyAssignmentComponent,
    NzTagModule, NzEmptyModule, NzCardModule, ViewCompetencyAssignmentComponent,
    TablePageSizeSelectorComponent],
  templateUrl: './competency-assignment-overview.component.html',
  styleUrl: './competency-assignment-overview.component.scss'
})
export class CompetencyAssignmentOverviewComponent {
  @ViewChild(AddCompetencyAssignmentComponent) addCompetencyAssignmentComponent!: AddCompetencyAssignmentComponent;
  @ViewChild(EditCompetencyAssignmentComponent) editCompetencyAssignmentComponent!: EditCompetencyAssignmentComponent;
  @ViewChild(ViewCompetencyAssignmentComponent) viewCompetencyAssignmentComponent!: ViewCompetencyAssignmentComponent;
  @ViewChild(FileUploadComponent) fileUploadComponent!: FileUploadComponent;

  selectedFiles: File[] = [];
  readonly maxFileSizeMB: number = 5;
  readonly acceptedFileType: string = '.xlsx'
  readonly acceptedFileExtension: string = 'xlsx'
  private readonly errorTitleKey = 'NOTIFICATION.ERR.TITLE';
  private readonly errorMessageKey = 'NOTIFICATION.ERR.MESSAGE';
  private readonly invalidFileTypeErrorTitleKey = 'COMPONENT.FILE.ERROR.INVALID.TITLE';
  private readonly invalidFileTypeErrorMessageKey = 'COMPONENT.FILE.ERROR.INVALID.MSG';
  private readonly invalidFileSizeErrorTitleKey = 'COMPONENT.FILE.ERROR.LARGE.TITLE';
  private readonly invalidFileSizeErrorMessageKey = 'COMPONENT.FILE.ERROR.LARGE.MSG';

  private readonly titleKey = 'PAGE.COMPETENCY.ASSIGNMENT.TITLE';
  private readonly searchAnyKey = 'PAGE.COMPETENCY.ASSIGNMENT.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.COMPETENCY.ASSIGNMENT.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.COMPETENCY.ASSIGNMENT.SEARCH.FIELD.ROLE';
  private readonly searchCompetencyKey = 'PAGE.COMPETENCY.ASSIGNMENT.SEARCH.FIELD.COMPETENCY';
  private readonly deleteConfirmTitleKey = 'PAGE.COMPETENCY.ASSIGNMENT.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.COMPETENCY.ASSIGNMENT.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.COMPETENCY.ASSIGNMENT.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.COMPETENCY.ASSIGNMENT.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.COMPETENCY.ASSIGNMENT.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.COMPETENCY.ASSIGNMENT.BULK_DELETE.CONFIRM.OK';

  optionList: Option[] = []
  competencyAssignmentOverviewData: CompetencyAssignmentOverviewData[] = []
  listOfDisplayData = [...this.competencyAssignmentOverviewData];
  listOfCurrentPageData: readonly CompetencyAssignmentOverviewData[] = [];

  red = "#E6173F";
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];
  staffColorCodes: string[] = [
    '#d32f2f',
    '#c2185b',
    '#7b1fa2',
    '#512da8',
    '#303f9f',
    '#1976d2',
    '#0288d1',
    '#0097a7',
    '#00796b',
    '#388e3c',
    '#689f38',
    '#afb42b',
    '#fbc02d',
    '#ffa000',
    '#f57c00'
  ];
  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
  pageSize: number = 10;

  confirmModal?: NzModalRef;

  sortByOrgChartName = (a: any, b: any) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByRoleName = (a: any, b: any) =>
    a.roleName.localeCompare(b.roleName);

  constructor(private translateService: TranslateService, private title: Title,
    private roleCompetencyService: RoleCompetencyService, private authService: AuthService,
    private loadingService: LoadingService, private modal: NzModalService
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchCompetencyAssignmentOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchRoleKey) },
      { id: 3, name: this.translateService.instant(this.searchCompetencyKey) },
    ]
  }

  fetchCompetencyAssignmentOverview(): void {
    this.roleCompetencyService.getRoleCompetencyOverview().subscribe({
      next: (res) => {
        this.competencyAssignmentOverviewData = res;
        this.competencyAssignmentOverviewData = res
          .sort((a, b) => {
            const deptCompare = a.orgChartName.localeCompare(b.orgChartName);
            if (deptCompare !== 0) return deptCompare;
            return a.roleName.localeCompare(b.roleName);
          })
          .map(item => ({
            ...item,
            competencyAssignments: [...item.competencyAssignments].sort(
              (a, b) => b.weightage - a.weightage
            )
          }));

        this.listOfDisplayData = this.competencyAssignmentOverviewData;
        this.isLoaded = true;
      }
    })
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.competencyAssignmentOverviewData];
    } else {
      this.listOfDisplayData = this.competencyAssignmentOverviewData.filter(
        (item: CompetencyAssignmentOverviewData) => {
          const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
          const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
          const matchesCompetency = item.competencyAssignments?.some(competency =>
            competency.competencyName?.toLowerCase().includes(searchVal)
          );

          if (this.searchField === 0) { // ANY
            return matchesDepartment || matchesRole || matchesCompetency;
          } else if (this.searchField === 1) { // DEPARTMENT
            return matchesDepartment;
          } else if (this.searchField === 2) { // ROLE
            return matchesRole;
          } else if (this.searchField === 3) { // COMPETENCY NAME
            return matchesCompetency;
          }
          return false;
        });
    }

    this.loadingService.hide();
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }

  addCompetencyAssignment(): void {
    this.addCompetencyAssignmentComponent.initialize();
    this.addCompetencyAssignmentComponent.open();
  }

  bulkDeleteCompetencyAssignment(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedRoleIds = Array.from(this.setOfCheckedId);

    const selectedRoles = this.competencyAssignmentOverviewData
      .filter(r => selectedRoleIds.includes(r.roleId));

    const confirmRoleCompetency = selectedRoles
      .map(r => `<li>${r.roleName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmRoleCompetency}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleCompetencyService.bulkDeleteRoleCompetency(selectedRoleIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editCompetencyAssignment(row: CompetencyAssignmentOverviewData): void {
    this.editCompetencyAssignmentComponent.initialize(row);
    this.editCompetencyAssignmentComponent.open();
  }

  viewCompetencyAssignment(row: CompetencyAssignmentOverviewData): void {
    this.viewCompetencyAssignmentComponent.initialize(row);
    this.viewCompetencyAssignmentComponent.open();
  }

  deleteCompetencyAssignment(row: CompetencyAssignmentOverviewData): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmRoleCompetency = `<b>${row.roleName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmRoleCompetency}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleCompetencyService.deleteRoleCompetency(row.roleId).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  updateCheckedSet(id: number, checked: boolean): void {
    if (checked) {
      this.setOfCheckedId.add(id);
    } else {
      this.setOfCheckedId.delete(id);
    }
  }

  refreshCheckedStatus(): void {
    const listOfEnabledData = this.listOfCurrentPageData;

    if (listOfEnabledData.length === 0) {
      this.checked = false;
      this.indeterminate = false;
      return;
    }

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.roleId));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.roleId)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly CompetencyAssignmentOverviewData[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.roleId, value));
    this.refreshCheckedStatus();
  }

  openFileSelector(): void {
    this.fileUploadComponent.open();
  }

  importData(): void {
    if (this.fileUploadComponent.selectedFiles.length == 1) {
      const file = this.fileUploadComponent.selectedFiles.at(0)!;

      const maxSizeBytes = this.maxFileSizeMB * 1024 * 1024;
      const validExtensions = [this.acceptedFileExtension];

      const fileName = file.name.toLowerCase();
      const fileExtension = fileName.split('.').pop()!;
      const isValidExtension = validExtensions.includes(fileExtension);
      const isValidType =
        file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
        file.type === 'application/octet-stream' ||
        file.type === '';

      if (file.size > maxSizeBytes) {
        this.fileUploadComponent.errorTitle = this.translateService.instant(this.invalidFileSizeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translateService.instant(this.invalidFileSizeErrorMessageKey);
        return;
      }
      if (!isValidExtension || !isValidType) {
        this.fileUploadComponent.errorTitle = this.translateService.instant(this.invalidFileTypeErrorTitleKey);
        this.fileUploadComponent.errorMessage = this.translateService.instant(this.invalidFileTypeErrorMessageKey);
        return;
      }

      this.loadingService.show();
      this.fileUploadComponent.isUploading = true;
      this.roleCompetencyService.import(file).subscribe({
        next: () => {
          this.loadList()
          this.fileUploadComponent.close();
        },
        error: (error: HttpErrorResponse) => {
          let errorTitle = this.translateService.instant(this.errorTitleKey);
          let errorMessage = this.translateService.instant(this.errorMessageKey);

          if (error.error?.message) {
            errorMessage = error.error?.message;
          }

          if (error.error?.title) {
            errorTitle = error.error?.title;
          }

          this.fileUploadComponent.errorMessage = errorMessage;
          this.fileUploadComponent.errorTitle = errorTitle;
          this.fileUploadComponent.isUploading = false;
          this.loadingService.hide();
        },
        complete: () => {
          this.fileUploadComponent.isUploading = false;
          this.loadingService.hide();
        }
      });
    }
  }

  export(): void {
    if (this.hasAccess(['CAN_MANAGE_ROLE'])) {
      this.roleCompetencyService.export().subscribe((response) => {
        const blob = response.body!;
        const contentDisposition = response.headers.get('content-disposition');
        const filename = contentDisposition?.split('filename=')[1]?.replace(/"/g, '') || 'download.xlsx';

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        window.URL.revokeObjectURL(url);
      });
    }
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
