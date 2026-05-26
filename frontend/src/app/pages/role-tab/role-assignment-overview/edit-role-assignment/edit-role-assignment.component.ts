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
import { RoleAssignmentOverviewData, RoleAssignmentCreation } from '../../../../models/staff-role.model';

@Component({
  selector: 'app-edit-role-assignment',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzTagModule, NzEmptyModule],
  templateUrl: './edit-role-assignment.component.html',
  styleUrl: './edit-role-assignment.component.scss'
})
export class EditRoleAssignmentComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.ROLE.ASSIGNMENT.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.ROLE.ASSIGNMENT.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.ROLE.ASSIGNMENT.EDIT.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  inputVisible = false;
  inputValue = '';

  staffList: Staff[] = []
  selectedStaffList = [...this.staffList];
  selectedOrgChartId: number = NaN;
  selectedOrgChartName: string = '';
  selectedRoleId: number = NaN;
  selectedRoleName: string = '';

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
        staff: this.staffService.getAllStaff()
      }).subscribe({
        next: ({ staff }) => {
          this.staffList = staff;
          this.selectedStaffList = this.staffList;

          this.initialized$.next(true);
        }
      })
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

  initialize(data: RoleAssignmentOverviewData): void {
    this.adjustDrawerWidth();
    this.fetchAllData();

    this.selectedOrgChartId = data.orgChartId;
    this.selectedOrgChartName = data.orgChartName;
    this.selectedRoleId = data.roleId;
    this.selectedRoleName = data.roleName;

    const selectedStaffId = data.staffList.map(
      (staff) => staff.id
    );

    this.validateForm.reset({
      department: data.orgChartId,
      role: data.roleId,
      staff: selectedStaffId || []
    })
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as number,
      staff: []
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

      const roleName = this.selectedRoleName;
      const departmentName = this.selectedOrgChartName;

      const roleConfirm = `<li>${roleName} (${departmentName})</li>`

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: `${confirmContent}<br/><br/><b><ul>${roleConfirm}</ul></b>`,
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

      if (!roleId) return;

      const requestBody: RoleAssignmentCreation = {
        roleId,
        staffIds: staffIds || []
      };

      this.roleAssignmentService.updateRoleAssignment(requestBody).subscribe({
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
