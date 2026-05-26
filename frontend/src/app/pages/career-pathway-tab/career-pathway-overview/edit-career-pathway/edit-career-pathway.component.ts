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
import { NzFlexModule } from 'ng-zorro-antd/flex';

import { OrgChart } from '../../../../models/orgChart.model';
import { Role } from '../../../../models/role.model';
import { CareerPathwayOverview, CreateCareerPathwayRequest, ParentChildRoleId } from '../../../../models/careerPathway.model';

import { OrgChartService } from '../../../../services/orgChart.service';
import { AuthService } from '../../../../services/auth.service';
import { RoleService } from '../../../../services/role.service';
import { LoadingService } from '../../../../services/loading.service';
import { TrackService } from '../../../../services/track.service';
import { CareerPathwayService } from '../../../../services/careerPathway.service';
import { OrgChartNode } from 'ngx-interactive-org-chart';

@Component({
  selector: 'app-edit-career-pathway',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule, NzEmptyModule, NzToolTipModule, NzFlexModule],
  templateUrl: './edit-career-pathway.component.html',
  styleUrl: './edit-career-pathway.component.scss'
})
export class EditCareerPathwayComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.CAREER_PATHWAY.OVERVIEW.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.CAREER_PATHWAY.OVERVIEW.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.CAREER_PATHWAY.OVERVIEW.EDIT.CONFIRM.OK";

  orgChartList: OrgChart[] = [];
  roleList: Role[] = [];
  selectedRoleList = [...this.roleList];
  trackList: string[] = [];

  selectedCareerPathwayId?: number;
  selectedRecord?: CareerPathwayOverview;

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

  fetchAllData(selectedData: CareerPathwayOverview): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        role: this.roleService.getAllRoles(),
        track: this.trackService.getAll()
      }).subscribe({
        next: ({ orgChart, role, track }) => {
          this.orgChartList = orgChart;

          this.roleList = role;
          const orgChartId = Number(selectedData.orgChartId);
          this.selectedRoleList = this.roleList.filter(r => Number(r.orgChart.id) === orgChartId);
          this.trackList = track.flatMap(track => track.track);

          this.validateForm.reset({
            department: selectedData.orgChartId,
            name: selectedData.careerPathwayName,
            description: selectedData.careerPathwayDescription ?? '',
            trackList: selectedData.trackDtoList.flatMap((track) => track.track) || [],
            rootRole: Number(selectedData.graph.id!),
          });

          this.traverseOrgChart(selectedData.graph, (parentId, childId) => {
            const control = this.fb.group({
              parentId: [Number(parentId), Validators.required],
              childId: [Number(childId), Validators.required]
            });
            this.listOfRoleControl.push(control);
          });

          this.validateForm.get('department')?.disable();
          this.initialized$.next(true);
        }
      })
    }
  }

  private traverseOrgChart(
    node: OrgChartNode,
    callback: (parentId: string, childId: string) => void
  ): void {
    if (!node.children) return;

    node.children.forEach(child => {
      callback(node.id!, child.id!);
      this.traverseOrgChart(child, callback);
    });
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

  initialize(selectedData?: CareerPathwayOverview): void {
    this.adjustDrawerWidth();
    if (selectedData) {
      this.selectedCareerPathwayId = selectedData.careerPathwayId;
      this.selectedRecord = selectedData;
      this.fetchAllData(selectedData);
      this.visible$.next(true);
    }
  }

  uninitialize(): void {
    this.validateForm.reset({
      department: null as unknown as number,
      name: null as unknown as string,
      description: null as unknown as string,
      rootRole: null as unknown as number,
      trackList: null as unknown as []
    });

    this.selectedCareerPathwayId = undefined;
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

      const careerPathwayName = this.validateForm.get('name')?.value;
      const careerPathwayConfirm = `<li>${careerPathwayName} ( ${this.selectedRecord?.orgChartName} )</li>`

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: `${confirmContent}<br/><br/><b><ul>${careerPathwayConfirm}</ul></b>`,
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
      const name = this.validateForm.get('name')?.value;
      const description = this.validateForm.get('description')?.value;
      const trackList = this.validateForm.get('trackList')?.value as string[];
      const rootRoleId = this.validateForm.get('rootRole')?.value;
      const parentChildRoleId = this.validateForm.get('roleList')?.value as ParentChildRoleId[];

      if (!this.selectedCareerPathwayId || !orgChartId || !name || !rootRoleId) return;

      const parentChildMap: Record<number, number[]> = {};

      parentChildRoleId.forEach(({ parentId, childId }) => {
        parentId = Number(parentId);
        childId = Number(childId);
        if (!parentChildMap[parentId]) {
          parentChildMap[parentId] = [];
        }
        parentChildMap[parentId].push(childId);
      });

      const requestBody: CreateCareerPathwayRequest = {
        careerPathwayId: this.selectedCareerPathwayId!,
        orgChartId: orgChartId,
        careerPathwayName: name,
        description: description ?? "",
        track: trackList ?? [],
        rootRoleId: Number(rootRoleId),
        parentChildRoleId: parentChildMap ?? null
      };

      this.careerPathwayService.edit(requestBody).subscribe({
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


