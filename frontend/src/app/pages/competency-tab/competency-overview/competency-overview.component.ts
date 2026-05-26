import { Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { NzTableModule } from 'ng-zorro-antd/table';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';
import { CompetencyCompTag } from '../../../models/competency-comp-tag.model';
import { CompetencyService } from '../../../services/competency.service';

import { AddCompetencyComponent } from './add-competency/add-competency.component';
import { EditCompetencyComponent } from './edit-competency/edit-competency.component';
import { ViewCompetencyComponent } from './view-competency/view-competency.component';
import { FileUploadComponent } from '../../../components/file-buffer/file-upload/file-upload.component';
import { TablePageSizeSelectorComponent } from '../../../components/table-page-size-selector/table-page-size-selector.component';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-competency-overview',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, NzTagModule, AddCompetencyComponent, EditCompetencyComponent,
    FileUploadComponent, ViewCompetencyComponent, TablePageSizeSelectorComponent],
  templateUrl: './competency-overview.component.html',
  styleUrl: './competency-overview.component.scss'
})
export class CompetencyOverviewComponent {
  @ViewChild(AddCompetencyComponent) addCompetencyComponent!: AddCompetencyComponent;
  @ViewChild(EditCompetencyComponent) editCompetencyComponent!: EditCompetencyComponent;
  @ViewChild(ViewCompetencyComponent) viewCompetencyComponent!: ViewCompetencyComponent;
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

  private readonly titleKey = 'PAGE.COMPETENCY.OVERVIEW.TITLE';
  private readonly searchAnyKey = 'PAGE.COMPETENCY.OVERVIEW.SEARCH.FIELD.ANY';
  private readonly searchCompetencyNameKey = 'PAGE.COMPETENCY.OVERVIEW.SEARCH.FIELD.NAME';
  private readonly searchDescriptionKey = 'PAGE.COMPETENCY.OVERVIEW.SEARCH.FIELD.DESC';
  private readonly searchTagKey = 'PAGE.COMPETENCY.OVERVIEW.SEARCH.FIELD.TAG';
  private readonly deleteConfirmTitleKey = 'PAGE.COMPETENCY.OVERVIEW.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.COMPETENCY.OVERVIEW.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.COMPETENCY.OVERVIEW.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.COMPETENCY.OVERVIEW.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.COMPETENCY.OVERVIEW.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.COMPETENCY.OVERVIEW.BULK_DELETE.CONFIRM.OK';

  optionList: Option[] = []
  competencyCompTagList: CompetencyCompTag[] = []
  listOfDisplayData = [...this.competencyCompTagList];
  listOfCurrentPageData: readonly CompetencyCompTag[] = [];
  colorCodes: string[] = ['#E14D2A', '#FD841F', '#3E6D9C', '#001253'];

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
  pageSize: number = 10;

  confirmModal?: NzModalRef;

  sortByCompetencyName = (a: CompetencyCompTag, b: CompetencyCompTag) =>
    a.competencyName.localeCompare(b.competencyName);

  sortByCompetencyDescription = (a: CompetencyCompTag, b: CompetencyCompTag) =>
    a.competencyDescription!.localeCompare(b.competencyDescription!);

  constructor(private translateService: TranslateService, private title: Title,
    private competencyService: CompetencyService, private authService: AuthService,
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
    this.fetchCompetencyOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchCompetencyNameKey) },
      { id: 2, name: this.translateService.instant(this.searchDescriptionKey) },
      { id: 3, name: this.translateService.instant(this.searchTagKey) }
    ]
  }

  fetchCompetencyOverview(): void {
    this.competencyService.getCompetencyOverview().subscribe({
      next: (res) => {

        let competency = res;

        this.competencyCompTagList = competency.sort((a, b) => {
          return a.competencyName.localeCompare(b.competencyName);
        });

        this.listOfDisplayData = this.competencyCompTagList;
        this.isLoaded = true;
      }
    })
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.competencyCompTagList];
    } else {
      this.listOfDisplayData = this.competencyCompTagList.filter((item: CompetencyCompTag) => {
        const matchesCompetencyName = item.competencyName.toLowerCase().includes(searchVal);
        const matchesDescription = item.competencyDescription?.toLowerCase().includes(searchVal);
        const matchesTag = item.assignedCompTags?.some(compTag =>
          compTag.tag.toLowerCase().includes(searchVal)
        );

        if (this.searchField === 0) { // ANY
          return matchesCompetencyName || matchesDescription || matchesTag;
        } else if (this.searchField === 1) { // COMPETENCY NAME
          return matchesCompetencyName;
        } else if (this.searchField === 2) { // DESCRIPTION
          return matchesDescription;
        } else if (this.searchField === 3) { // TAG
          return matchesTag;
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

  addCompetency(): void {
    this.addCompetencyComponent.initialize();
    this.addCompetencyComponent.open();
  }

  bulkDeleteCompetency(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedCompetencyIds = Array.from(this.setOfCheckedId);

    const selectedCompetencies = this.competencyCompTagList.filter(c => selectedCompetencyIds.includes(c.competencyId!));

    const confirmCompetency = selectedCompetencies
      .map(c => `<li>${c.competencyName}`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmCompetency}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyService.bulkDeleteCompetency(selectedCompetencyIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });

  }

  editCompetency(row: CompetencyCompTag): void {
    this.editCompetencyComponent.selectedCompetencyId = row.competencyId!;
    this.editCompetencyComponent.selectedCompetencyName = row.competencyName;
    this.editCompetencyComponent.selectedCompetencyDescription = row.competencyDescription || '';

    const compTagList = row.assignedCompTags.flatMap(compTag => compTag.tag);
    this.editCompetencyComponent.selectedCompTagList = compTagList;

    this.editCompetencyComponent.initialize();
    this.editCompetencyComponent.open();
  }

  viewCompetency(row: CompetencyCompTag): void {
    this.viewCompetencyComponent.initialize(row);
    this.viewCompetencyComponent.open();
  }

  deleteCompetency(row: CompetencyCompTag): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmCompetency = `<b>${row.competencyName}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCompetency}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.competencyService.deleteCompetency(row.competencyId!).subscribe({
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

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.competencyId!));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.competencyId!)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly CompetencyCompTag[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.competencyId!, value));
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
      this.competencyService.import(file).subscribe({
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
    if (this.hasAccess(['CAN_MANAGE_COMPETENCY'])) {
      this.competencyService.export().subscribe((response) => {
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
