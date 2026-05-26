import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { AddRoleAssignmentComponent } from './add-role-assignment/add-role-assignment.component';
import { EditRoleAssignmentComponent } from './edit-role-assignment/edit-role-assignment.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { ViewRoleAssignmentComponent } from './view-role-assignment/view-role-assignment.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { RoleAssignmentService } from '../../../services/role-assignment.service';
import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';

import { RoleAssignmentOverviewData } from '../../../models/staff-role.model';

interface Option {
  id: number;
  name: string;
}
@Component({
  selector: 'app-role-assignment-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, NzToolTipModule, AddRoleAssignmentComponent, EditRoleAssignmentComponent,
    FileUploadComponent, NzEmptyModule, NzGridModule, NzCardModule, NzTagModule,
    ViewRoleAssignmentComponent, TablePageSizeSelectorComponent],
  templateUrl: './role-assignment-overview.component.html',
  styleUrl: './role-assignment-overview.component.scss'
})

export class RoleAssignmentOverviewComponent {
  @ViewChild(AddRoleAssignmentComponent) addRoleAssignmentComponent!: AddRoleAssignmentComponent;
  @ViewChild(EditRoleAssignmentComponent) editRoleAssignmentComponent!: EditRoleAssignmentComponent;
  @ViewChild(ViewRoleAssignmentComponent) viewRoleAssignmentComponent!: ViewRoleAssignmentComponent;
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

  private readonly titleKey = 'PAGE.ROLE.ASSIGNMENT.TITLE';
  private readonly searchAnyKey = 'PAGE.ROLE.ASSIGNMENT.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.ROLE.ASSIGNMENT.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.ROLE.ASSIGNMENT.SEARCH.FIELD.ROLE';
  private readonly searchStaffNameKey = 'PAGE.ROLE.ASSIGNMENT.SEARCH.FIELD.STAFF.NAME';
  private readonly searchStaffEmailAddressKey = 'PAGE.ROLE.ASSIGNMENT.SEARCH.FIELD.STAFF.EMAIL';
  private readonly deleteConfirmTitleKey = 'PAGE.ROLE.ASSIGNMENT.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.ROLE.ASSIGNMENT.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.ROLE.ASSIGNMENT.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.ROLE.ASSIGNMENT.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.ROLE.ASSIGNMENT.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.ROLE.ASSIGNMENT.BULK_DELETE.CONFIRM.OK';

  red = "#E6173F";
  optionList: Option[] = []
  roleAssignmentOverviewData: RoleAssignmentOverviewData[] = []
  listOfDisplayData = [...this.roleAssignmentOverviewData];
  listOfCurrentPageData: readonly RoleAssignmentOverviewData[] = [];
  colorCodes: string[] = [
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
  pageSize: number = 10;

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;

  confirmModal?: NzModalRef;

  sortByOrgChartName = (a: any, b: any) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByRoleName = (a: any, b: any) =>
    a.roleName.localeCompare(b.roleName);

  constructor(private translateService: TranslateService, private title: Title,
    private roleAssignmentService: RoleAssignmentService, private authService: AuthService,
    private loadingService: LoadingService, private modal: NzModalService,
    private router: Router,
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchRoleAssignmentOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchRoleKey) },
      { id: 3, name: this.translateService.instant(this.searchStaffNameKey) },
      { id: 4, name: this.translateService.instant(this.searchStaffEmailAddressKey) },
    ]
  }

  fetchRoleAssignmentOverview(): void {
    this.roleAssignmentService.getRoleAssignmentOverview().subscribe({
      next: (res) => {
        this.roleAssignmentOverviewData = res;

        this.roleAssignmentOverviewData.sort((a, b) => {
          const sortByOrgChartName = a.orgChartName.localeCompare(b.orgChartName);
          const sortByRoleName = a.roleName.localeCompare(b.roleName)

          if (sortByOrgChartName != 0) return sortByOrgChartName;
          if (sortByRoleName != 0) return sortByRoleName;
          return a.roleId - b.roleId;
        });

        this.listOfDisplayData = this.roleAssignmentOverviewData;
        this.isLoaded = true;
      }
    })
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.roleAssignmentOverviewData];
    } else {
      this.listOfDisplayData = this.roleAssignmentOverviewData.filter((item: RoleAssignmentOverviewData) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
        const matchesStaffName = item.staffList?.some(staff =>
          (staff.name ?? '').toLocaleLowerCase().includes(searchVal)
        );
        const matchesStaffEmail = item.staffList?.some(staff =>
          (staff.email).toLocaleLowerCase().includes(searchVal)
        );

        if (this.searchField === 0) { // ANY
          return matchesDepartment || matchesRole || matchesStaffName || matchesStaffEmail;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // ROLE
          return matchesRole;
        } else if (this.searchField === 3) { // STAFF NAME
          return matchesStaffName;
        } else if (this.searchField === 4) { // STAFF EMAIL ADDRESS
          return matchesStaffEmail;
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

  addRoleAssignment(): void {
    this.addRoleAssignmentComponent.initialize();
    this.addRoleAssignmentComponent.open();
  }

  bulkDeleteRoleAssignment(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedRoleIds = Array.from(this.setOfCheckedId);

    const selectedRoles = this.roleAssignmentOverviewData.filter(r => selectedRoleIds.includes(r.roleId));

    const confirmRoleAssignment = selectedRoles
      .map(r => `<li>${r.roleName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmRoleAssignment}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleAssignmentService.bulkDeleteRole(selectedRoleIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editRoleAssignment(row: RoleAssignmentOverviewData): void {
    this.editRoleAssignmentComponent.initialize(row);
    this.editRoleAssignmentComponent.open();
  }

  viewRoleAssignment(row: RoleAssignmentOverviewData): void {
    this.viewRoleAssignmentComponent.initialize(row);
    this.viewRoleAssignmentComponent.open();
  }

  deleteRoleAssignment(row: RoleAssignmentOverviewData): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmRoleAssignment = `<b>${row.roleName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmRoleAssignment}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleAssignmentService.deleteRoleAssignment(row.roleId).subscribe({
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

  onCurrentPageDataChange(listOfCurrentPageData: readonly RoleAssignmentOverviewData[]): void {
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
      this.roleAssignmentService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_ROLE', 'CAN_MANAGE_STAFF'])) {
      this.roleAssignmentService.export().subscribe((response) => {
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
