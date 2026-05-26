import {ChangeDetectorRef, Component, Input, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {NzModalService} from 'ng-zorro-antd/modal';
import {
  TrainingInvitation,
  TrainingInvitationUI,
  TrainingRegistration,
  UpdateInvitation
} from '../../../models/training.model';
import {CommonModule} from '@angular/common';
import {NzTableModule} from 'ng-zorro-antd/table';
import {TrainingInvitationService} from '../../../services/training.invitation.service';
import {NzButtonModule} from 'ng-zorro-antd/button';
import {FormsModule} from '@angular/forms';
import {AuthService} from '../../../services/auth.service';
import {TrainingRegistrationService} from '../../../services/training.registration.service';
import {catchError, switchMap, tap} from 'rxjs';


@Component({
  selector: 'app-training-assignment-drawer',
  imports: [CommonModule, NzTableModule, NzButtonModule, FormsModule],
  templateUrl: './training-assignment-drawer.component.html',
  styleUrl: './training-assignment-drawer.component.scss'
})
export class TrainingAssignmentDrawerComponent implements OnInit{
  @Input() invitations: TrainingInvitationUI[] = [];
  @ViewChild('rejectModal', { static: true }) rejectModal!: TemplateRef<any>;

  rejectReason = '';
  userId: string | null = null;

  constructor(private modal: NzModalService,
              private trainingInvitationService: TrainingInvitationService,
              private trainingRegistrationService: TrainingRegistrationService,
              private cdr: ChangeDetectorRef,
              private auth: AuthService) {}

  ngOnInit(){
    this.userId = this.auth.userId;
    console.log(this.userId);
  }

  accept(invite: TrainingInvitation): void {
    this.updateStatus(invite, 'ACCEPTED');
  }

  reject(invite: TrainingInvitation): void {
    this.rejectReason = '';

    this.modal.create({
      nzTitle:`Reject ${invite.trainingProgram.title}`,
      nzContent: this.rejectModal,
      nzOkText: 'Submit',
      nzCancelText: 'Cancel',
      nzOnOk:() => {
        const reason = this.rejectReason?.trim() || 'No reason provided';
        this.updateStatus(invite, 'REJECTED', reason);
      }
    })
  }

  private updateStatus(invite: TrainingInvitationUI, status: string, reason?: string): void {
    if (!invite.invitationId) {
      console.error('Invitation ID missing:', invite);
      return;
    }
    console.log("training ID",invite.trainingProgram.trainingId);
    invite.isUpdating = true;
    const oldStatus = invite.status;
    const oldReason = invite.reason;

    const dto: UpdateInvitation = {
      status,
      reason: status === 'REJECTED' ? reason : undefined
    };

    const registration: TrainingRegistration = {
      staffId: this.userId!,
      training: invite.trainingProgram
    }

    let request$ = status === 'ACCEPTED'
      ? this.trainingRegistrationService.createTrainingRegistration(registration).pipe(
        tap(() => {
          this.modal.success({
            nzTitle: `Accepted ${invite.trainingProgram.title}`,
            nzContent: 'You have been successfully registered for this training.'
          });
        }),
        switchMap(() =>
          this.trainingInvitationService.updateInvitationStatus(invite.invitationId!, dto)
        )
      )
      : this.trainingInvitationService.updateInvitationStatus(invite.invitationId!, dto).pipe(
        tap(() => {
          if (status === 'REJECTED') {
            this.modal.warning({
              nzTitle: `Rejected ${invite.trainingProgram.title}`,
              nzContent: reason ? `Reason: ${reason}` : ''
            });
          }
        })
      );

    request$.pipe(
      tap((updatedInvitation) => {
        const index = this.invitations.findIndex(i => i.invitationId === updatedInvitation.invitationId);
        if (index !== -1) {
          this.invitations[index] = { ...this.invitations[index], ...updatedInvitation, isUpdating: false };
          this.invitations = [...this.invitations];
          this.cdr.detectChanges();

        }
      }),
      catchError((err) => {
        console.error('Error updating invitation or registration:', err);
        invite.status = oldStatus;
        invite.reason = oldReason;
        invite.isUpdating = false;
        this.modal.error({
          nzTitle: 'Failed to update invitation status',
          nzContent: 'Please try again later.'
        });
        return [];
      })
    ).subscribe();
  }


}
