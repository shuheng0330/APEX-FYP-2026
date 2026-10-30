import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Title } from '@angular/platform-browser';
import {
  NzTableFilterFn,
  NzTableFilterList,
  NzTableModule,
  NzTableSortFn,
  NzTableSortOrder,
} from 'ng-zorro-antd/table';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzRateModule } from 'ng-zorro-antd/rate';
import { FormsModule } from '@angular/forms';
import { NzInputDirective } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSkeletonComponent } from 'ng-zorro-antd/skeleton';

import { Router } from '@angular/router';
import { filter, forkJoin, take } from 'rxjs';

import { EvaluationService } from '../../services/evaluation.service';
import { StaffService } from '../../services/staff.service';
import { RoleCompetencyService } from '../../services/role-competency.service';
import { AuthService } from '../../services/auth.service';

import { StaffTemp } from '../../models/staff-temp.model';
import { RoleCompetency } from '../../models/role-compotency.model';
import { EvaluationDTO, RatingDTO } from '../../models/evaluation.model';
import { Role } from '../../models/role.model';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzButtonModule } from 'ng-zorro-antd/button';

interface ItemData {
  name: string;
  role: Role;
  performanceOverallScore: number;
}

interface ColumnItem {
  name: string;
  sortOrder: NzTableSortOrder | null;
  sortFn: NzTableSortFn<ItemData> | null;
  listOfFilter: NzTableFilterList;
  filterFn: NzTableFilterFn<ItemData> | null;
  filterMultiple: boolean;
  sortDirections: NzTableSortOrder[];
}

@Component({
  selector: 'evaluation-page',
  standalone: true,
  templateUrl: './evaluation-page.component.html',
  styleUrls: ['./evaluation-page.component.scss'],
  imports: [
    CommonModule,
    TranslatePipe,
    NzTableModule,
    NzModalModule,
    NzRateModule,
    FormsModule,
    NzInputDirective,
    NzSkeletonComponent,
    NzEmptyModule,
    NzButtonModule,
  ],
})
export class EvaluationPageComponent implements OnInit {
  @ViewChild('ratingFormTpl', { static: true }) ratingFormTpl!: TemplateRef<any>;
  titleKey = 'PAGE.EVALUATION.TITLE';
  value = 0;
  comment = '';
  evaluatedStaffIds: string[] = [];
  evaluationDone = false;
  evaluations: EvaluationDTO[] = [];
  roleCompetenciesList: RoleCompetency[] = [];
  allRoleCompetenciesList: RoleCompetency[] = [];
  listOfDownLineStaff: StaffTemp[] = [];
  listOfRole: NzTableFilterList = [];
  listOfDepartment: NzTableFilterList = [];
  loading = true;
  userId: string | null = null;

  readonly tooltips = [
    'Extremely Poor', 'Very Poor', 'Poor', 'Fair', 'Average',
    'Satisfactory', 'Good', 'Very Good', 'Excellent', 'Exceptional'
  ];

  listOfColumns: ColumnItem[] = [];

  constructor(
    private translate: TranslateService,
    private titleService: Title,
    private modal: NzModalService,
    private evaluationService: EvaluationService,
    private message: NzMessageService,
    private staffService: StaffService,
    private roleCompetenciesService: RoleCompetencyService,
    private router: Router,
    private auth: AuthService
  ) { }

  ngOnInit(): void {
    this.translate.get(this.titleKey).subscribe(t => this.titleService.setTitle(t));

    this.auth.userId$
      .pipe(
        filter((id): id is string => !!id),
        take(1)
      )
      .subscribe(id => {
        this.userId = id;
        this.loadDownLineStaff();
      });
  }

  onEvaluateStaff(staffId: string): void {
    this.comment = '';

    this.staffService.getStaffByIdTempToBeReplaced(staffId).subscribe(staff => {
      const roleId = staff.role.id;
      const roleName = staff.role.name;

      this.roleCompetenciesList = this.allRoleCompetenciesList
        .filter(comp => comp.id?.roleId === roleId)
        .map(c => ({ ...c, rating: 0 }));

      if (this.roleCompetenciesList.length === 0) {
        this.message.warning(`Role competencies are not configured yet for the role: ${roleName}`);
        return;
      }

      this.onClickOpenRatingForm(staffId);
    });
  }

  onClickOpenRatingForm(staffId: string): void {
    this.modal.create({
      nzTitle: 'Rating Form',
      nzMaskClosable: false,
      nzOkText: 'Submit',
      nzOnOk: () => this.submit(staffId),
      nzContent: this.ratingFormTpl,
      nzWrapClassName: 'my-custom-style'
    });
  }

