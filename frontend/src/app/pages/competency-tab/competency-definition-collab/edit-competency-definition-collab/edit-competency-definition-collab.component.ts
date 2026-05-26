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

import { CompTag } from '../../../../models/comp-tag.model';
import { CompetencyCollaborationOverviewData, EditCompetencyCollaboration, ProposalParticipantId } from '../../../../models/competency-collaboration.model';

@Component({
  selector: 'app-edit-competency-definition-collab',
  imports: [CommonModule, NzButtonModule, NzDrawerModule, NzFormModule, NzInputModule, NzSelectModule,
    TranslateModule, NzIconModule, FormsModule, ReactiveFormsModule, NzModalModule,
    NzTagModule, NzToolTipModule],
  templateUrl: './edit-competency-definition-collab.component.html',
  styleUrl: './edit-competency-definition-collab.component.scss'
})
export class EditCompetencyDefinitionCollabComponent {
  @Output() formSubmitted = new EventEmitter<void>();

  private readonly confirmTitleKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.EDIT.CONFIRM.TITLE";
  private readonly confirmContentKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.EDIT.CONFIRM.CONTENT";
  private readonly confirmOkKey = "PAGE.COMPETENCY.COLLAB.COMPETENCY.EDIT.CONFIRM.OK";
  private proposalParticipantId: ProposalParticipantId | undefined;

  visible$ = new BehaviorSubject<boolean>(false);
  initialized$ = new BehaviorSubject<boolean>(false);
  drawerWidth: string = '736px';

  listOfExistingTag: CompTag[] = []

  confirmModal?: NzModalRef;

  private fb = inject(NonNullableFormBuilder);
  private destroy$ = new Subject<void>();
  validateForm = this.fb.group({
    competency: [null as unknown as string, [Validators.required]],
    description: [null as unknown as string, null],
    compTagList: this.fb.control<string[]>([]),
    lastUpdatedBy: [{ value: null as unknown as string, disabled: true }, null]
  });

  constructor(private translateService: TranslateService, private authService: AuthService,
    private modal: NzModalService, private compTagService: CompTagService,
    private loadingService: LoadingService,
    private competencyCollaborationService: CompetencyDefinitionCollaborationService) { }

  fetchAllData(data: CompetencyCollaborationOverviewData): void {
    if (!this.initialized$.value) {
      forkJoin({
        compTag: this.compTagService.getAllCompTags()
      }).subscribe({
        next: ({ compTag }) => {
          this.listOfExistingTag = compTag;
          this.proposalParticipantId = data.proposalParticipantId;

          const compTagList = data.assignedCompTags?.flatMap(compTag => compTag.tag);

          const lastUpdatedByInfo = data.lastUpdatedBy.name ?
            `${data.lastUpdatedBy.email} (${data.lastUpdatedBy.name})` :
            `${data.lastUpdatedBy.email}`;

          this.validateForm.reset({
            competency: data.competencyName,
            description: data.competencyDescription ?? '',
            compTagList: compTagList,
            lastUpdatedBy: lastUpdatedByInfo
          });
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

  initialize(data: CompetencyCollaborationOverviewData): void {
    this.adjustDrawerWidth();
    this.fetchAllData(data);
  }

  uninitialize(): void {
    this.validateForm.reset({
      competency: null as unknown as string,
      description: null as unknown as string,
    });

    this.proposalParticipantId = undefined;
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

      const competencyName = this.validateForm.get('competency')?.value;
      const competencyDescription = this.validateForm.get('description')?.value;
      const compTagList = this.validateForm.get('compTagList')?.value;

      if (!competencyName) return;

      const requestBody: EditCompetencyCollaboration = {
        proposalParticipantId: this.proposalParticipantId!,
        competencyName: competencyName,
        competencyDescription: competencyDescription ?? '',
        compTagList: compTagList ?? []
      };

      this.competencyCollaborationService.editCollaboration(requestBody).subscribe({
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
