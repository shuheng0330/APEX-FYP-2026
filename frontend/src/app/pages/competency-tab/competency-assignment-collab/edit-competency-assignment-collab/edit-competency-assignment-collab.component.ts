import { Component, EventEmitter, HostListener, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, forkJoin, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, FormArray, FormControl, FormGroup, ValidatorFn, AbstractControl, ValidationErrors } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzAutocompleteModule } from 'ng-zorro-antd/auto-complete';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { OrgChart } from '../../../../models/orgChart.model';
import { Role } from '../../../../models/role.model';
import {
  CompetencyAssignmentRequiredData,
  CompetencyAssignmentRequiredDataId, CompetencyAssignmentProposal,
  RoleCompetencyProposalId,
  CompetencyAssignmentProposalOverviewData,
  UpdateCompetencyAssignmentRequest
} from '../../../../models/competency-collaboration.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { JobScopeService } from '../../../../services/jobScope.service';
import { AuthService } from '../../../../services/auth.service';
import { RoleService } from '../../../../services/role.service';
import { LoadingService } from '../../../../services/loading.service';
import { StaffService } from '../../../../services/staff.service';
import { CompetencyAssignmentCollaborationService } from '../../../../services/competency-assignment-collaboration.service';

@Component({
  selector: 'app-edit-competency-assignment-collab',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule, NzEmptyModule, NzToolTipModule],
  templateUrl: './edit-competency-assignment-collab.component.html',
  styleUrl: './edit-competency-assignment-collab.component.scss'
})
export class EditCompetencyAssignmentCollabComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.COMPETENCY.COLLAB.ASSIGNMENT.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.COMPETENCY.COLLAB.ASSIGNMENT.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.COMPETENCY.COLLAB.ASSIGNMENT.EDIT.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  roleList: Role[] = [];
  selectedRoleList = [...this.roleList];
  jobScopeList: string[] = [];
  filteredJobScopeList = [...this.jobScopeList];
  competencyList: CompetencyAssignmentRequiredData[] = [];
  selectedCompetencyList = [...this.competencyList];

  selectedRoleCompetencyProposalId: RoleCompetencyProposalId | undefined;
  selectedRecord: CompetencyAssignmentProposalOverviewData | undefined;

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [{ value: null as unknown as number, disabled: true }, [Validators.required]],
    role: [{ value: null as unknown as number, disabled: true }, [Validators.required]],
    description: [null as unknown as string, null],
    jobScopeList: this.fb.array<string>([]),
    competencyList: this.fb.array<FormGroup>([], [this.minFormArrayLength(1)]),
    reviewerList: this.fb.control<string[]>([]),
    proposerList: this.fb.control<string[]>([]),
    lastUpdatedBy: [{ value: null as unknown as string, disabled: true }, [Validators.required]],
  });

  listOfJobScopeControl = this.validateForm.get('jobScopeList') as FormArray<FormControl<string>>;

  get listOfCompetencyControl(): FormArray<FormGroup> {
    return this.validateForm.get('competencyList') as FormArray<FormGroup>;
  }

  get selectedCompetencyIds(): Set<CompetencyAssignmentRequiredDataId> {
    const competencyList = this.validateForm.get('competencyList')?.value as CompetencyAssignmentProposal[] ?? []
    return new Set(competencyList.map(c => c.id))
  }

  competencyCheck(id: CompetencyAssignmentRequiredDataId): boolean {
    return Array.from(this.selectedCompetencyIds).some(existingId =>
      this.compareCompetencyId(existingId, id)
    );
  }

  onWeightageInput(index: number, event: any): void {
    const input = event.target as HTMLInputElement;
    let value = input.value;

    value = value.replace(/[^0-9]/g, '');

    if (value.length > 1 && value.startsWith('0')) {
      value = value.replace(/^0+/, '');
    }

    if (value !== '') {
      const numValue = parseInt(value, 10);
      if (numValue > 100) {
        value = '100'; // Cap at max
      } else if (numValue < 1) {
        value = '1';
      }
    }

    input.value = value;

    const control = this.listOfCompetencyControl.at(index).get('weightage');
    if (control && control.value !== value) {
      control.setValue(value);
      control.markAsDirty();
    }
  }

  constructor(private translateService: TranslateService, private authService: AuthService,
    private orgChartService: OrgChartService, private modal: NzModalService,
    private roleService: RoleService, private loadingService: LoadingService,
    private jobScopeService: JobScopeService, private staffService: StaffService,
    private competencyAssignmentCollaborationService: CompetencyAssignmentCollaborationService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles(),
        jobScope: this.jobScopeService.getAllJobScopes(),
        competency: this.competencyAssignmentCollaborationService.getCompetencyRequired(this.selectedRoleCompetencyProposalId),
      }).subscribe({
        next: ({ orgChart, role, jobScope, competency }) => {
          this.orgChartList = orgChart;
          this.roleList = role;
          this.selectedRoleList = this.roleList;
          this.competencyList = competency;
          this.selectedCompetencyList = this.competencyList;
          this.jobScopeList = jobScope.map(j => j.jobScope);
          this.filteredJobScopeList = this.jobScopeList;

          this.validateForm.reset({
            department: this.selectedRecord?.orgChartId,
            role: this.selectedRecord?.roleId,
            description: this.selectedRecord?.description,
            lastUpdatedBy: this.selectedRecord?.lastUpdatedBy.email
          });

          this.selectedRecord?.assignedCompetencies.forEach(competency => {
            const isExisted = this.selectedCompetencyList.some(givenCompetency =>
              this.compareCompetencyId(givenCompetency.id, competency.id)
            );

            if (isExisted) {
              const control = this.fb.group({
                id: [competency.id, Validators.required],
                weightage: [
                  competency.weightage,
                  [
                    Validators.required,
                    Validators.min(1),
                    Validators.max(100),
                    Validators.pattern(/^[1-9][0-9]{0,2}$/),
                  ],
                ],
              }
              );
              this.listOfCompetencyControl.push(control);
            }
          });

          this.selectedRecord?.assignedJobScopes?.forEach(jobScope => {
            this.listOfJobScopeControl.push(this.fb.control(jobScope.jobScope, Validators.required));
          });

          this.initialized$.next(true);
        }
      })

    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  compareCompetencyId(o1: any, o2: any): boolean {
    if (!o1 || !o2) return o1 === o2;
    return (
      o1.competencyId === o2.competencyId &&
      o1.proposal === o2.proposal &&
      ((o1.competencyProposalId?.proposalId ?? null) === (o2.competencyProposalId?.proposalId ?? null)) &&
      ((o1.competencyProposalId?.staffId ?? null) === (o2.competencyProposalId?.staffId ?? null))
    );
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: any) {
    this.adjustDrawerWidth();
  }

  adjustDrawerWidth() {
    if (window.innerWidth <= 768) {
      this.drawerWidth = '100%';
    } else {
      this.drawerWidth = '736px';
    }
  }

  initialize(data: CompetencyAssignmentProposalOverviewData): void {
    this.adjustDrawerWidth();
    this.selectedRecord = data;
    this.selectedRoleCompetencyProposalId = data.id;
    this.fetchAllData();
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as number,
      description: null as unknown as string,
      competencyList: undefined,
      jobScopeList: undefined,
      lastUpdatedBy: null as unknown as string
    });

    this.listOfJobScopeControl.clear();
    this.listOfCompetencyControl.clear();

    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  minFormArrayLength(min: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (control instanceof FormArray) {
        return control.length >= min ? null : { minLengthArray: { requiredLength: min, actualLength: control.length } };
      }
      return null;
    };
  }

  addJobScopeField(e?: MouseEvent): void {
    e?.preventDefault();
    this.listOfJobScopeControl.push(this.fb.control(null as unknown as string, Validators.required));
  }

  addCompetencyField(e?: MouseEvent): void {
    e?.preventDefault();
    const control = this.fb.group({
      id: [null as unknown as number, Validators.required],
      weightage: [null as unknown as number, [Validators.required, Validators.min(1), Validators.max(100), Validators.pattern(/^[1-9][0-9]{0,2}$/),]]
    });
    this.listOfCompetencyControl.push(control);
  }

  removeJobScopeField(index: number, e: MouseEvent): void {
    e.preventDefault();
    this.listOfJobScopeControl.removeAt(index);
  }

  removeCompetencyField(index: number, e: MouseEvent): void {
    e.preventDefault();
    if (this.listOfCompetencyControl.length > 0) {
      this.listOfCompetencyControl.removeAt(index);
    }
  }

  onJobScopeInputChange(value: string): void {
    this.filteredJobScopeList = this.jobScopeList.filter(
      jobScope => jobScope.toLocaleLowerCase().indexOf(value.toLocaleLowerCase()) !== -1
    )
  }

  onJobScopeClick(index: number, e: MouseEvent): void {
    const value = this.listOfJobScopeControl.at(index)?.value;
    if (!value) {
      this.filteredJobScopeList = this.jobScopeList;
      return;
    }

    this.filteredJobScopeList = this.jobScopeList.filter(
      jobScope => jobScope.toLocaleLowerCase().indexOf(value.toLocaleLowerCase()) !== -1
    )
  }

  showConfirm(): void {
    if (this.validateForm.valid) {
      const confirmTitle = this.translateService.instant(this.confirmTitleKey);
      const confirmContent = this.translateService.instant(this.confirmContentKey);
      const confirmOk = this.translateService.instant(this.confirmOkKey);

      const roleConfirm = `<li>${this.selectedRecord?.roleName} ( ${this.selectedRecord?.orgChartName} )</li>`

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: `${confirmContent}<br/><br/><b><ul>${roleConfirm}</ul></b>`,
        nzOkText: confirmOk,
        nzOnOk: () => this.submit()
      });

    } else {
      this.markFormGroupDirty(this.validateForm);
    }
  }

  private markFormGroupDirty(control: AbstractControl): void {
    if (control instanceof FormGroup || control instanceof FormArray) {
      Object.values(control.controls).forEach(child => this.markFormGroupDirty(child));
    }
    control.markAsDirty();
    control.updateValueAndValidity({ onlySelf: true });
  }

  submit(): void {
    if (this.validateForm.valid) {
      this.loadingService.show();
      const orgChartId = this.validateForm.get('department')?.value;
      const roleId = this.validateForm.get('role')?.value;
      const description = this.validateForm.get('description')?.value;
      const competencyList = this.validateForm.get('competencyList')?.value as CompetencyAssignmentProposal[];
      const jobScopeList = this.validateForm.get('jobScopeList')?.value;
      const proposerList = this.validateForm.get('proposerList')?.value;
      const reviewerList = this.validateForm.get('reviewerList')?.value;
      const id = this.selectedRoleCompetencyProposalId;

      if (!id || !orgChartId || !roleId || !competencyList) return

      const requestBody: UpdateCompetencyAssignmentRequest = {
        id,
        orgChartId,
        roleId,
        description: description ?? '',
        jobScopeList: jobScopeList ?? [],
        competencyList: competencyList!
      };

      this.loadingService.hide();

      this.competencyAssignmentCollaborationService.updateCompetency(requestBody).subscribe({
        next: () => {
          this.formSubmitted.emit();

          this.uninitialize();
          this.close();
        },
        complete: () => {
          this.loadingService.hide();
        }
      })

    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
      Object.values(this.listOfJobScopeControl.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
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

