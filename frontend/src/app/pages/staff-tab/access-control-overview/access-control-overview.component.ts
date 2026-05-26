import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzButtonModule } from 'ng-zorro-antd/button';

import { TranslatedAuthority } from '../../../models/access-control.model';
import { OrgChartRoleAuthorityMap } from '../../../models/access-control.model';

import { AuthorityService } from '../../../services/authority.service';
import { LoadingService } from '../../../services/loading.service';
import { AuthService } from '../../../services/auth.service';

import { AddAccessControlComponent } from './add-access-control/add-access-control.component';
import { EditAccessControlComponent } from './edit-access-control/edit-access-control.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { HttpErrorResponse } from '@angular/common/http';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-access-control-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzTagModule, NzIconModule,
    NzToolTipModule, NzDropDownModule, AddAccessControlComponent, EditAccessControlComponent,
    NzModalModule, NzInputModule, FormsModule, NzSelectModule, NzButtonModule,
    FileUploadComponent, TablePageSizeSelectorComponent],
  templateUrl: './access-control-overview.component.html',
  styleUrl: './access-control-overview.component.scss'
})

export class AccessControlOverviewComponent implements OnInit {
  @ViewChild(AddAccessControlComponent) addAccessControlComponent!: AddAccessControlComponent;
  @ViewChild(EditAccessControlComponent) editAccessControlComponent!: EditAccessControlComponent;
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

  private readonly titleKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.TITLE";
  private readonly deleteConfirmTitleKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.TITLE";
  private readonly deleteConfirmContentKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.CONTENT";
  private readonly deleteConfirmOkKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.OK";
  private readonly bulkDeleteConfirmTitleKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.TITLE";
  private readonly bulkDeleteConfirmContentKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.CONTENT";
  private readonly bulkDeleteConfirmOkKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.DELETE.CONFIRM.OK";
  private readonly searchAnyKey = 'PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.SEARCH.FIELD.ROLE';
  private readonly searchAccessKey = 'PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.SEARCH.FIELD.ACCESS';

  confirmModal?: NzModalRef;

  translatedAuthorityList: TranslatedAuthority[] = [];
  orgChartRoleAuthorityMapList: OrgChartRoleAuthorityMap[] = [];
  flattenedRoleList: any[] = [];
  listOfCurrentPageData: readonly any[] = [];
  listOfDisplayData = [...this.flattenedRoleList]
  optionList: Option[] = []
  setOfCheckedId = new Set<number>();

  isLoaded: boolean = false;
  checked = false;
  indeterminate = false;
  searchValue?: string;
  searchField: number = 0;
  red = "#E6173F";
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

  sortByOrgChartName = (a: any, b: any) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByRoleName = (a: any, b: any) =>
    a.roleName.localeCompare(b.roleName);

  constructor(private title: Title, private authorityService: AuthorityService,
    private modal: NzModalService, private translateService: TranslateService,
    private loadingService: LoadingService, private authService: AuthService
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.fetchAccessControlOverview();
    this.loadOptionList();
  }

  fetchAccessControlOverview(): void {
    this.authorityService.getStaffAccessControlOverview().subscribe({
      next: (overview) => {
        this.translatedAuthorityList = overview.permissions.sort((a, b) => a.id! - b.id!);
        this.orgChartRoleAuthorityMapList = overview.departments;

        this.flattenedRoleList = this.orgChartRoleAuthorityMapList.flatMap(
          dept => dept.roles.map(role => ({
            orgChartName: dept.orgChartName,
            orgChartId: dept.orgChartId,
            orgChartDeleted: dept.orgChartDeleted,
            roleName: role.roleName,
            roleId: role.roleId,
            authorityMap: role.authorityMap
          }))
        )

        this.listOfDisplayData = this.flattenedRoleList;
        this.isLoaded = true;
      }
    })
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchRoleKey) },
      { id: 3, name: this.translateService.instant(this.searchAccessKey) }
    ]
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.flattenedRoleList];
    } else {

      let matchingAuthorityIds: number[] = [];
      if (this.searchField === 0 || this.searchField === 3) {
        matchingAuthorityIds = this.translatedAuthorityList
          .filter(auth => auth.label.toLowerCase().includes(searchVal))
          .map(auth => auth.id!);
      }

      this.listOfDisplayData = this.flattenedRoleList.filter((item: any) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);


        if (this.searchField === 0 || this.searchField === 3) {
          if (matchingAuthorityIds.length === 0 && this.searchField === 3) return false;
          const hasMatchingAuth = matchingAuthorityIds.some(id => {
            return item.authorityMap[id] === true;
          });

          if (hasMatchingAuth) return true;
        }

        if (this.searchField === 0) { // ANY
          return matchesDepartment || matchesRole;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // ROLE
          return matchesRole;
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

  onCurrentPageDataChange(listOfCurrentPageData: readonly any[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
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


  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.roleId, value));
    this.refreshCheckedStatus();
  }

  addPermission(): void {
    this.addAccessControlComponent.translatedAuthorityList = this.translatedAuthorityList;
    this.addAccessControlComponent.initialize();
    this.addAccessControlComponent.open();
  }

  editPermission(roleId: number): void {
    const orgChartRole = this.flattenedRoleList.find((orgChartRole) =>
      orgChartRole.roleId === roleId);

    const orgChartId = orgChartRole.orgChartId;
    const orgChartName = orgChartRole.orgChartName;
    const roleName = orgChartRole.roleName;
    const grantedAuthorityMap = orgChartRole.authorityMap;

    const grantedAuthorities: string[] = Object.keys(grantedAuthorityMap)
      .filter(key => grantedAuthorityMap[key]);

    this.editAccessControlComponent.selectedOrgChartId = orgChartId;
    this.editAccessControlComponent.selectedOrgChartName = orgChartName;
    this.editAccessControlComponent.selectedRoleId = roleId;
    this.editAccessControlComponent.selectedRoleName = roleName;
    this.editAccessControlComponent.selectedAuthorities = grantedAuthorities;

    this.editAccessControlComponent.initialize();
    this.editAccessControlComponent.translatedAuthorityList = this.translatedAuthorityList;
    this.editAccessControlComponent.open();
  }

  deletePermission(roleId: number): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const orgChartRole = this.flattenedRoleList.find((orgChartRole) =>
      orgChartRole.roleId === roleId);

    const orgChartName = orgChartRole.orgChartName;
    const orgChartId = orgChartRole.orgChartId;
    const roleName = orgChartRole.roleName;

    const confirmOrgChartRole = `<b>${roleName} (${orgChartName})</b>`

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmOrgChartRole}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.loadingService.show();
        this.authorityService.deleteGrantedAccess(orgChartId!, roleId!)
          .subscribe({
            complete: () => {
              this.loadList();
              this.loadingService.hide();
            }
          });
      }
    })
  }

  bulkDeletePermission(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedRoleIds = Array.from(this.setOfCheckedId);

    const selectedRoles = this.flattenedRoleList.filter(r => selectedRoleIds.includes(r.roleId));

    const confirmOrgChartRole = selectedRoles
      .map(r => `<li>${r.roleName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmOrgChartRole}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.authorityService.bulkDeleteGrantedAccess(selectedRoleIds)
          .subscribe({
            complete: () => {
              this.loadList();
            }
          });
      }
    })
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
      this.authorityService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_ACCESS_CONTROL'])) {
      this.authorityService.export().subscribe((response) => {
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
