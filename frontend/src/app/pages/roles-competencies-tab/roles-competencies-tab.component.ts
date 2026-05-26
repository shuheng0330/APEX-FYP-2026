import { Component, OnInit, ViewChild } from '@angular/core';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
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
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { ViewRolesCompetenciesComponent } from './view-roles-competencies/view-roles-competencies.component';
import { AddRoleComponent } from '../role-tab/role-overview/add-role/add-role.component';
import { EditCompetencyAssignmentComponent } from '../competency-tab/competency-assignment-overview/edit-competency-assignment/edit-competency-assignment.component';
import { EditRoleAssignmentComponent } from '../role-tab/role-assignment-overview/edit-role-assignment/edit-role-assignment.component';
import { EditRoleComponent } from '../role-tab/role-overview/edit-role/edit-role.component';
import { TablePageSizeSelectorComponent } from '../../components/table-page-size-selector/table-page-size-selector.component';

import { CompetencyAssignment, CompetencyAssignmentOverviewData } from '../../models/role-compotency.model';
import { RoleAssignmentOverviewData } from '../../models/staff-role.model';
import { Role, RoleDetailsData, RoleOverview } from '../../models/role.model';

import { RoleCompetencyService } from '../../services/role-competency.service';
import { LoadingService } from '../../services/loading.service';
import { RoleService } from '../../services/role.service';
import { AuthService } from '../../services/auth.service';
import { RoleAssignmentService } from '../../services/role-assignment.service';

interface Option {
  id: number;
  name: string;
}

@Component({
  selector: 'app-roles-competencies-tab',
  imports: [CommonModule, TranslateModule, NzTableModule, NzButtonModule,
    NzModalModule, NzIconModule, NzInputModule, NzSelectModule, FormsModule, NzSwitchModule,
    NzDropDownModule, ViewRolesCompetenciesComponent, AddRoleComponent, EditCompetencyAssignmentComponent,
    EditRoleAssignmentComponent, EditRoleComponent, NzToolTipModule, NzTagModule,
    TablePageSizeSelectorComponent],
  templateUrl: './roles-competencies-tab.component.html',
  styleUrl: './roles-competencies-tab.component.scss'
})
export class RolesCompetenciesTabComponent implements OnInit {
  @ViewChild(ViewRolesCompetenciesComponent) viewRolesCompetenciesComponent!: ViewRolesCompetenciesComponent
  @ViewChild(AddRoleComponent) addRoleComponent!: AddRoleComponent;
  @ViewChild(EditCompetencyAssignmentComponent) editCompetencyAssignmentComponent!: EditCompetencyAssignmentComponent;
  @ViewChild(EditRoleAssignmentComponent) editRoleAssignmentComponent!: EditRoleAssignmentComponent;
  @ViewChild(EditRoleComponent) editRoleComponent!: EditRoleComponent;

  private readonly titleKey = "PAGE.ROLES_COMPETENCIES.TITLE"
  private readonly searchAnyKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.ANY';
  private readonly searchDepartmentKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.DEPARTMENT';
  private readonly searchRoleKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.ROLE';
  private readonly searchJobScopeKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.JOB_SCOPE';
  private readonly searchCompetencyKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.COMPETENCY';
  private readonly searchStaffNameKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.STAFF.NAME';
  private readonly searchStaffEmailKey = 'PAGE.ROLES_COMPETENCIES.SEARCH.FIELD.STAFF.EMAIL';
  private readonly toggleConfirmTitleKey = 'PAGE.ROLES_COMPETENCIES.TOGGLE.CONFIRM.TITLE';
  private readonly toggleConfirmContentKey = 'PAGE.ROLES_COMPETENCIES.TOGGLE.CONFIRM.CONTENT';
  private readonly toggleConfirmOkKey = 'PAGE.ROLES_COMPETENCIES.TOGGLE.CONFIRM.OK';
  private readonly deleteConfirmTitleKey = 'PAGE.ROLES_COMPETENCIES.DELETE.CONFIRM.TITLE';
  private readonly deleteConfirmContentKey = 'PAGE.ROLES_COMPETENCIES.DELETE.CONFIRM.CONTENT';
  private readonly deleteConfirmOkKey = 'PAGE.ROLES_COMPETENCIES.DELETE.CONFIRM.OK';
  private readonly bulkDeleteConfirmTitleKey = 'PAGE.ROLES_COMPETENCIES.BULK_DELETE.CONFIRM.TITLE';
  private readonly bulkDeleteConfirmContentKey = 'PAGE.ROLES_COMPETENCIES.BULK_DELETE.CONFIRM.CONTENT';
  private readonly bulkDeleteConfirmOkKey = 'PAGE.ROLES_COMPETENCIES.BULK_DELETE.CONFIRM.OK';

