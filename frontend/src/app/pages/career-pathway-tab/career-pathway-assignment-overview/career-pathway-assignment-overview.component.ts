import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

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

import { AddCareerPathwayAssignmentComponent } from './add-career-pathway-assignment/add-career-pathway-assignment.component';
import { EditCareerPathwayAssignmentComponent } from './edit-career-pathway-assignment/edit-career-pathway-assignment.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { ViewCareerPathwayAssignmentComponent } from './view-career-pathway-assignment/view-career-pathway-assignment.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { CareerPathwayAssignmentService } from '../../../services/careerPathwayAssignment.service';

import { CareerPathwayAssignmentOverview } from '../../../models/careerPathway.model';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-career-pathway-assignment-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule, FileUploadComponent,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, AddCareerPathwayAssignmentComponent, EditCareerPathwayAssignmentComponent,
    NzToolTipModule, NzEmptyModule, NzGridModule, NzCardModule, NzTagModule,
    ViewCareerPathwayAssignmentComponent, TablePageSizeSelectorComponent],
  templateUrl: './career-pathway-assignment-overview.component.html',
  styleUrl: './career-pathway-assignment-overview.component.scss'
})
export class CareerPathwayAssignmentOverviewComponent {
  @ViewChild(AddCareerPathwayAssignmentComponent) addCareerPathwayAssignmentComponent!: AddCareerPathwayAssignmentComponent;
  @ViewChild(EditCareerPathwayAssignmentComponent) editCareerPathwayAssignmentComponent!: EditCareerPathwayAssignmentComponent;
  @ViewChild(ViewCareerPathwayAssignmentComponent) viewCareerPathwayAssignmentComponent!: ViewCareerPathwayAssignmentComponent;
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

  private readonly titleKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.TITLE';
  private readonly searchAnyKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.SEARCH.FIELD.DEPARTMENT';
  private readonly searchCareerPathwayKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.SEARCH.FIELD.CAREER_PATHWAY';
  private readonly searchStaffNameKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.SEARCH.FIELD.STAFF.NAME';
  private readonly searchStaffEmailAddressKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.SEARCH.FIELD.STAFF.EMAIL';
  private readonly deleteConfirmTitleKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.CAREER_PATHWAY.ASSIGNMENT.BULK_DELETE.CONFIRM.OK';

  private readonly profilePage = '/profile';

  optionList: Option[] = []
  data: CareerPathwayAssignmentOverview[] = [];
  listOfDisplayData = [...this.data];
  listOfCurrentPageData: readonly CareerPathwayAssignmentOverview[] = [];

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
  red = "#E6173F";
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
  pageSize: number = 10;

  confirmModal?: NzModalRef;

  sortByOrgChartName = (a: CareerPathwayAssignmentOverview, b: CareerPathwayAssignmentOverview) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByCareerPathwayName = (a: CareerPathwayAssignmentOverview, b: CareerPathwayAssignmentOverview) =>
    a.careerPathwayName.localeCompare(b.careerPathwayName);

  constructor(private translateService: TranslateService, private title: Title,
    private authService: AuthService, private loadingService: LoadingService,
    private modal: NzModalService, private careerPathwayAssignmentService: CareerPathwayAssignmentService,
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
    this.fetchCareerPathwayAssignmentOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchCareerPathwayKey) },
      { id: 3, name: this.translateService.instant(this.searchStaffNameKey) },
      { id: 4, name: this.translateService.instant(this.searchStaffEmailAddressKey) },
    ]
  }

  fetchCareerPathwayAssignmentOverview(): void {
    forkJoin({
      careetPathway: this.careerPathwayAssignmentService.overview()
    }).subscribe({
      next: ({ careetPathway }) => {
        this.data = careetPathway.sort((a, b) => {
          const sortByOrgChartName = a.orgChartName.localeCompare(b.orgChartName);
          const sortByCareerPathwayName = a.careerPathwayName.localeCompare(b.careerPathwayName);
          const sortById = a.careerPathwayId - b.careerPathwayId;

          if (sortByOrgChartName != 0) return sortByOrgChartName;
          if (sortByCareerPathwayName != 0) return sortByCareerPathwayName;
          return sortById;
        })

        this.data.forEach((item) => {
          item.staffs.sort((a, b) => {
            const sortByName = (a.name ?? '').localeCompare(b.name ?? '');
            const sortByEmail = a.email.localeCompare(b.email);

            if (sortByName != 0) return sortByName;
            return sortByEmail;
          })
        })

        this.listOfDisplayData = this.data;
        this.isLoaded = true;
      }
    }
    )

  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.data];
    } else {
      this.listOfDisplayData = this.data.filter((item: CareerPathwayAssignmentOverview) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesCareerPathway = item.careerPathwayName?.toLowerCase().includes(searchVal);
        const matchesStaffName = item.staffs?.some(staff =>
          staff.name?.toLowerCase().includes(searchVal)
        );
        const matchesStaffEmail = item.staffs?.some(staff =>
          staff.email.toLowerCase().includes(searchVal)
        );

        if (this.searchField === 0) { // ANY
          return matchesDepartment || matchesCareerPathway || matchesStaffName || matchesStaffEmail;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // CAREER PATHWAY
          return matchesCareerPathway;
        } else if (this.searchField === 3) { // STAFF NAME
          return matchesStaffName;
        } else if (this.searchField === 4) { // STAFF EMAIL ADDRESS
          return matchesStaffEmail;
        }
        return false;
      });
    }

    setTimeout(() => this.loadingService.hide(), 100);
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }

  viewProfile(staffId: string): void {
    this.router.navigate([this.profilePage], { queryParams: { staffId } });
  }

  addCareerPathwayAssignment(): void {
    this.addCareerPathwayAssignmentComponent.initialize();
    this.addCareerPathwayAssignmentComponent.open();
  }

  bulkDeleteCareerPathwayAssignment(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedCareerPathwayIds = Array.from(this.setOfCheckedId);

    const selectedCareerPathways = this.data.filter(r => selectedCareerPathwayIds.includes(r.careerPathwayId));

    const confirmOrgChartRole = selectedCareerPathways
      .map(r => `<li>${r.careerPathwayName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmOrgChartRole}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.careerPathwayAssignmentService.deleteAll(selectedCareerPathwayIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editCareerPathwayAssignment(row: CareerPathwayAssignmentOverview): void {
    this.editCareerPathwayAssignmentComponent.initialize(row);
    this.editCareerPathwayAssignmentComponent.open();
  }

  viewCareerPathwayAssignment(row: CareerPathwayAssignmentOverview): void {
    this.viewCareerPathwayAssignmentComponent.initialize(row);
    this.viewCareerPathwayAssignmentComponent.open();
  }

  deleteCareerPathwayAssignment(row: CareerPathwayAssignmentOverview): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmCareerPathway = `<b>${row.careerPathwayName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCareerPathway}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.careerPathwayAssignmentService.delete(row.careerPathwayId).subscribe({
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

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.careerPathwayId));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.careerPathwayId)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly CareerPathwayAssignmentOverview[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.careerPathwayId, value));
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
      this.careerPathwayAssignmentService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_STAFF', 'CAN_MANAGE_CAREER_PATHWAY'])) {
      this.careerPathwayAssignmentService.export().subscribe((response) => {
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
