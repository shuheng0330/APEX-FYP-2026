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
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { AuthService } from '../../../../services/auth.service';
import { LoadingService } from '../../../../services/loading.service';
import { CompTagService } from '../../../../services/compTag.service';
import { CompetencyDefinitionCollaborationService } from '../../../../services/competency-definition-collaboration.service';
import { StaffService } from '../../../../services/staff.service';

import { CompTag } from '../../../../models/comp-tag.model';
import { Staff } from '../../../../models/staff.model';
import { ProposeCompetencyRequest } from '../../../../models/competency-collaboration.model';

@Component({
  selector: 'app-add-competency-definition-collab',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzTagModule, NzToolTipModule],
  templateUrl: './add-competency-definition-collab.component.html',
  styleUrl: './add-competency-definition-collab.component.scss'
})
export class AddCompetencyDefinitionCollabComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.ADD.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.ADD.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.ADD.CONFIRM.OK";

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  listOfExistingTag: CompTag[] = []
  listOfExistingStaff: Staff[] = []
  listOfSelectedReviewer = [...this.listOfExistingStaff];
  listOfSelectedProposer = [...this.listOfExistingStaff];

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    competency: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
    compTagList: this.fb.control<string[]>([]),
    reviewerList: this.fb.control<string[]>([]),
    proposerList: this.fb.control<string[]>([])
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private compTagService: CompTagService,
    private loadingService: LoadingService, private competencyCollaborationService: CompetencyDefinitionCollaborationService,
    private staffService: StaffService) { }

  fetchAllData(): void {
    if (!this.initialized$.value) {
      forkJoin({
        compTag: this.compTagService.getAllCompTags(),
        authorizedStaff: this.staffService.getAllStaffByAuthorities(['CAN_MANAGE_COMPETENCY']),
        allStaff: this.staffService.getAllStaff(),
      }).subscribe({
        next: ({ compTag, authorizedStaff, allStaff }) => {
          this.listOfExistingTag = compTag;
          this.listOfExistingStaff = allStaff;
          this.listOfSelectedReviewer = authorizedStaff;
          this.listOfSelectedProposer = allStaff;
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

    if (!this.hasAccess(['CAN_MANAGE_COMPETENCY'])) {
      this.validateForm.get('reviewerList')?.addValidators(Validators.required);
    }
  }

  uninitialize(): void {
    this.validateForm.reset({
      competency: null as unknown as string,
      description: null as unknown as string,
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

      const competencyName = this.validateForm.get('competency')?.value;
      const competencyDescription = this.validateForm.get('description')?.value;
      const compTagList = this.validateForm.get('compTagList')?.value;
      const proposerList = this.validateForm.get('proposerList')?.value;
      const reviewerList = this.validateForm.get('reviewerList')?.value;

      if (!competencyName) return;

      if ((!this.hasAccess(['CAN_MANAGE_COMPETENCY']) && reviewerList!.length == 0) ||
        (!this.hasAccess(['CAN_MANAGE_COMPETENCY']) && proposerList!.length > 0)) return;

      const requestBody: ProposeCompetencyRequest = {
        competencyName: competencyName,
        competencyDescription: competencyDescription ?? '',
        competencyTagList: compTagList ?? [],
        reviewerList: reviewerList ?? [],
        proposerList: proposerList ?? []
      };

      this.competencyCollaborationService.createCollaboration(requestBody).subscribe({
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
