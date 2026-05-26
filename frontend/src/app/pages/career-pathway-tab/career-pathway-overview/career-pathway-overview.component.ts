import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTreeNodeOptions } from 'ng-zorro-antd/core/tree';
import { NzTreeModule } from 'ng-zorro-antd/tree';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzCardModule } from 'ng-zorro-antd/card';

import {
  OrgChartNode,
} from 'ngx-interactive-org-chart';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { CareerPathwayService } from '../../../services/careerPathway.service';

import { AddCareerPathwayComponent } from './add-career-pathway/add-career-pathway.component';
import { EditCareerPathwayComponent } from './edit-career-pathway/edit-career-pathway.component';
import { ViewCareerPathwayComponent } from './view-career-pathway/view-career-pathway.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

import { CareerPathwayOverview, CareerPathwayOverviewGraphData } from '../../../models/careerPathway.model';
import { HttpErrorResponse } from '@angular/common/http';

interface Option {
  id: number;
  name: string;
}

interface RoleDto {
  name: string;
  deleted: boolean
}

@Component({
  selector: 'app-career-pathway-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule, FileUploadComponent,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, AddCareerPathwayComponent, NzTagModule, NzTreeModule, NzToolTipModule,
    EditCareerPathwayComponent, NzCardModule, TablePageSizeSelectorComponent,
    ViewCareerPathwayComponent],
  templateUrl: './career-pathway-overview.component.html',
  styleUrl: './career-pathway-overview.component.scss'
})
export class CareerPathwayOverviewComponent {
  @ViewChild(AddCareerPathwayComponent) addCareerPathwayComponent!: AddCareerPathwayComponent;
  @ViewChild(EditCareerPathwayComponent) editCareerPathwayComponent!: EditCareerPathwayComponent;
  @ViewChild(ViewCareerPathwayComponent) ViewCareerPathwayComponent!: ViewCareerPathwayComponent;
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

  private readonly titleKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.TITLE';
  private readonly searchAnyKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.DEPARTMENT';
  private readonly searchCareerPathwayKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.CAREER_PATHWAY';
  private readonly searchRoleKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.ROLE';
  private readonly searchDescriptionKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.DESC';
  private readonly searchTrackKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.SEARCH.FIELD.TRACK';
  private readonly deleteConfirmTitleKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.CAREER_PATHWAY.OVERVIEW.BULK_DELETE.CONFIRM.OK';

  optionList: Option[] = []
  data: CareerPathwayOverview[] = [];
  listOfDisplayData = [...this.data];
  listOfCurrentPageData: readonly CareerPathwayOverview[] = [];
  careerPathwayRoleMap: Record<number, RoleDto[]> = {};

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  searchValue?: string;
  searchField: number = 0;
  pageSize: number = 10;