  data: RoleDetailsData[] = [];
  createdRole?: Role;
  updatedRole?: Role;
  editingRoleDetails?: RoleDetailsData;

  listOfCurrentPageData: readonly RoleDetailsData[] = [];
  listOfDisplayData = [...this.data];
  optionList: Option[] = [];

  setOfCheckedId = new Set<number>();
  checked = false;
  indeterminate = false;
  isLoaded = false;
  isSwitchLoading: boolean = false;
  searchValue?: string;
  searchField: number = 0;
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

  sortByVisibility = (a: RoleDetailsData, b: RoleDetailsData) =>
    Number(a.visible) - Number(b.visible);

  sortByOrgChartName = (a: RoleDetailsData, b: RoleDetailsData) =>
    a.orgChartName.localeCompare(b.orgChartName);

  sortByRoleName = (a: RoleDetailsData, b: RoleDetailsData) =>
    a.roleName.localeCompare(b.roleName);

  confirmModal?: NzModalRef;

  constructor(private translateService: TranslateService, private title: Title,
    private modal: NzModalService, private authService: AuthService, private roleService: RoleService,
    private roleCompetencyService: RoleCompetencyService, private loadingService: LoadingService,
    private roleAssignmentService: RoleAssignmentService
  ) { }

  ngOnInit(): void {
    this.title.setTitle(this.translateService.instant(this.titleKey));
    this.loadList();
  }

  loadList(): void {
    this.searchValue = undefined;
    this.setOfCheckedId.clear();
    this.refreshCheckedStatus();

    this.loadOptionList();
    this.updatedRole = undefined;
    this.createdRole = undefined;
    this.editingRoleDetails = undefined;
    this.fetchRolesCompetenciesOverview();
  }

