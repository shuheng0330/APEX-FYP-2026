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
import { StaffService } from '../../../../services/staff.service';

import { Staff } from '../../../../models/staff.model';
import { OrgChart } from '../../../../models/orgChart.model';
import { CareerPathway, UpdateCareerPathwayAssignmentRequest } from '../../../../models/careerPathway.model';
import { CareerPathwayService } from '../../../../services/careerPathway.service';
import { CareerPathwayAssignmentService } from '../../../../services/careerPathwayAssignment.service';

@Component({
  selector: 'app-add-career-pathway-assignment',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzTagModule, NzEmptyModule],
  templateUrl: './add-career-pathway-assignment.component.html',
  styleUrl: './add-career-pathway-assignment.component.scss'
})
export class AddCareerPathwayAssignmentComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.CAREER_PATHWAY.ASSIGNMENT.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.CAREER_PATHWAY.ASSIGNMENT.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.CAREER_PATHWAY.ASSIGNMENT.ADD.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  inputVisible = false;
  inputValue = '';

  orgChartList: OrgChart[] = [];
  careerPathwayList: CareerPathway[] = [];
  selectedCareerPathwayList = [...this.careerPathwayList];
  staffList: Staff[] = []
  filteredStaffList = [...this.staffList];
  selectedStaffList = [...this.filteredStaffList];

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    department: [null as unknown as number, [Validators.required]],
    careerPathway: [null as unknown as number, null],
    staff: this.fb.control<string[]>([])
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private orgChartService: OrgChartService, private loadingService: LoadingService,
    private staffService: StaffService, private careerPathwayAssignmentService: CareerPathwayAssignmentService,
    private careerPathwayService: CareerPathwayService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        orgChart: this.orgChartService.getAllOrgChart(),
        careerPathway: this.careerPathwayService.getAll(),
        staff: this.staffService.getAllStaff()
      }).subscribe({
        next: ({ orgChart, careerPathway, staff }) => {
          this.orgChartList = orgChart;
          this.careerPathwayList = careerPathway;
          this.staffList = staff;

          this.selectedCareerPathwayList = [];
          this.selectedStaffList = []

          this.initialized$.next(true);
        }
      })
    }
  }

  onDepartmentChange(departmentId: number): void {
    if (departmentId != null) {
      this.filteredStaffList = [];
      this.filteredStaffList = [];

      this.selectedCareerPathwayList = this.careerPathwayList.filter(
        (careerPathway) => careerPathway.orgChart.id === departmentId
      );

      this.filteredStaffList = this.staffList.filter(
        (staff) => staff.role && staff.role.orgChart.id === departmentId
      )
      this.selectedStaffList = this.filteredStaffList;

      const isCareerPathwaySameDepartment = this.selectedCareerPathwayList.some(
        (selectedCareerPathway) => selectedCareerPathway.id! === this.validateForm.get('careerPathway')?.value
      );

      if (!isCareerPathwaySameDepartment) {
        this.validateForm.get('careerPathway')?.reset();
      }

      const isStaffSameDepartment = this.filteredStaffList.some(
        (selectedStaff) => this.validateForm.get('staff')?.value.some(
          (seletecStaffId) => selectedStaff.id! === seletecStaffId
        )
      );

      if (!isStaffSameDepartment) {
        this.validateForm.get('staff')?.reset();
      }

    } else {
      this.selectedCareerPathwayList = [];
      this.filteredStaffList = [];
      this.selectedStaffList = this.filteredStaffList;
      this.validateForm.get('careerPathway')?.reset();
      this.validateForm.get('staff')?.reset();
    }
  }

  staffSearch(searchVal: string): void {
    const departmentId = this.validateForm.get('department')?.value;
    if (departmentId) {
      if (!searchVal) {
        this.selectedStaffList = [...this.filteredStaffList];
      } else {
        this.selectedStaffList = this.filteredStaffList.filter((item: Staff) => {
          const matchesStaffName = (item.name ?? '').toLowerCase().includes(searchVal);
          const matchesStaffEmail = item.email.toLowerCase().includes(searchVal);
          return matchesStaffName || matchesStaffEmail;
        });
      }

      this.loadingService.hide();
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
      careerPathway: null as unknown as number,
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

      const careerPathwayId = this.validateForm.get('careerPathway')?.value;
      const staffIds = this.validateForm.get('staff')?.value;

      if (!careerPathwayId || !staffIds) return;

      const requestBody: UpdateCareerPathwayAssignmentRequest = {
        careerPathwayId: careerPathwayId,
        staffIds
      };

      this.careerPathwayAssignmentService.assign(requestBody).subscribe({
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