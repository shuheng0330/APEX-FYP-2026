import { Component, EventEmitter, HostListener, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, forkJoin, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, FormArray, FormControl } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzAutocompleteModule } from 'ng-zorro-antd/auto-complete';

import { OrgChart } from '../../../../models/orgChart.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { JobScopeService } from '../../../../services/jobScope.service';
import { AuthService } from '../../../../services/auth.service';
import { RoleService } from '../../../../services/role.service';
import { Role, RoleCreation } from '../../../../models/role.model';
import { LoadingService } from '../../../../services/loading.service';

@Component({
  selector: 'app-add-role',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule],
  templateUrl: './add-role.component.html',
  styleUrl: './add-role.component.scss'
})
export class AddRoleComponent {
  @Output() formSubmitted = new EventEmitter<void>();
  @Output() createdRole = new EventEmitter<Role>();
  @Input() isAddingRoleCompetencies: boolean = false;

  private readonly confirmTitleKey = "PAGE.ROLE.OVERVIEW.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.ROLE.OVERVIEW.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.ROLE.OVERVIEW.ADD.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  jobScopeList: string[] = [];
  filteredJobScopeList = [...this.jobScopeList];

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    role: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
    visibility: [false as boolean, [Validators.required]],
    jobScopeList: this.fb.array<string>([])
  });

  listOfControl = this.validateForm.get('jobScopeList') as FormArray<FormControl<string>>;

  constructor(private translateService: TranslateService, private authService: AuthService,
    private orgChartService: OrgChartService, private modal: NzModalService,
    private roleService: RoleService, private loadingService: LoadingService,
    private jobScopeService: JobScopeService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        jobScope: this.jobScopeService.getAllJobScopes()
      }).subscribe({
        next: ({ orgChart, jobScope }) => {
          this.orgChartList = orgChart;

          this.jobScopeList = jobScope.map((jobScope) =>
            jobScope.jobScope
          );
          this.filteredJobScopeList = this.jobScopeList;

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

    if (!this.authService.hasRole('CAN_VIEW_INVISIBLE_ROLE')) {
      this.validateForm.get('visibility')?.setValue(true);
    }
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      role: null as unknown as string,
      description: null as unknown as string,
      visibility: false as boolean,
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
    e?.preventDefault();
    this.listOfControl.push(this.fb.control('', Validators.required));
  }

  removeField(index: number, e: MouseEvent): void {
    e.preventDefault();
    this.listOfControl.removeAt(index);
  }

  onJobScopeInputChange(value: string): void {
    this.filteredJobScopeList = this.jobScopeList.filter(
      jobScope => jobScope.toLocaleLowerCase().indexOf(value.toLocaleLowerCase()) !== -1
    )
  }

  onJobScopeClick(index: number, e: MouseEvent): void {
    if (!this.listOfControl.at(index)?.value) {
      this.filteredJobScopeList = this.jobScopeList;
      return;
    }

    const value = this.listOfControl.at(index)?.value;
    this.filteredJobScopeList = this.jobScopeList.filter(
      jobScope => jobScope.toLocaleLowerCase().indexOf(value.toLocaleLowerCase()) !== -1
    )
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
      Object.values(this.listOfControl.controls).forEach(control => {
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
      const orgChartId = this.validateForm.get('department')?.value;
      const roleName = this.validateForm.get('role')?.value;
      const visibility = this.validateForm.get('visibility')?.value;
      const description = this.validateForm.get('description')?.value;
      const jobScopeList = this.validateForm.get('jobScopeList')?.value;

      if (!orgChartId || !roleName) return;

      const requestBody: RoleCreation = {
        orgChartId,
        roleName,
        visibility: visibility ?? false,
        description: description || '',
        jobScopeList: jobScopeList || []
      };

      this.roleService.createRole(requestBody).subscribe({
        next: (response) => {
          this.formSubmitted.emit();
          this.createdRole.emit(response.createdRole);

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
      Object.values(this.listOfControl.controls).forEach(control => {
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
