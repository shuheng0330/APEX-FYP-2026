import { Component, EventEmitter, HostListener, inject, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzEmptyModule } from 'ng-zorro-antd/empty';


import { TranslatedAuthority } from '../../../../models/access-control.model';
import { OrgChart } from '../../../../models/orgChart.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { Role } from '../../../../models/role.model';
import { RoleService } from '../../../../services/role.service';
import { BehaviorSubject, forkJoin, Subject } from 'rxjs';
import { AuthorityService } from '../../../../services/authority.service';
import { LoadingService } from '../../../../services/loading.service';

@Component({
  selector: 'app-add-access-control',
  imports: [CommonModule, ReactiveFormsModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule,
    NzSelectModule, TranslateModule, NzCheckboxModule, FormsModule, NzIconModule,
    NzToolTipModule, NzModalModule, NzEmptyModule],
  templateUrl: './add-access-control.component.html',
  styleUrl: './add-access-control.component.scss'
})

export class AddAccessControlComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.ADD.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  translatedAuthorityList: TranslatedAuthority[] = [];
  orgChartList: OrgChart[] = [];
  allRoleList: Role[] = [];
  selectedDepartmentRoleList: Role[] = [];

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    role: [null as unknown as number, [Validators.required]],
    authorities: [null as unknown as number[], [Validators.required]]
  });

  constructor(private orgChartService: OrgChartService, private roleService: RoleService,
    private authorityService: AuthorityService, private loadingService: LoadingService,
    private modal: NzModalService, private translateService: TranslateService
  ) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles()
      }).subscribe({
        next: ({ orgChart, role }) => {
          this.orgChartList = orgChart;
          this.allRoleList = role;
          this.initialized$.next(true);
        }
      })
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onDepartmentChange(departmentId: number): void {
    if (departmentId != null) {
      this.selectedDepartmentRoleList = this.allRoleList.filter(
        (role) => role.orgChart.id === departmentId
      );

      const isSameDepartment = this.selectedDepartmentRoleList.some(
        (selectedRole) => selectedRole.id! === this.validateForm.get('role')?.value
      );

      if (!isSameDepartment) {
        this.validateForm.get('role')?.reset();
      }

    } else {
      this.selectedDepartmentRoleList = [];
      this.validateForm.get('role')?.reset();
    }
  }


  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
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
      authorities: null as unknown as number[]
    });
    this.selectedDepartmentRoleList = [];
    this.initialized$.next(false);
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
      const orgChartId = this.validateForm.get('department')?.value;
      const roleId = this.validateForm.get('role')?.value;
      const selectedAuthorities = this.validateForm.get('authorities')?.value;

      if (!orgChartId)
        return;

      if (!roleId)
        return;

      if (!selectedAuthorities)
        return;

      this.authorityService.grantAccess(orgChartId!, roleId!, selectedAuthorities!)
        .subscribe({
          next: () => {
            this.formSubmitted.emit();

            this.uninitialize();
            this.close();
          },
          error: err => {
          },
          complete: () => this.loadingService.hide()
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
}
