import { Component, EventEmitter, HostListener, inject, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, AbstractControl, ValidationErrors } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';

import { TranslatedAuthority } from '../../../../models/access-control.model';

import { Role } from '../../../../models/role.model';
import { BehaviorSubject, Subject } from 'rxjs';
import { AuthorityService } from '../../../../services/authority.service';
import { LoadingService } from '../../../../services/loading.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-edit-access-control',
  imports: [CommonModule, ReactiveFormsModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule,
    NzSelectModule, TranslateModule, NzCheckboxModule, FormsModule, NzIconModule,
    NzToolTipModule, NzModalModule],
  templateUrl: './edit-access-control.component.html',
  styleUrl: './edit-access-control.component.scss'
})
export class EditAccessControlComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.STAFF.ACCESS_CONTROL_OVERVIEW.EDIT.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  translatedAuthorityList: TranslatedAuthority[] = [];
  selectedDepartmentRoleList: Role[] = [];

  selectedOrgChartId: number = NaN;
  selectedOrgChartName: string = '';
  selectedRoleId: number = NaN;
  selectedRoleName: string = '';
  selectedAuthorities: string[] = [];

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    role: [null as unknown as number, [Validators.required]],
    authorities: [null as unknown as string[], [Validators.required]]
  });

  constructor(private authorityService: AuthorityService, private loadingService: LoadingService,
    private modal: NzModalService, private translateService: TranslateService,
    private authService: AuthService
  ) { }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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
    this.validateForm.reset({
      department: this.selectedOrgChartId,
      role: this.selectedRoleId,
      authorities: this.selectedAuthorities
    });
    this.initialized$.next(true);
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as number,
      authorities: null as unknown as string[]
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

      this.loadingService.show()
      this.authorityService.editGrantedAccess(orgChartId!, roleId!, selectedAuthorities!)
        .subscribe({
          next: () => {
            this.formSubmitted.emit();

            this.authService.refresh(false).subscribe();
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
