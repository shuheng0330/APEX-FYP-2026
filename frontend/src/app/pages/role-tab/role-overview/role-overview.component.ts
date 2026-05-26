import { Component, OnInit, ViewChild } from '@angular/core';
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
import { NzGridModule } from 'ng-zorro-antd/grid';

import { AddRoleComponent } from './add-role/add-role.component';
import { EditRoleComponent } from './edit-role/edit-role.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { ViewRoleComponent } from './view-role/view-role.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { RoleService } from '../../../services/role.service';
import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';

import { RoleOverview } from '../../../models/role.model';
import { HttpErrorResponse } from '@angular/common/http';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-role-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, AddRoleComponent, EditRoleComponent, NzToolTipModule, FileUploadComponent,
    NzTagModule, NzEmptyModule, NzGridModule, ViewRoleComponent,
    TablePageSizeSelectorComponent],
  templateUrl: './role-overview.component.html',
  styleUrl: './role-overview.component.scss'
})
export class RoleOverviewComponent implements OnInit {
  @ViewChild(AddRoleComponent) addRoleComponent!: AddRoleComponent;
  @ViewChild(EditRoleComponent) editRoleComponent!: EditRoleComponent;
  @ViewChild(ViewRoleComponent) viewRoleComponent!: ViewRoleComponent;
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

  private readonly titleKey = 'PAGE.ROLE.OVERVIEW.TITLE';
  private readonly searchAnyKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.ROLE';
  private readonly searchDescKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.DESC';
  private readonly searchJobScopeKey = 'PAGE.ROLE.OVERVIEW.SEARCH.FIELD.JOB_SCOPE';
  private readonly toggleConfirmTitleKey = 'PAGE.ROLE.OVERVIEW.TOGGLE.CONFIRM.TITLE';
  private readonly toggleConfirmContentKey = 'PAGE.ROLE.OVERVIEW.TOGGLE.CONFIRM.CONTENT';
  private readonly toggleConfirmOkKey = 'PAGE.ROLE.OVERVIEW.TOGGLE.CONFIRM.OK';
  private readonly deleteConfirmTitleKey = 'PAGE.ROLE.OVERVIEW.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.ROLE.OVERVIEW.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.ROLE.OVERVIEW.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.ROLE.OVERVIEW.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.ROLE.OVERVIEW.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.ROLE.OVERVIEW.BULK_DELETE.CONFIRM.OK';

  orgChartRoleJobScopeList: RoleOverview[] = [];
  listOfCurrentPageData: readonly RoleOverview[] = [];
  listOfDisplayData = [...this.orgChartRoleJobScopeList];
  optionList: Option[] = []

  red = "#E6173F";
  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
  pageSize: number = 10;

  confirmModal?: NzModalRef;

  sortByVisibility = (a: any, b: any) =>
    Number(a.visible) - Number(b.visible);

  sortByOrgChartName = (a: any, b: any) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByRoleName = (a: any, b: any) =>
    a.roleName.localeCompare(b.roleName);

  constructor(private translateService: TranslateService, private title: Title,
    private roleService: RoleService, private authService: AuthService,
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
    this.fetchRoleOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchRoleKey) },
      { id: 3, name: this.translateService.instant(this.searchDescKey) },
      { id: 4, name: this.translateService.instant(this.searchJobScopeKey) },
    ]
  }

  fetchRoleOverview(): void {
    this.roleService.getRoleOverview().subscribe({
      next: (res) => {

        let roles = res;
        if (!(this.authService.hasRole('CAN_VIEW_INVISIBLE_ROLE') ||
          this.authService.hasRole('CAN_MANAGE_ROLE'))) {
          roles = roles.filter(role => role.visible === true)
        }

        this.orgChartRoleJobScopeList = roles.sort((a, b) => {
          const deptCompare = a.orgChartName.localeCompare(b.orgChartName);
          if (deptCompare !== 0) return deptCompare;
          return a.roleName.localeCompare(b.roleName);
        });

        this.listOfDisplayData = this.orgChartRoleJobScopeList;

        this.isLoaded = true;
      }
    })
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.orgChartRoleJobScopeList];
    } else {
      this.listOfDisplayData = this.orgChartRoleJobScopeList.filter((item: RoleOverview) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
        const matchesDescription = item.description?.toLowerCase().includes(searchVal);
        const matchesJobScope = item.assignedJobScopes?.some(jobScope =>
          jobScope.jobScope?.toLowerCase().includes(searchVal)
        );

        if (this.searchField === 0) { // ANY
          return matchesDepartment || matchesRole || matchesDescription || matchesJobScope;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // ROLE
          return matchesRole;
        } else if (this.searchField === 3) { // DESCRIPTION
          return matchesDescription;
        } else if (this.searchField === 4) { // JOBSCOPE
          return matchesJobScope;
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


  addRole(): void {
    this.addRoleComponent.initialize();
    this.addRoleComponent.open();
  }

  bulkDeleteRole(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedRoleIds = Array.from(this.setOfCheckedId);

    const selectedRoles = this.orgChartRoleJobScopeList.filter(r => selectedRoleIds.includes(r.roleId));

    const confirmOrgChartRole = selectedRoles
      .map(r => `<li>${r.roleName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmOrgChartRole}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleService.bulkDeleteRole(selectedRoleIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });

  }

  editRole(row: RoleOverview): void {
    this.editRoleComponent.initialize(row);
    this.editRoleComponent.open();
  }

  viewRole(row: RoleOverview): void {
    this.viewRoleComponent.initialize(row);
    this.viewRoleComponent.open();
  }

  deleteRole(row: RoleOverview): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmOrgChartRole = `<b>${row.roleName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmOrgChartRole}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.roleService.deleteRole(row.roleId).subscribe({
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

  onCurrentPageDataChange(listOfCurrentPageData: readonly RoleOverview[]): void {
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

  toggleVisibility(row: RoleOverview): void {
    const toggleConfirmTitle = this.translateService.instant(this.toggleConfirmTitleKey);
    const toggleConfirmContent = this.translateService.instant(this.toggleConfirmContentKey);
    const toggleConfirmOk = this.translateService.instant(this.toggleConfirmOkKey);

    const confirmOrgChartRole = `<b>${row.roleName} (${row.orgChartName})</b>`;

    const visibility = !row.visible

    this.confirmModal = this.modal.confirm({
      nzTitle: toggleConfirmTitle,
      nzContent: `${toggleConfirmContent}<br/><br/><ul><li>${confirmOrgChartRole}</li></ul>`,
      nzOkText: toggleConfirmOk,
      nzOnOk: () => {
        this.isSwitchLoading = true;
        this.roleService.toggleVisibility(row.roleId, visibility).subscribe({
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
      this.roleService.import(file).subscribe({
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
      this.roleService.export().subscribe((response) => {
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