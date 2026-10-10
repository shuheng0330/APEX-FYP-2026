import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ModalOptions, NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

@Component({
  standalone: true, imports: [CommonModule, TranslateModule],
  template: `<div class="late-warning" role="alert"><strong>{{ 'REVIEW_FEEDBACK.LATE_TITLE' | translate }}</strong>
    <p>{{ 'REVIEW_FEEDBACK.LATE_WARNING' | translate }}</p>
    @if (data.deadline) { <p>{{ 'MY_ASSESSMENTS.DEADLINE' | translate }}: {{ data.deadline | date:'dd MMM yyyy' }}</p> }
    </div>`,
  styles: [`.late-warning { padding: 14px; background: #fff7e6; border-left: 4px solid #d99a17; border-radius: 6px; color: #805900; } p { margin: 8px 0 0; }`]
})
export class LateAssessmentWarningComponent {
  readonly data = inject<{ deadline: string | null }>(NZ_MODAL_DATA);
}

export function assessmentSubmissionConfirmation(translate: TranslateService, normalKey: string,
    overdue: boolean, deadline: string | null): Pick<ModalOptions, 'nzContent' | 'nzData' | 'nzOkText'> {
  return overdue ? { nzContent: LateAssessmentWarningComponent, nzData: { deadline }, nzOkText: translate.instant('REVIEW_FEEDBACK.SUBMIT_LATE') }
    : { nzContent: translate.instant(normalKey) };
}
