import { Component, EventEmitter, HostListener, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { FormsModule, NonNullableFormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzModalModule, NzModalRef, NzModalService } from 'ng-zorro-antd/modal';

import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { CompTag } from '../../../../models/comp-tag.model';
import { CompTagService } from '../../../../services/compTag.service';
import { CompetencyService } from '../../../../services/competency.service';
import { CompetencyEdit } from '../../../../models/competency.model';

@Component({
  selector: 'app-edit-competency',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule],
  templateUrl: './edit-competency.component.html',
  styleUrl: './edit-competency.component.scss'
})
export class EditCompetencyComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.COMPETENCY.OVERVIEW.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.COMPETENCY.OVERVIEW.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.COMPETENCY.OVERVIEW.EDIT.CONFIRM.OK";

  selectedCompetencyId: number = NaN;
  selectedCompetencyName: string = '';
  selectedCompetencyDescription?: string;
  selectedCompTagList?: string[];

  listOfExistingTag: CompTag[] = [];

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    competency: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
    compTagList: this.fb.control<string[]>([])
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private compTagService: CompTagService,
    private loadingService: LoadingService, private competencyService: CompetencyService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      this.compTagService.getAllCompTags().subscribe({
        next: (res: CompTag[]) => {
          this.listOfExistingTag = res;
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

    this.validateForm.reset({
      competency: this.selectedCompetencyName,
      description: this.selectedCompetencyDescription || '',
      compTagList: this.selectedCompTagList || [],
    });

    this.initialized$.next(true);
  }

  uninitialize(): void {
    this.validateForm.reset({
      competency: null as unknown as string,
      description: null as unknown as string,
      compTagList: null as unknown as string[],
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

      const competencyName = this.validateForm.get('competency')?.value!;

      const competencyConfirm = `<li>${competencyName}</li>`

      this.confirmModal = this.modal.confirm({
        nzTitle: confirmTitle,
        nzContent: `${confirmContent}<br/><br/><b><ul>${competencyConfirm}</ul></b>`,
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

      const competencyId = this.selectedCompetencyId;
      const competencyName = this.validateForm.get('competency')?.value;
      const description = this.validateForm.get('description')?.value;
      const compTagList = this.validateForm.get('compTagList')?.value;

      if (!competencyId || !competencyName) return;

      const requestBody: CompetencyEdit = {
        competencyId,
        competencyName,
        competencyDescription: description || '',
        compTagList: compTagList || []
      };

      this.competencyService.updateCompetency(requestBody).subscribe({
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
