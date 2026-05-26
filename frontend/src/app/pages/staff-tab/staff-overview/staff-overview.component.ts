import { Component, OnInit, ViewChild } from '@angular/core';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
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

import { AuthService } from '../../../services/auth.service';

import { AddStaffComponent } from './add-staff/add-staff.component';
import { EditStaffComponent } from './edit-staff/edit-staff.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { StaffOverviewData } from '../../../models/staff.model';
import { StaffService } from '../../../services/staff.service';
import { LoadingService } from '../../../services/loading.service';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-staff-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule, FileUploadComponent,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, AddStaffComponent, EditStaffComponent, NzToolTipModule, NzTagModule,
    TablePageSizeSelectorComponent],
  templateUrl: './staff-overview.component.html',
  styleUrl: './staff-overview.component.scss'
})
export class StaffOverviewComponent {
  @ViewChild(AddStaffComponent) addStaffComponent!: AddStaffComponent;
  @ViewChild(EditStaffComponent) editStaffComponent!: EditStaffComponent;
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

  private readonly titleKey = "PAGE.STAFF.STAFF_OVERVIEW.TITLE"
  private readonly searchAnyKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchStaffNameKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.NAME';
  private readonly searchStaffEmailKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.EMAIL';
  private readonly searchDepartmentKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.ROLE';
  private readonly searchCareerPathwayKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.CAREER_PATHWAY';
  private readonly searchManagerKey = 'PAGE.STAFF.STAFF_OVERVIEW.SEARCH.FIELD.MANAGER';
  private readonly toggleConfirmTitleKey = 'PAGE.STAFF.STAFF_OVERVIEW.TOGGLE.CONFIRM.TITLE';
  private readonly toggleConfirmContentKey = 'PAGE.STAFF.STAFF_OVERVIEW.TOGGLE.CONFIRM.CONTENT';
  private readonly toggleConfirmOkKey = 'PAGE.STAFF.STAFF_OVERVIEW.TOGGLE.CONFIRM.OK';
  private readonly deleteConfirmTitleKey = 'PAGE.STAFF.STAFF_OVERVIEW.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.STAFF.STAFF_OVERVIEW.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.STAFF.STAFF_OVERVIEW.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.STAFF.STAFF_OVERVIEW.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.STAFF.STAFF_OVERVIEW.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.STAFF.STAFF_OVERVIEW.BULK_DELETE.CONFIRM.OK';
  private readonly profilePage = '/profile'

  overviewData: StaffOverviewData[] = []

  listOfCurrentPageData: readonly StaffOverviewData[] = [];
  listOfDisplayData = [...this.overviewData];
  optionList: Option[] = [];

  red = "#E6173F";
  setOfCheckedId = new Set<string>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
  pageSize: number = 10;

  sortByAccountStatus = (a: StaffOverviewData, b: StaffOverviewData) =>
    Number(a.accountStatus) - Number(b.accountStatus);

  sortByName = (a: StaffOverviewData, b: StaffOverviewData) =>
    (a.staffName ?? '').localeCompare(b.staffName ?? '');

  sortByEmail = (a: StaffOverviewData, b: StaffOverviewData) =>
    a.staffEmail.localeCompare(b.staffEmail);

  sortByOrgChartName = (a: StaffOverviewData, b: StaffOverviewData) =>
    (a.orgChartName ?? '').localeCompare(b.orgChartName ?? '');

  sortByRoleName = (a: StaffOverviewData, b: StaffOverviewData) =>
    (a.roleName ?? '').localeCompare(b.roleName ?? '');

  sortByCareerPathwayName = (a: StaffOverviewData, b: StaffOverviewData) =>
    (a.careerPathwayName ?? '').localeCompare(b.careerPathwayName ?? '');

  sortByManagerName = (a: StaffOverviewData, b: StaffOverviewData) =>
    (a.managerName ?? '').localeCompare(b.managerName ?? '');

  confirmModal?: NzModalRef;

