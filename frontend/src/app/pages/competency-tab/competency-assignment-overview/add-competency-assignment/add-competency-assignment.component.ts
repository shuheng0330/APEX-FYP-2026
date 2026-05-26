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

import { OrgChart } from '../../../../models/orgChart.model';
import { Role } from '../../../../models/role.model';
import { Competency } from '../../../../models/competency.model';
import { CompetencyAssignment, RoleCompetencyCreation } from '../../../../models/role-compotency.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { AuthService } from '../../../../services/auth.service';
import { RoleService } from '../../../../services/role.service';
import { LoadingService } from '../../../../services/loading.service';
import { CompetencyService } from '../../../../services/competency.service';
import { RoleCompetencyService } from '../../../../services/role-competency.service';

@Component({
  selector: 'app-add-competency-assignment',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule, NzEmptyModule],
  templateUrl: './add-competency-assignment.component.html',
  styleUrl: './add-competency-assignment.component.scss'
})
export class AddCompetencyAssignmentComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.COMPETENCY.ASSIGNMENT.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.COMPETENCY.ASSIGNMENT.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.COMPETENCY.ASSIGNMENT.ADD.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  roleList: Role[] = [];
  selectedRoleList = [...this.roleList];
  competencyList: Competency[] = [];
  selectedCompetencyList = [...this.competencyList];

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    role: [null as unknown as number, [Validators.required]],
    competencyList: this.fb.array<FormGroup>([], [this.minFormArrayLength(1)])
  });

  get listOfControl(): FormArray<FormGroup> {
    return this.validateForm.get('competencyList') as FormArray<FormGroup>;
  }

  get selectedCompetencyIds(): Set<number> {
    const competencyList = this.validateForm.get('competencyList')?.value as CompetencyAssignment[] ?? []
    return new Set(competencyList.map(c => c.competencyId))
  }

  competencyCheck(competencyId: number): boolean {
    return this.selectedCompetencyIds.has(competencyId)
  }

  minFormArrayLength(min: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (control instanceof FormArray) {
        return control.length >= min ? null : { minLengthArray: { requiredLength: min, actualLength: control.length } };
      }
      return null;
    };
  }

  constructor(private translateService: TranslateService, private authService: AuthService,
    private orgChartService: OrgChartService, private modal: NzModalService,
    private roleService: RoleService, private loadingService: LoadingService,
    private competencyService: CompetencyService, private roleCompetencyService: RoleCompetencyService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles(),
        competency: this.competencyService.getAllCompetencies()
      }).subscribe({
        next: ({ orgChart, role, competency }) => {
          this.orgChartList = orgChart;
          this.roleList = role;
          this.competencyList = competency;
          this.selectedCompetencyList = this.competencyList;

          this.initialized$.next(true);
        }
      })
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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

  initialize(): void {
    this.adjustDrawerWidth();
    this.fetchAllData();
    this.addField();
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as number,
    });

    this.listOfControl.clear();

    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  addField(e?: MouseEvent): void {
    const control = this.fb.group({
      competencyId: [null as unknown as number, Validators.required],
      weightage: [null as unknown as number, [Validators.required, Validators.min(1), Validators.max(100), Validators.pattern(/^[1-9][0-9]{0,2}$/),]]
    });
    this.listOfControl.push(control);
  }

  removeField(index: number, event: MouseEvent): void {
    event.preventDefault();
    if (this.listOfControl.length > 0) {
      this.listOfControl.removeAt(index);
    }
  }

  onDepartmentChange(departmentId: number): void {
    if (departmentId != null) {
      this.selectedRoleList = this.roleList.filter(
        (role) => role.orgChart.id! === departmentId
      );

      const isRoleSameDepartment = this.selectedRoleList.some(
        (selectedRole) => selectedRole.id! === this.validateForm.get('role')?.value
      );

      if (!isRoleSameDepartment) {
        this.validateForm.get('role')?.reset();
      }

    } else {
      this.selectedRoleList = [];
      this.validateForm.get('role')?.reset();
    }
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

    const control = this.listOfControl.at(index).get('weightage');
    if (control && control.value !== value) {
      control.setValue(value);
      control.markAsDirty();
    }
  }

  showConfirm(): void {
    if (this.validateForm.valid) {
      const confirmTitle = this.translateService.instant(this.confirmTitleKey);
      const confirmContent = this.translateService.instant(this.confirmContentKey);
      const confirmOk = this.translateService.instant(this.confirmOkKey);

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: confirmContent,
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
      const competencyList = this.validateForm.get('competencyList')?.value as CompetencyAssignment[];

      if (!orgChartId || !roleId || !competencyList) return;

      const requestBody: RoleCompetencyCreation = {
        roleId,
        competencyAssignment: competencyList || []
      };

      this.roleCompetencyService.createRoleCompetency(requestBody).subscribe({
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
      this.markFormGroupDirty(this.validateForm);
    }
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
