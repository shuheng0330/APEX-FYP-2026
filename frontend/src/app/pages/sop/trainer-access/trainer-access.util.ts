import { HttpErrorResponse } from '@angular/common/http';
import { NzMessageService } from 'ng-zorro-antd/message';

/** The longest reason a Trainer access change or a course transfer may have (FR-20-01). */
export const MAX_REASON = 500;

/** How each training audit action shows in a history timeline (NFR-007). */
const AUDIT_ACTIONS: Record<string, { label: string; color: string }> = {
  TRAINER_ACCESS_GRANTED: { label: 'Granted', color: 'green' },
  TRAINER_ACCESS_END_DATE_CHANGED: { label: 'End date changed', color: 'blue' },
  TRAINER_ACCESS_REVOKED: { label: 'Revoked', color: 'red' },
  TRAINER_ACCESS_LAPSED: { label: 'Ended', color: 'gray' },
  COURSE_TRANSFERRED: { label: 'Transferred', color: 'blue' },
  COURSE_ARCHIVED: { label: 'Archived', color: 'gray' },
  COURSE_RESTORED: { label: 'Restored', color: 'green' },
  COURSE_DRAFT_CREATED: { label: 'Draft started', color: 'gold' },
  COURSE_VERSION_PUBLISHED: { label: 'Version published', color: 'green' },
  COURSE_DRAFT_DISCARDED: { label: 'Draft discarded', color: 'gray' },
};

export function auditAction(action: string): { label: string; color: string } {
  return AUDIT_ACTIONS[action] ?? { label: action, color: 'gray' };
}

/** Shows why a change was stopped: in the dialog through `show`, and in a pop-up. */
export function showRefusal(error: string, message: NzMessageService, show: (error: string) => void): void {
  show(error);
  message.warning(error);
}

/** Every change needs a reason (FR-20-01). Returns true, after saying why, when the reason is missing or too long. */
export function reasonMissing(reason: string, message: NzMessageService, show: (error: string) => void): boolean {
  const error = !reason.trim() ? 'A reason is required. Say why you are making this change.'
    : reason.length > MAX_REASON ? `Keep the reason under ${MAX_REASON} characters.` : '';
  if (error) {
    showRefusal(error, message, show);
  }
  return !!error;
}

/** The API's message for a refused change, shown inside the dialog. */
export function errorMessage(e: HttpErrorResponse): string {
  return e.error?.message || 'Something went wrong. Please try again.';
}