  constructor(private translateService: TranslateService, private title: Title,
    private modal: NzModalService, private authService: AuthService, private staffService: StaffService,
    private loadingService: LoadingService, private router: Router
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchStaffOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchStaffNameKey) },
      { id: 2, name: this.translateService.instant(this.searchStaffEmailKey) },
      { id: 3, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 4, name: this.translateService.instant(this.searchRoleKey) },
      { id: 5, name: this.translateService.instant(this.searchCareerPathwayKey) },
      { id: 6, name: this.translateService.instant(this.searchManagerKey) },
    ]
  }

  fetchStaffOverview(): void {
    this.staffService.getStaffOverview().subscribe({
      next: (res) => {
        let staffs = res;

        this.overviewData = staffs.sort((a, b) => {
          const nameCompare = (a.staffName ?? '').localeCompare(b.staffName ?? '');
          if (nameCompare !== 0) return nameCompare;
          return (a.staffEmail ?? '').localeCompare(b.staffEmail ?? '');
        });

        this.listOfDisplayData = this.overviewData;
        this.isLoaded = true;
      }
    });
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.overviewData];
    } else {
      this.listOfDisplayData = this.overviewData.filter((item: StaffOverviewData) => {
        const matchesStaffName = item.staffName?.toLocaleLowerCase().includes(searchVal);
        const matchesStaffEmail = item.staffEmail?.toLocaleLowerCase().includes(searchVal);
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
        const matchesCareerPathway = item.careerPathwayName?.toLowerCase().includes(searchVal);
        const matchesManagerName = item.managerName?.toLowerCase().includes(searchVal);
        const matchesManagerEmail = item.managerEmail?.toLowerCase().includes(searchVal);

        if (this.searchField === 0) { // ANY
          return matchesStaffName || matchesStaffEmail || matchesDepartment || matchesRole
            || matchesCareerPathway || matchesManagerName || matchesManagerEmail;
        } else if (this.searchField === 1) { // STAFF NAME
          return matchesStaffName;
        } else if (this.searchField === 2) { // STAFF EMAIL
          return matchesStaffEmail;
        } else if (this.searchField === 3) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 4) { // ROLE
          return matchesRole;
        } else if (this.searchField === 5) { // CAREER PATHWAY
          return matchesCareerPathway;
        } else if (this.searchField === 6) { // MANAGER
          return matchesManagerName || matchesManagerEmail;
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

  viewStaff(data: StaffOverviewData): void {
    const staffId = data.staffId
    this.router.navigate([this.profilePage], { queryParams: { staffId } });
  }

  addStaff(): void {
    this.addStaffComponent.initialize();
    this.addStaffComponent.open();
  }

  bulkDeleteStaff(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedStaffIds = Array.from(this.setOfCheckedId);

    const selectedStaffs = this.overviewData.filter(s => selectedStaffIds.includes(s.staffId));

    const confrimStaff = selectedStaffs
      .map(s => {
        if (s.staffName) {
          `<li>${s.staffEmail} (${s.staffName})`
        } else {
          `<li>${s.staffEmail}`
        }
      })
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confrimStaff}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffService.bulkDeleteStaff(selectedStaffIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editStaff(row: StaffOverviewData): void {
    this.editStaffComponent.initialize(row);
    this.editStaffComponent.open();
  }

  deleteStaff(row: StaffOverviewData): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    let staffConfirm = `${row.staffEmail}`;
    if (row.staffName) {
      staffConfirm += `<br/>(${row.staffName})`;
    }

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ul><li>${staffConfirm}</li></ul></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffService.deleteStaff(row.staffId).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  updateCheckedSet(id: string, checked: boolean): void {
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

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.staffId));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.staffId)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly StaffOverviewData[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: string, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.staffId, value));
    this.refreshCheckedStatus();
  }

  toggleAccountStatus(row: StaffOverviewData): void {
    const toggleConfirmTitle = this.translateService.instant(this.toggleConfirmTitleKey);
    const toggleConfirmContent = this.translateService.instant(this.toggleConfirmContentKey);
    const toggleConfirmOk = this.translateService.instant(this.toggleConfirmOkKey);

    let staffConfirm = `${row.staffEmail}`;
    if (row.staffName) {
      staffConfirm += `<br/>(${row.staffName})`;
    }

    const accountStatus = !row.accountStatus;

    this.confirmModal = this.modal.confirm({
      nzTitle: toggleConfirmTitle,
      nzContent: `${toggleConfirmContent}<br/><br/><ul><b><li>${staffConfirm}</li></b></ul>`,
      nzOkText: toggleConfirmOk,
      nzOnOk: () => {
        this.isSwitchLoading = true;
        this.staffService.toggleAccountStatus(row.staffId, accountStatus).subscribe({
          complete: () => {
            this.loadList();
            setTimeout(() => {
              this.isSwitchLoading = false;
            }, 500);
          }
        })
      }
    });
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
      this.staffService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_STAFF'])) {
      this.staffService.export().subscribe((response) => {
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
