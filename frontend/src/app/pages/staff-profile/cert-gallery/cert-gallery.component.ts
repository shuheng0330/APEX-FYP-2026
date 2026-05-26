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
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { AddCertComponent } from './add-cert/add-cert.component';
import { EditCertComponent } from './edit-cert/edit-cert.component';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';

import { BehaviorSubject, forkJoin } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { StaffCert } from '../../../models/staff.model';
import { StaffCertService } from '../../../services/staff-cert.service';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-cert-gallery',
  standalone: true,
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule,
    NzDropDownModule, AddCertComponent, EditCertComponent, NzTagModule,
    NzCheckboxModule, NzEmptyModule, NzCardModule, NzGridModule, NzToolTipModule],
  templateUrl: './cert-gallery.component.html',
  styleUrl: './cert-gallery.component.scss'
})
export class CertGalleryComponent {
  @ViewChild(AddCertComponent) addCertComponent!: AddCertComponent;
  @ViewChild(EditCertComponent) editCertComponent!: EditCertComponent;

  private readonly titleKey = 'PAGE.PROFILE.CERT.TITLE';
  private readonly searchAnyKey = 'PAGE.PROFILE.CERT.SEARCH.FIELD.ANY';
  private readonly searchFileNameKey = 'PAGE.PROFILE.CERT.SEARCH.FIELD.CERT';
  private readonly searchDescKey = 'PAGE.PROFILE.CERT.SEARCH.FIELD.DESC';
  private readonly deleteConfirmTitleKey = 'PAGE.PROFILE.CERT.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.PROFILE.CERT.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.PROFILE.CERT.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.PROFILE.CERT.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.PROFILE.CERT.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.PROFILE.CERT.BULK_DELETE.CONFIRM.OK';

  data: StaffCert[] = [];
  listOfCurrentPageData: readonly StaffCert[] = [];
  listOfDisplayData = [...this.data];
  optionList: Option[] = []

  isVisiting$ = new BehaviorSubject<boolean>(false);
  staffProfileId?: string;

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;

  searchValue?: string;
  searchField: number = 0;

  confirmModal?: NzModalRef;

  sortByFileName = (a: StaffCert, b: StaffCert) =>
    a.fileName.localeCompare(b.fileName);

  constructor(private translateService: TranslateService, private title: Title, private authService: AuthService,
    private loadingService: LoadingService, private modal: NzModalService,
    private route: ActivatedRoute, private staffCertService: StaffCertService
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));

    this.route.queryParams.subscribe(params => {
      const staffId = params['staffId'];
      if (staffId) {
        if (staffId != this.authService.userId!) this.isVisiting$.next(true);
        else this.isVisiting$.next(false);
        this.staffProfileId = staffId;
        this.loadList();
      } else {
        this.staffProfileId = this.authService.userId!;
        this.isVisiting$.next(false);
        this.loadList();
      }
    });
  }

  loadList(): void {
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.fetchOverview();
  }

  loadOptionList(): void {
    this.optionList = [
      { id: 0, name: this.translateService.instant(this.searchAnyKey) },
      { id: 1, name: this.translateService.instant(this.searchFileNameKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) }
    ]
  }

  fetchOverview(): void {
    forkJoin({
      overview: this.staffCertService.overview(this.staffProfileId!)
    }).subscribe({
      next: ({ overview }) => {
        this.data = overview;

        this.data.sort((a, b) => a.fileName.localeCompare(b.fileName));

        this.listOfDisplayData = this.data;
        this.isLoaded = true;
      }
    })
  }

  search(): void {
    this.loadingService.show();

    const searchVal = this.searchValue?.trim().toLocaleLowerCase() || "";

    this.listOfDisplayData = [];

    if (!searchVal) {
      this.listOfDisplayData = [...this.data];
    } else {
      this.listOfDisplayData = this.data.filter((item: StaffCert) => {
        const matchesFileName = item.fileName.toLowerCase().includes(searchVal);
        const matchesDescription = item.description?.toLowerCase().includes(searchVal);

        if (this.searchField === 0) { // ANY
          return matchesFileName || matchesDescription;
        } else if (this.searchField === 1) { // FILE NAME
          return matchesFileName;
        } else if (this.searchField === 2) { // DESC
          return matchesDescription;
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

  addCert(): void {
    this.addCertComponent.initialize();
    this.addCertComponent.open();
  }

  bulkDeleteCert(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedIds = Array.from(this.setOfCheckedId);

    const selectedSkill = this.data.filter(r => selectedIds.includes(r.id));

    const confirmCert = selectedSkill
      .map(r => `<li>${r.fileName}`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmCert}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffCertService.deleteAll(selectedIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editCert(row: StaffCert): void {
    this.editCertComponent.initialize(row);
    this.editCertComponent.open();
  }

  deleteCert(row: StaffCert): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmCert = `<b>${row.fileName}</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmCert}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffCertService.delete(row.id).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  viewCert(row: StaffCert): void {
    const newWindow = window.open('', '_blank');

    if (newWindow) {
      newWindow.document.write('<div>Loading certificate preview...</div>');
    }

    this.staffCertService.viewCert(row.staff.id, row.fileName).subscribe({
      next: (response) => {
        const blob = new Blob([response.body!], { type: 'application/pdf' });
        const url = window.URL.createObjectURL(blob);

        if (newWindow) {
          newWindow.location.href = url;
        } else {
          window.open(url, '_blank');
        }
      }, error: (err) => {
        if (newWindow) {
          newWindow.close();
        }
        console.error('Failed to load certificate', err);
      }
    })
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

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.id));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.id)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly StaffCert[]): void {
    this.listOfCurrentPageData = listOfCurrentPageData;
    this.refreshCheckedStatus();
  }

  onItemChecked(id: number, checked: boolean): void {
    this.updateCheckedSet(id, checked);
    this.refreshCheckedStatus();
  }

  onAllChecked(value: boolean): void {
    this.listOfCurrentPageData.forEach(item => this.updateCheckedSet(item.id, value));
    this.refreshCheckedStatus();
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
