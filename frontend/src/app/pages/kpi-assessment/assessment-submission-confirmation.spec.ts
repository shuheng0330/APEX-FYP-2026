import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';
import { assessmentSubmissionConfirmation, LateAssessmentWarningComponent } from './assessment-submission-confirmation';

describe('Assessment late confirmation', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [TranslateModule.forRoot(), LateAssessmentWarningComponent],
    providers: [{ provide: NZ_MODAL_DATA, useValue: { deadline: '2026-10-05' } }] }));
  it('leaves normal confirmation unchanged and uses explicit late confirmation only when overdue', () => {
    const translate = TestBed.inject(TranslateService);
    expect(assessmentSubmissionConfirmation(translate, 'NORMAL_CONFIRM', false, '2026-10-05')).toEqual({ nzContent: 'NORMAL_CONFIRM' });
    const late = assessmentSubmissionConfirmation(translate, 'NORMAL_CONFIRM', true, '2026-10-05');
    expect(late.nzContent).toBe(LateAssessmentWarningComponent); expect(late.nzData).toEqual({ deadline: '2026-10-05' });
    expect(late.nzOkText).toBe('REVIEW_FEEDBACK.SUBMIT_LATE');
  });
  it('shows an amber warning with the applicable deadline', () => {
    const fixture = TestBed.createComponent(LateAssessmentWarningComponent); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('REVIEW_FEEDBACK.LATE_WARNING');
    expect(fixture.nativeElement.textContent).toContain('05 Oct 2026'); expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
  });
});