  submit(staffId: string): Promise<boolean> {
    return new Promise<boolean>((resolve, reject) => {
      if (this.roleCompetenciesList.some(comp => comp.rating === 0)) {
        this.message.error('All competencies must be rated');
        return reject();
      }

      const ratings: RatingDTO[] = this.roleCompetenciesList.map(comp => ({
        compId: comp.competency.id!,
        rating: comp.rating!
      }));

      const evaluationDTO: EvaluationDTO = {
        staffId,
        comment: this.comment,
        ratings,
        createdBy: this.userId!
      };

      this.evaluationService.createEvaluation(evaluationDTO).subscribe({
        next: () => {
          this.message.success('Evaluation submitted successfully.');
          this.resetEvaluationState(staffId);
          resolve(true);
        },
        error: (err) => {
          console.error('Error while submitting evaluation:', err);
          reject(false);
        }
      });
    });
  }

  private resetEvaluationState(staffId: string): void {
    this.comment = '';
    this.roleCompetenciesList.forEach(comp => comp.rating = 0);
    this.evaluationDone = true;
    this.getStaffList();

    if (!this.evaluatedStaffIds.includes(staffId)) {
      this.evaluatedStaffIds.push(staffId);
    }
  }

  goToStaffDashboard(staffId: string): void {
    this.router.navigate(['/performance', staffId], {
      queryParams: { context: 'manager' }
    });
  }

  getStaffList(): void {
    this.staffService.getDirectDownLineByManagerId(this.userId!).subscribe(staffs => {
      this.listOfDownLineStaff = staffs;
    });
  }

  private loadDownLineStaff(): void {
    if (!this.userId) return;

    this.staffService.getDirectDownLineByManagerId(this.userId).subscribe({
      next: staffs => {
        this.loadEvaluationsAndRoleCompetencies(staffs);
      },
      error: err => {
        console.error('Error fetching downline staff:', err);
        this.loading = false;
      }
    });
  }

  private loadEvaluationsAndRoleCompetencies(staffs: StaffTemp[]): void {
    forkJoin({
      evaluations: this.evaluationService.getEvaluationsByDirectDownLineId(this.userId!),
      roleCompetencies: this.roleCompetenciesService.getAllRoleCompetencies()
    }).subscribe({
      next: ({ evaluations, roleCompetencies }) => {
        this.evaluations = evaluations;
        this.evaluatedStaffIds = evaluations
          .filter(e => e.evaluationCycleStatus === 'OPEN')
          .map(e => e.staffId);
        this.allRoleCompetenciesList = roleCompetencies;

        this.listOfDownLineStaff = staffs.map(staff => {
          const staffEval = evaluations.find(e => e.staffId === staff.id);
          return {
            ...staff,
            performanceOverallScore: staffEval?.overallScore ?? -1
          };
        });

        this.listOfRole = this.buildFilterList(staffs.map(s => s.role.name));
        this.listOfDepartment = this.buildFilterList(staffs.map(s => s.role.orgChart.name));
        this.listOfColumns = this.generateColumns();
        this.loading = false;
      },
      error: err => {
        console.error('Error fetching evaluations or role competencies:', err);
        this.loading = false;
      }
    });
  }

  private buildFilterList(values: string[]): NzTableFilterList {
    return Array.from(new Set(values)).map(value => ({ text: value, value }));
  }

  generateColumns(): ColumnItem[] {
    return [
      {
        name: 'Staff',
        sortOrder: null,
        sortFn: (a, b) => a.name.localeCompare(b.name),
        sortDirections: ['ascend', 'descend', null],
        filterMultiple: true,
        listOfFilter: [],
        filterFn: null,
      },
      {
        name: 'Department',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        listOfFilter: this.listOfDepartment,
        filterFn: (dept: string, item: ItemData) => item.role.orgChart.name.includes(dept),
      },
      {
        name: 'Role',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        listOfFilter: this.listOfRole,
        filterFn: (role: string, item: ItemData) => item.role.name.includes(role),
      },
      {
        name: 'Performance Overall Score',
        sortOrder: null,
        sortFn: (a, b) => (a.performanceOverallScore || 0) - (b.performanceOverallScore || 0),
        sortDirections: ['ascend', 'descend', null],
        filterMultiple: false,
        listOfFilter: [],
        filterFn: null,
      },
      {
        name: 'Performance',
        sortOrder: null,
        sortFn: null,
        sortDirections: [null],
        filterMultiple: false,
        listOfFilter: [],
        filterFn: null,
      }
    ];
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.auth.hasRole(role));
  }
}
