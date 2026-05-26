import { Component, EventEmitter, HostListener, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, forkJoin, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzEmptyModule } from 'ng-zorro-antd/empty';

import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { OrgChartService } from '../../../../services/orgChart.service';
import { RoleService } from '../../../../services/role.service';
import { StaffService } from '../../../../services/staff.service';
import { RoleAssignmentService } from '../../../../services/role-assignment.service';

import { Staff } from '../../../../models/staff.model';
import { OrgChart } from '../../../../models/orgChart.model';
import { Role } from '../../../../models/role.model';
import { RoleAssignmentCreation } from '../../../../models/staff-role.model';

@Component({
  selector: 'app-add-role-assignment',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzTagModule, NzEmptyModule],
  templateUrl: './add-role-assignment.component.html',
  styleUrl: './add-role-assignment.component.scss'
})
export class AddRoleAssignmentComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.ROLE.ASSIGNMENT.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.ROLE.ASSIGNMENT.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.ROLE.ASSIGNMENT.ADD.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  inputVisible = false;
  inputValue = '';

  orgChartList: OrgChart[] = [];
  roleList: Role[] = [];
  selectedRoleList = [...this.roleList];
  staffList: Staff[] = []
  selectedStaffList = [...this.staffList];

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    role: [null as unknown as number, null],
    staff: this.fb.control<string[]>([])
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private orgChartService: OrgChartService,
    private roleService: RoleService, private loadingService: LoadingService,
    private staffService: StaffService, private roleAssignmentService: RoleAssignmentService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles(),
        staff: this.staffService.getAllStaff()
      }).subscribe({
        next: ({ orgChart, role, staff }) => {
          this.orgChartList = orgChart;
          this.roleList = role;
          this.staffList = staff;
          this.selectedStaffList = this.staffList;

          this.initialized$.next(true);
        }
      })
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

  staffSearch(searchVal: string): void {
    this.selectedStaffList = [];
    if (!searchVal) {
      this.selectedStaffList = [...this.staffList];
    } else {
      this.selectedStaffList = this.staffList.filter((item: Staff) => {
        const matchesStaffName = (item.name ?? '').toLowerCase().includes(searchVal);
        const matchesStaffEmail = item.email.toLowerCase().includes(searchVal);

        return matchesStaffName || matchesStaffEmail;
      });
    }

    this.loadingService.hide();
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
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as number,
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

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: confirmContent,
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

      const roleId = this.validateForm.get('role')?.value;
      const staffIds = this.validateForm.get('staff')?.value;

      if (!roleId || !staffIds) return;

      const requestBody: RoleAssignmentCreation = {
        roleId,
        staffIds
      };

      this.roleAssignmentService.createRoleAssignment(requestBody).subscribe({
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
