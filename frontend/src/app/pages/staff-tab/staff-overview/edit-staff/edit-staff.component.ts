import { Component, EventEmitter, HostListener, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, forkJoin, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, FormArray, FormControl, ValidatorFn, AbstractControl, ValidationErrors } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { OrgChart } from '../../../../models/orgChart.model';
import { StaffOverviewData, StaffEdit, CareerPathwayDto, RoleDto } from '../../../../models/staff.model';
import { Staff } from '../../../../models/staff.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { StaffService } from '../../../../services/staff.service';

@Component({
  selector: 'app-edit-staff',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule, NzEmptyModule,
    NzToolTipModule],
  templateUrl: './edit-staff.component.html',
  styleUrl: './edit-staff.component.scss'
})
export class EditStaffComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.STAFF.STAFF_OVERVIEW.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.STAFF.STAFF_OVERVIEW.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.STAFF.STAFF_OVERVIEW.EDIT.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  roleList: RoleDto[] = [];
  selectedRoleList: RoleDto[] = [];
  careerPathwayList: CareerPathwayDto[] = [];
  selectedCareerPathwayList: CareerPathwayDto[] = [];
  staffList: Staff[] = []

  selectedStaffId: string = '';
  selectedName: string = '';
  selectedEmail: string = '';
  selectedCareerPathwayId?: number;

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();

  careerPathwayMatchValidator = (control: AbstractControl): ValidationErrors | null => {
    if (!control.parent) {
      return null;
    }

    const roleId = control.parent.get('role')?.value;
    const careerPathwayId = control.value;

    if (!roleId || !careerPathwayId) {
      return null;
    }

    const selectedPathway = this.careerPathwayList.find(
      (cp) => cp.careerPathwayId === careerPathwayId
    );

    const isValid = selectedPathway?.childrenRoleIds?.includes(roleId);

    return isValid ? null : { roleMismatch: true };
  };

  validateForm = this.fb.group({
    email: [null as unknown as string, [Validators.required]],
    name: [null as unknown as string, null],
    accountStatus: [true as boolean, [Validators.required]],
    department: [null as unknown as number, null],
    role: [null as unknown as number, null],
    careerPathway: [null as unknown as number, [this.careerPathwayMatchValidator]],
    manager: [null as unknown as string, null],
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private staffService: StaffService, private orgChartService: OrgChartService,
    private loadingService: LoadingService) { }

  fetchAllData(data: StaffOverviewData): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        orgChartRoleCareerPathway: this.staffService.getAddStaffInfo(),
        staffs: this.staffService.getAllStaff()
      }).subscribe({
        next: ({ orgChart, orgChartRoleCareerPathway, staffs }) => {
          this.orgChartList = orgChart
          this.roleList = orgChartRoleCareerPathway.roles;
          this.careerPathwayList = orgChartRoleCareerPathway.careerPathways;
          this.staffList = staffs;

          this.validateForm.get('role')?.valueChanges.subscribe(() => {
            this.validateForm.get('careerPathway')?.updateValueAndValidity();
          });

          this.selectedStaffId = data.staffId;
          this.selectedName = data.staffName || '';
          this.selectedEmail = data.staffEmail;
          this.selectedCareerPathwayId = data.careerPathwayId;

          this.onDepartmentChange(data.orgChartId!);

          this.validateForm.reset({
            accountStatus: data.accountStatus,
            email: data.staffEmail,
            name: data.staffName || null as unknown as string,
            department: data.orgChartId || null as unknown as number,
            role: data.roleId || null as unknown as number,
            careerPathway: data.careerPathwayId || null as unknown as number,
            manager: data.managerId || null as unknown as string,
          });

          const careerPathwayControl = this.validateForm.get('careerPathway');
          if (careerPathwayControl) {
            careerPathwayControl.markAsTouched();
            careerPathwayControl.updateValueAndValidity();
          }

          this.initialized$.next(true);
        }
      })
    }
  }

  onDepartmentChange(departmentId: number): void {
    if (departmentId != null) {
      this.selectedRoleList = this.roleList.filter(
        (role) => role.orgChartId === departmentId
      );

      const isRoleSameDepartment = this.selectedRoleList.some(
        (selectedRole) => selectedRole.roleId === this.validateForm.get('role')?.value
      );

      if (!isRoleSameDepartment) {
        this.validateForm.get('role')?.reset();
      }

      this.selectedCareerPathwayList = this.careerPathwayList.filter(
        (careerPathway) => careerPathway.orgChartId === departmentId ||
          careerPathway.careerPathwayId === this.selectedCareerPathwayId
      );

      const isCareerPathwaySameDepartment = this.selectedCareerPathwayList.some(
        (selectedCareerPathway) => selectedCareerPathway.careerPathwayId === this.validateForm.get('careerPathway')?.value
      );

      if (!isCareerPathwaySameDepartment) {
        this.validateForm.get('careerPathway')?.reset();
      }

    } else {
      this.selectedRoleList = [];
      this.validateForm.get('role')?.reset();

      this.selectedCareerPathwayList = [];
      this.validateForm.get('careerPathway')?.reset();
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

  initialize(data: StaffOverviewData): void {
    this.adjustDrawerWidth();
    this.fetchAllData(data);
  }

  uninitialize(): void {
    this.validateForm.reset({
      accountStatus: true as unknown as boolean,
      email: null as unknown as string,
      name: null as unknown as string,
      department: null as unknown as number,
      role: null as unknown as number,
      careerPathway: null as unknown as number,
      manager: null as unknown as string,
    });
    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  showConfirm(): void {
    if (this.validateForm.valid) {
      const confirmTitle = this.translateService.instant(this.confirmTitleKey);
      const confirmContent = this.translateService.instant(this.confirmContentKey);
      const confirmOk = this.translateService.instant(this.confirmOkKey);

      let staffConfirm = `<li>${this.selectedEmail}`

      if (this.selectedName) {
        staffConfirm += `<br/>(${this.selectedName})`
      }

      staffConfirm += `</li>`

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: `${confirmContent}<br/><br/><b><ul>${staffConfirm}</ul></b>`,
        nzOkText: confirmOk,
        nzOnOk: () => this.submit()
      });

    } else {
      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
    }
  }

  submit(): void {
    if (this.validateForm.valid) {
      this.loadingService.show();
      const email = this.validateForm.get('email')?.value.trim().toLocaleLowerCase();
      const name = this.validateForm.get('name')?.value;
      const role = this.validateForm.get('role')?.value;
      const careerPathway = this.validateForm.get('careerPathway')?.value;
      const manager = this.validateForm.get('manager')?.value;
      const accountStatus = this.validateForm.get('accountStatus')?.value;

      if (!email || !this.selectedStaffId) return;

      const requestBody: StaffEdit = {
        staffId: this.selectedStaffId,
        email,
        name: name || '',
        roleId: role || undefined,
        careerPathwayId: careerPathway || undefined,
        managerId: manager || undefined,
        accountStatus: accountStatus ?? true
      };

      this.staffService.updateStaff(requestBody).subscribe({
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
    }
  }

  hasAccess(requiredRoles: string[]): boolean {
    if (!requiredRoles) {
      return true;
    }
    return requiredRoles.some(role => this.authService.hasRole(role));
  }
}
