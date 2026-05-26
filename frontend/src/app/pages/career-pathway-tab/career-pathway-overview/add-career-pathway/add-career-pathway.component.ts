import { Component, EventEmitter, HostListener, inject, model, Output } from '@angular/core';
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
import { NzFlexModule } from 'ng-zorro-antd/flex';

import { OrgChart } from '../../../../models/orgChart.model';
import { Role } from '../../../../models/role.model';
import { CreateCareerPathwayRequest, ParentChildRoleId } from '../../../../models/careerPathway.model';


import { OrgChartService } from '../../../../services/orgChart.service';
import { AuthService } from '../../../../services/auth.service';
import { RoleService } from '../../../../services/role.service';
import { LoadingService } from '../../../../services/loading.service';
import { TrackService } from '../../../../services/track.service';
import { CareerPathwayService } from '../../../../services/careerPathway.service';

@Component({
  selector: 'app-add-career-pathway',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule, NzEmptyModule, NzToolTipModule, NzFlexModule],
  templateUrl: './add-career-pathway.component.html',
  styleUrl: './add-career-pathway.component.scss'
})
export class AddCareerPathwayComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.CAREER_PATHWAY.OVERVIEW.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.CAREER_PATHWAY.OVERVIEW.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.CAREER_PATHWAY.OVERVIEW.ADD.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  roleList: Role[] = [];
  selectedRoleList = [...this.roleList];
  trackList: string[] = [];

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    name: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string,],
    trackList: this.fb.control<string[]>([]),
    rootRole: [null as unknown as number, [Validators.required]],
    roleList: this.fb.array<FormGroup>([])
  });

  listOfRoleControl = this.validateForm.get('roleList') as FormArray<FormGroup>;

  constructor(private translateService: TranslateService, private authService: AuthService,
    private orgChartService: OrgChartService, private modal: NzModalService,
    private roleService: RoleService, private loadingService: LoadingService,
    private trackService: TrackService, private careerPathwayService: CareerPathwayService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles(),
        track: this.trackService.getAll()
      }).subscribe({
        next: ({ orgChart, role, track }) => {
          this.orgChartList = orgChart;
          this.roleList = role;
          this.trackList = track.flatMap(track => track.track);

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
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      name: null as unknown as string,
      description: null as unknown as string,
      rootRole: null as unknown as number
    });

    this.listOfRoleControl.clear();

    this.initialized$.next(false);
  }

  open(): void {
    this.visible$.next(true);
  }

  close(): void {
    this.uninitialize();
    this.visible$.next(false);
  }

  addRoleField(e?: MouseEvent): void {
    const control = this.fb.group({
      parentId: [null as unknown as number, Validators.required],
      childId: [null as unknown as number, Validators.required]
    });
    this.listOfRoleControl.push(control);
  }

  removeRoleField(index: number, event: MouseEvent): void {
    event.preventDefault();
    if (this.listOfRoleControl.length > 0) {
      this.listOfRoleControl.removeAt(index);
    }
  }

  onDepartmentChange(departmentId?: number): void {
    if (departmentId != null) {
      this.selectedRoleList = this.roleList.filter(
        (role) => role.orgChart.id! === departmentId
      );

      const isRoleSameDepartment = this.selectedRoleList.some(
        (selectedRole) => selectedRole.id! === this.validateForm.get('role')?.value
      );

      if (!isRoleSameDepartment) {
        this.validateForm.get('rootRole')?.reset();
        this.listOfRoleControl.clear();
      }

    } else {
      this.selectedRoleList = [];
      this.validateForm.get('rootRole')?.reset();
      this.listOfRoleControl.clear();
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

      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });

      Object.values(this.listOfRoleControl.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
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
      const name = this.validateForm.get('name')?.value;
      const description = this.validateForm.get('description')?.value;
      const trackList = this.validateForm.get('trackList')?.value as string[];
      const rootRoleId = this.validateForm.get('rootRole')?.value;
      const parentChildRoleId = this.validateForm.get('roleList')?.value as ParentChildRoleId[];

      if (!orgChartId || !name || !rootRoleId) return;

      const parentChildMap: Record<number, number[]> = {};

      parentChildRoleId.forEach(({ parentId, childId }) => {
        if (!parentChildMap[parentId]) {
          parentChildMap[parentId] = [];
        }
        parentChildMap[parentId].push(childId);
      });

      const requestBody: CreateCareerPathwayRequest = {
        orgChartId,
        careerPathwayName: name,
        description: description ?? "",
        track: trackList ?? [],
        rootRoleId,
        parentChildRoleId: parentChildMap ?? null
      };

      this.careerPathwayService.create(requestBody).subscribe({
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

      Object.values(this.validateForm.controls).forEach(control => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });

      Object.values(this.listOfRoleControl.controls).forEach(control => {
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