  confirmModal?: NzModalRef;

  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];
  roleColorCodes: string[] = [
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
  red = "#E6173F";

  sortByDepartmentName = (a: CareerPathwayOverview, b: CareerPathwayOverview) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByCareerPathwayName = (a: CareerPathwayOverview, b: CareerPathwayOverview) =>
    a.careerPathwayName.localeCompare(b.careerPathwayName);

  sortByDescription = (a: CareerPathwayOverview, b: CareerPathwayOverview) =>
    (a.careerPathwayDescription ?? '').localeCompare(b.careerPathwayDescription ?? '');

  sortByTrack = (a: CareerPathwayOverview, b: CareerPathwayOverview) =>
    a.trackDtoList.length - b.trackDtoList.length;

  constructor(private translateService: TranslateService, private title: Title,
    private authService: AuthService, private loadingService: LoadingService,
    private careerPathwayService: CareerPathwayService, private modal: NzModalService) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.careerPathwayRoleMap = {};
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchCareerPathwayOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
      { id: 2, name: this.translateService.instant(this.searchCareerPathwayKey) },
      { id: 3, name: this.translateService.instant(this.searchDescriptionKey) },
      { id: 4, name: this.translateService.instant(this.searchTrackKey) },
      { id: 5, name: this.translateService.instant(this.searchRoleKey) },
    ]
  }

  fetchCareerPathwayOverview(): void {
    forkJoin({
      overview: this.careerPathwayService.overview()
    }).subscribe({
      next: ({ overview }) => {
        this.data = overview
          .sort((a, b) => {
            const compareDepartment = a.orgChartName.localeCompare(b.orgChartName);
            const compareName = a.careerPathwayName.localeCompare(b.careerPathwayName);
            const compareId = a.careerPathwayId - b.careerPathwayId;

            if (compareDepartment !== 0) return compareDepartment;
            if (compareName !== 0) return compareName;
            return compareId;
          });

        this.data.forEach((data) => {
          if (data.graph)
            data.graph = this.mapOrgChartNodeStyle(data.graph!, data.careerPathwayId)
        })

        this.listOfDisplayData = this.data;
        this.isLoaded = true;
      }
    })
  }

  private mapOrgChartNodeStyle(node: OrgChartNode, careerPathwayId: number): OrgChartNode {
    const data = node.data! as CareerPathwayOverviewGraphData;
    if (!this.careerPathwayRoleMap[careerPathwayId]) {
      this.careerPathwayRoleMap[careerPathwayId] = [];
    }

    this.careerPathwayRoleMap[careerPathwayId].push({ name: node.name!, deleted: data.deleted });
    return {
      id: node.id,
      name: node.name,
      data: data,
      children: node.children?.map((child) => this.mapOrgChartNodeStyle(child, careerPathwayId)) || [],
      style: {
        background: data.hasVisited === 0 ? '#DDF4E7' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-border-color': data.hasVisited === 0 ? '#B6CEB4' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#15469C' : '#0C2B4E',
        '--collapse-button-hover-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--collapse-button-hover-background': data.hasVisited === 0 ? '#A8FBD3' : data.hasVisited === -1 ? '#1890ff' : '#1890ff',
        '--node-outline-color': data.hasVisited === 0 ? '#DDF4E7' : data.hasVisited === -1 ? '#e0e6f6' : '#0C2B4E',
        '--node-active-outline-color': data.hasVisited === 0 ? '#043915' : data.hasVisited === -1 ? '#15469C' : '#000000'
      }
    }
  }

  private mapOrgChartNodeToNzTree(node: OrgChartNode<any>): NzTreeNodeOptions {
    return {
      title: node.name ?? 'Unnamed',
      key: node.id?.toString() ?? crypto.randomUUID(), // fallback key
      expanded: true,
      selectable: true,
      deleted: node.data.deleted as boolean,
      children: node.children?.map(child => this.mapOrgChartNodeToNzTree(child)) ?? []
    };
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.data];
    } else {
      this.listOfDisplayData = this.data.filter((item: CareerPathwayOverview) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesName = item.careerPathwayName.toLocaleLowerCase().includes(searchVal);
        const matchesDescription = item.careerPathwayDescription?.toLocaleLowerCase().includes(searchVal);
        const matchesTrack = item.trackDtoList?.some(track =>
          track.track.toLocaleLowerCase().includes(searchVal)
        );
        const matchesRole = this.nodeMatchesSearch(item.graph, searchVal);

        if (this.searchField === 0) { // ANY
          return matchesDepartment || matchesName || matchesDescription || matchesTrack || matchesRole;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // NAME
          return matchesName;
        } else if (this.searchField === 3) { // DESC
          return matchesDescription;
        } else if (this.searchField === 4) { // TRACK
          return matchesTrack;
        } else if (this.searchField === 5) { // ROLE
          return matchesRole;
        }
        return false;
      });
    }

    this.loadingService.hide()
  }

  private nodeMatchesSearch(node: OrgChartNode<any> | undefined, searchVal: string): boolean {
    if (!node) return false;

    const nameMatches = node.name?.toLowerCase().includes(searchVal);
    const childMatches = node.children?.some(child => this.nodeMatchesSearch(child, searchVal));

    return !!(nameMatches || childMatches);
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }



  addCareerPathway(): void {
    this.addCareerPathwayComponent.initialize();
    this.addCareerPathwayComponent.open();
  }

  bulkDeleteCareerPathway(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedCareerPathwayIds = Array.from(this.setOfCheckedId);

    const selectedCareerPathways = this.data.filter(r => selectedCareerPathwayIds.includes(r.careerPathwayId));

    const confirmCareerPathways = selectedCareerPathways
      .map(r => `<li>${r.careerPathwayName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmCareerPathways}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.careerPathwayService.deleteAll(selectedCareerPathwayIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });

  }

  editCareerPathway(row: CareerPathwayOverview): void {
    this.editCareerPathwayComponent.initialize(row);
  }

  viewCareerPathway(row: CareerPathwayOverview): void {
    this.ViewCareerPathwayComponent.initialize(row);
    this.ViewCareerPathwayComponent.open();
  }

  deleteCareerPathway(row: CareerPathwayOverview): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmCareerPathway = `<b>${row.careerPathwayName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCareerPathway}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.careerPathwayService.delete(row.careerPathwayId).subscribe({
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

  onCurrentPageDataChange(listOfCurrentPageData: readonly CareerPathwayOverview[]): void {
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
      this.careerPathwayService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_CAREER_PATHWAY'])) {
      this.careerPathwayService.export().subscribe((response) => {
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
