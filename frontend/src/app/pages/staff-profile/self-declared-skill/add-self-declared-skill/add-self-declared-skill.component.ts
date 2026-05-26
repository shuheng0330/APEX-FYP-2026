import { Component, EventEmitter, HostListener, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule, FormArray, FormControl } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { NzAutocompleteModule } from 'ng-zorro-antd/auto-complete';

import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { CreateStaffSelfDeclaredSkillRequest } from '../../../../models/staff.model';
import { StaffSkillService } from '../../../../services/staff-skill.service';

interface ProficiencyOptions {
  label: string;
  value: string;
}

@Component({
  selector: 'app-add-self-declared-skill',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzAutocompleteModule],
  templateUrl: './add-self-declared-skill.component.html',
  styleUrl: './add-self-declared-skill.component.scss'
})
export class AddSelfDeclaredSkillComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.PROFILE.SKILL.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.PROFILE.SKILL.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.PROFILE.SKILL.ADD.CONFIRM.OK";

  proficiencyOptions: ProficiencyOptions[] = [
    { label: "Beginner", value: "BEGINNER" },
    { label: "Intermediate", value: "INTERMEDIATE" },
    { label: "Advanced", value: "ADVANCED" }
  ];

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    skill: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
    proficiency: [null as unknown as string, [Validators.required]]
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private staffSkillService: StaffSkillService,
    private loadingService: LoadingService,) { }

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
    this.initialized$.next(true);

  }

  uninitialize(): void {
    this.validateForm.reset({
      skill: null as unknown as string,
      description: null as unknown as string,
      proficiency: null as unknown as string,
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

      const staffId = this.authService.userId;
      const skill = this.validateForm.get('skill')?.value;
      const description = this.validateForm.get('description')?.value;
      const proficiency = this.validateForm.get('proficiency')?.value;

      if (!skill || !proficiency || !staffId) return;

      const requestBody: CreateStaffSelfDeclaredSkillRequest = {
        staffId: staffId,
        skill: skill,
        description: description ?? '',
        proficiency: proficiency
      }
      this.staffSkillService.create(requestBody).subscribe({
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