  loadOptionList(): void {
    if (this.hasAccess(['CAN_MANAGE_ROLE'])) {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
        { id: 2, name: this.translateService.instant(this.searchRoleKey) },
        { id: 3, name: this.translateService.instant(this.searchJobScopeKey) },
        { id: 4, name: this.translateService.instant(this.searchCompetencyKey) },
        { id: 5, name: this.translateService.instant(this.searchStaffNameKey) },
        { id: 6, name: this.translateService.instant(this.searchStaffEmailKey) },
      ]
    } else {
      this.optionList = [
        { id: 0, name: this.translateService.instant(this.searchAnyKey) },
        { id: 1, name: this.translateService.instant(this.searchDepartmentKey) },
        { id: 2, name: this.translateService.instant(this.searchRoleKey) },
        { id: 3, name: this.translateService.instant(this.searchJobScopeKey) },
        { id: 4, name: this.translateService.instant(this.searchCompetencyKey) },
      ]
    }
  }

  fetchRolesCompetenciesOverview(): void {
    forkJoin({
      overview: this.roleCompetencyService.roleDetailsOverview()
    }).subscribe({
      next: ({ overview }) => {
        if (!this.hasAccess(['CAN_MANAGE_ROLE']) && !this.hasAccess(['CAN_VIEW_INVISIBLE_ROLE'])) {
          overview = overview.filter(data => {
            return data.visible == true;
          });
        }

        this.data = overview;
        this.data.sort((a, b) => {
          const sortByOrgChartName = a.orgChartName.localeCompare(b.orgChartName);
          const sortByRoleName = a.roleName.localeCompare(b.roleName);

          if (sortByOrgChartName != 0) return sortByOrgChartName;
          if (sortByRoleName != 0) return sortByRoleName;
          return a.roleId - b.roleId;
        })

        this.data.forEach(item => {
          item.competencies?.sort((a, b) => a.weightage - b.weightage)
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
      this.listOfDisplayData = this.data.filter((item: RoleDetailsData) => {
        const matchesDepartment = item.orgChartName?.toLowerCase().includes(searchVal);
        const matchesRole = item.roleName?.toLowerCase().includes(searchVal);
        const matchesJobScope = item.jobScopes?.some(jobScope =>
          jobScope.jobScope?.toLowerCase().includes(searchVal)
        );
        const matchesCompetency = item.competencies?.some(competency =>
          competency.competency.name.toLowerCase().includes(searchVal)
        )
        const matchesStaffName = item.staffs?.some(staff =>
          (staff.name ?? '').toLowerCase().includes(searchVal)
        )
        const matchesStaffEmail = item.staffs?.some(staff =>
          staff.email.toLowerCase().includes(searchVal)
        )

        if (this.searchField === 0 && this.hasAccess(['CAN_MANAGE_ROLE'])) { // ANY (FULL ACCESS)
          return matchesDepartment || matchesRole || matchesJobScope || matchesCompetency
            || matchesStaffName || matchesStaffEmail;
        } else if (this.searchField === 0 && !this.hasAccess(['CAN_MANAGE_ROLE'])) { // ANY (LIMITED ACCESS)
          return matchesDepartment || matchesRole || matchesJobScope || matchesCompetency;
        } else if (this.searchField === 1) { // DEPARTMENT
          return matchesDepartment;
        } else if (this.searchField === 2) { // ROLE
          return matchesRole;
        } else if (this.searchField === 3) { // JOB SOPE
          return matchesJobScope;
        } else if (this.searchField === 4) { // COMPETENCY
          return matchesCompetency;
        } else if (this.searchField === 5) { // STAFF NAME
          return matchesStaffName;
        } else if (this.searchField === 6) { // STAFF EMAILf
          return matchesStaffEmail;
        }
        return false;
      });
    }

    this.loadingService.hide();
  }

  viewRolesCompetencies(data: RoleDetailsData): void {
    this.viewRolesCompetenciesComponent.initialize(data.roleId, data);
  }

  resetSearch(): void {
    this.searchValue = '';
    this.search();
  }

  addRolesCompetencies(): void {
    this.addRoleComponent.initialize();
    this.addRoleComponent.open();
  }

  handleCreatedRole(role: Role) {
    this.createdRole = role;
    this.addCompetencyAssignment();
  }

  addCompetencyAssignment(): void {
    const mockData: CompetencyAssignmentOverviewData = {
      orgChartId: this.createdRole!.orgChart.id,
      orgChartName: this.createdRole!.orgChart.name,
      orgChartDeleted: this.createdRole!.orgChart.deleted,
      roleId: this.createdRole!.id!,
      roleName: this.createdRole!.name,
      roleDeleted: this.createdRole!.deleted,
      competencyAssignments: [],
      totalWeightage: 0
    }
    this.editCompetencyAssignmentComponent.initialize(mockData);
    this.editCompetencyAssignmentComponent.open();
  }

  addRoleAssignment(): void {
    if (this.createdRole) {
      const mockData: RoleAssignmentOverviewData = {
        orgChartId: this.createdRole!.orgChart.id,
        orgChartName: this.createdRole!.orgChart.name,
        orgChartDeleted: this.createdRole!.orgChart.deleted,
        roleId: this.createdRole!.id!,
        roleName: this.createdRole!.name,
        roleDeleted: this.createdRole!.deleted,
        staffList: []
      }

      this.editRoleAssignmentComponent.initialize(mockData);
      this.editRoleAssignmentComponent.open();
    }

    if (this.updatedRole && this.editingRoleDetails) {
      const mockData: RoleAssignmentOverviewData = {
        orgChartId: this.updatedRole!.orgChart.id,
        orgChartName: this.updatedRole!.orgChart.name,
        orgChartDeleted: this.updatedRole!.orgChart.deleted,
        roleId: this.updatedRole!.id!,
        roleName: this.updatedRole!.name,
        roleDeleted: this.updatedRole!.deleted,
        staffList: this.editingRoleDetails.staffs ?? []
      }

      this.editRoleAssignmentComponent.initialize(mockData);
      this.editRoleAssignmentComponent.open();
    }
  }

  editRolesCompetencies(row: RoleDetailsData): void {
    this.editingRoleDetails = row;
    const mockData: RoleOverview = {
      orgChartId: row.orgChartId,
      orgChartName: row.orgChartName,
      orgChartDeleted: false,
      roleId: row.roleId,
      roleName: row.roleName,
      description: row.roleDescription,
      visible: row.visible,
      assignedJobScopes: row.jobScopes
    }

    this.editRoleComponent.initialize(mockData);
    this.editRoleComponent.open();
  }

  handleUpdatedRole(role: Role) {
    this.updatedRole = role;
    this.editCompetencyAssignment();
  }

  editCompetencyAssignment(): void {
    const mockCompetencyAssignment: CompetencyAssignment[] =
      this.editingRoleDetails!.competencies!.map(data => ({
        competencyId: data.competency.id!,
        competencyName: data.competency.name,
        deleted: data.competency.deleted,
        weightage: data.weightage
      }));

    const mockData: CompetencyAssignmentOverviewData = {
      orgChartId: this.updatedRole!.orgChart.id,
      orgChartName: this.updatedRole!.orgChart.name,
      orgChartDeleted: this.updatedRole!.orgChart.deleted,
      roleId: this.updatedRole!.id!,
      roleName: this.updatedRole!.name,
      roleDeleted: this.updatedRole!.deleted,
      competencyAssignments: mockCompetencyAssignment ?? [],
      totalWeightage: this.editingRoleDetails?.totalWeightage!
    }
    this.editCompetencyAssignmentComponent.initialize(mockData);
    this.editCompetencyAssignmentComponent.open();
  }

  bulkDeleteRolesCompetencies(): void {
    const confirmTitle = this.translateService.instant(this.bulkDeleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.bulkDeleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.bulkDeleteConfirmOkKey);

    const selectedRoleIds = Array.from(this.setOfCheckedId);

    const selectedRoles = this.data.filter(r => selectedRoleIds.includes(r.roleId));

    const confirmRole = selectedRoles
      .map(r => `<li>${r.roleName} (${r.orgChartName})`)
      .join('</li>');

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><b><ol>${confirmRole}</ol></b>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        forkJoin({
          role: this.roleService.bulkDeleteRole(selectedRoleIds),
          roleAssignment: this.roleAssignmentService.bulkDeleteRole(selectedRoleIds),
          roleCompetencies: this.roleCompetencyService.bulkDeleteRoleCompetency(selectedRoleIds)
        }).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  deleteRolesCompetencies(row: RoleDetailsData): void {
    const confirmTitle = this.translateService.instant(this.deleteConfirmTitleKey);
    const confirmContent = this.translateService.instant(this.deleteConfirmContentKey);
    const confirmOk = this.translateService.instant(this.deleteConfirmOkKey);

    const confirmRole = `<b>${row.roleName} (${row.orgChartName})</b>`;

    this.confirmModal = this.modal.confirm({
      nzTitle: confirmTitle,
      nzContent: `${confirmContent}<br/><br/><ul><li>${confirmRole}</li></ul>`,
      nzOkText: confirmOk,
      nzOnOk: () => {
        forkJoin({
          role: this.roleService.deleteRole(row.roleId),
          roleAssignment: this.roleAssignmentService.deleteRoleAssignment(row.roleId),
          roleCompetencies: this.roleCompetencyService.deleteRoleCompetency(row.roleId)
        }).subscribe({
          complete: () => {
            this.loadList();
          }
        })
      }
    });
  }

  toggleVisibility(row: RoleDetailsData): void {
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

  onCurrentPageDataChange(listOfCurrentPageData: readonly RoleDetailsData[]): void {
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

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
