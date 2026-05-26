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
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { AddSelfDeclaredSkillComponent } from './add-self-declared-skill/add-self-declared-skill.component';
import { EditSelfDeclaredSkillComponent } from './edit-self-declared-skill/edit-self-declared-skill.component';
import { ViewSelfDeclaredSkillComponent } from './view-self-declared-skill/view-self-declared-skill.component';

import { AuthService } from '../../../services/auth.service';
import { LoadingService } from '../../../services/loading.service';

import { BehaviorSubject, forkJoin } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { SkillProficiency, StaffSelfDeclaredSkill } from '../../../models/staff.model';
import { StaffSkillService } from '../../../services/staff-skill.service';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-self-declared-skill',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule,
    NzDropDownModule, AddSelfDeclaredSkillComponent, EditSelfDeclaredSkillComponent,
    NzTagModule, ViewSelfDeclaredSkillComponent],
  templateUrl: './self-declared-skill.component.html',
  styleUrl: './self-declared-skill.component.scss'
})
export class SelfDeclaredSkillComponent implements OnInit {
  @ViewChild(AddSelfDeclaredSkillComponent) addSelfDeclaredSkillComponent!: AddSelfDeclaredSkillComponent;
  @ViewChild(EditSelfDeclaredSkillComponent) editSelfDeclaredSkillComponent!: EditSelfDeclaredSkillComponent;
  @ViewChild(ViewSelfDeclaredSkillComponent) viewSelfDeclaredSkillComponent!: ViewSelfDeclaredSkillComponent;

  private readonly titleKey = 'PAGE.PROFILE.SKILL.TITLE';
  private readonly searchAnyKey = 'PAGE.PROFILE.SKILL.SEARCH.FIELD.ANY';
  private readonly searchSkillKey = 'PAGE.PROFILE.SKILL.SEARCH.FIELD.SKILL';
  private readonly searchDescKey = 'PAGE.PROFILE.SKILL.SEARCH.FIELD.DESC';
  private readonly searchProficiencyKey = 'PAGE.PROFILE.SKILL.SEARCH.FIELD.PROF';
  private readonly deleteConfirmTitleKey = 'PAGE.PROFILE.SKILL.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.PROFILE.SKILL.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.PROFILE.SKILL.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.PROFILE.SKILL.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.PROFILE.SKILL.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.PROFILE.SKILL.BULK_DELETE.CONFIRM.OK';

  data: StaffSelfDeclaredSkill[] = [];
  listOfCurrentPageData: readonly StaffSelfDeclaredSkill[] = [];
  listOfDisplayData = [...this.data];
  optionList: Option[] = []
  proficiencyColors: Record<SkillProficiency, string> = {
    [SkillProficiency.BEGINNER]: '#3E6D9C',
    [SkillProficiency.INTERMEDIATE]: '#FD841F',
    [SkillProficiency.ADVANCED]: '#E14D2A'
  };

  getProficiencyColor(proficiency: SkillProficiency): string {
    return this.proficiencyColors[proficiency] || '#808080';
  }

  isVisiting$ = new BehaviorSubject<boolean>(false);
  staffProfileId?: string;

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;

  searchValue?: string;
  searchField: number = 0;

  confirmModal?: NzModalRef;

  sortBySkillName = (a: StaffSelfDeclaredSkill, b: StaffSelfDeclaredSkill) =>
    a.skill.localeCompare(b.skill);

  sortByProficiency = (a: StaffSelfDeclaredSkill, b: StaffSelfDeclaredSkill) => {
    const order = [SkillProficiency.BEGINNER, SkillProficiency.INTERMEDIATE, SkillProficiency.ADVANCED];
    return order.indexOf(a.proficiency) - order.indexOf(b.proficiency);
  };

  constructor(private translateService: TranslateService, private title: Title, private authService: AuthService,
    private loadingService: LoadingService, private modal: NzModalService,
    private route: ActivatedRoute, private staffSkillService: StaffSkillService
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
      { id: 1, name: this.translateService.instant(this.searchSkillKey) },
      { id: 2, name: this.translateService.instant(this.searchDescKey) },
      { id: 3, name: this.translateService.instant(this.searchProficiencyKey) }
    ]
  }

  fetchOverview(): void {
    forkJoin({
      overview: this.staffSkillService.overview(this.staffProfileId!)
    }).subscribe({
      next: ({ overview }) => {
        this.data = overview;

        this.data.sort((a, b) => {
          const order = [SkillProficiency.BEGINNER, SkillProficiency.INTERMEDIATE, SkillProficiency.ADVANCED];
          return order.indexOf(a.proficiency) - order.indexOf(b.proficiency);
        });

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
      this.listOfDisplayData = this.data.filter((item: StaffSelfDeclaredSkill) => {
        const matchesName = item.skill?.toLowerCase().includes(searchVal);
        const matchesDescription = item.description?.toLowerCase().includes(searchVal);
        const matchesProficienct = item.proficiency.toLowerCase().includes(searchVal);

        if (this.searchField === 0) { // ANY
          return matchesName || matchesDescription || matchesProficienct;
        } else if (this.searchField === 1) { // SKILL NAME
          return matchesName;
        } else if (this.searchField === 2) { // ROLE
          return matchesDescription;
        } else if (this.searchField === 3) { // DESCRIPTION
          return matchesProficienct;
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
  
  addSkill(): void {
    this.addSelfDeclaredSkillComponent.initialize();
    this.addSelfDeclaredSkillComponent.open();
  }

  bulkDeleteSkill(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedIds = Array.from(this.setOfCheckedId);

    const selectedSkill = this.data.filter(r => selectedIds.includes(r.id));

    const confirmOrgChartRole = selectedSkill
      .map(r => `<li>${r.skill} (${r.proficiency})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmOrgChartRole}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffSkillService.deleteAll(selectedIds).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  editSkill(row: StaffSelfDeclaredSkill): void {
    this.editSelfDeclaredSkillComponent.initialize(row);
    this.editSelfDeclaredSkillComponent.open();
  }

  viewSkill(row: StaffSelfDeclaredSkill): void {
    this.viewSelfDeclaredSkillComponent.initialize(row);
    this.viewSelfDeclaredSkillComponent.open();
  }

  deleteSkill(row: StaffSelfDeclaredSkill): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmSkill = `<b>${row.skill} (${row.proficiency})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmSkill}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        this.staffSkillService.delete(row.id).subscribe({
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

    this.checked = listOfEnabledData.every(item => this.setOfCheckedId.has(item.id));
    this.indeterminate = listOfEnabledData.some(item => this.setOfCheckedId.has(item.id)) && !this.checked;
  }

  onCurrentPageDataChange(listOfCurrentPageData: readonly StaffSelfDeclaredSkill[]): void {
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


