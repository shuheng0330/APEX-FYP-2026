import { ParamMap, Params, Router } from '@angular/router';
import { SopDocument, SopModule } from '../../models/sop.model';

/** The SOP files accepted, for the file picker; the same list as the server's (FR-06-01, FR-06-08). */
export const SOP_FILE_ACCEPT = '.doc,.docx,.pdf,.ppt,.pptx,.xls,.xlsx,.txt';
/** How the accepted SOP files are described, the same way on every upload screen (FR-06-01). */
export const SOP_FILE_TYPES = 'Word, PDF, PowerPoint, Excel or plain-text file (.doc, .docx, .pdf, .ppt, .pptx, .xls, .xlsx or .txt)';
/** The largest SOP file accepted, matching the server (FR-06-12). */
export const MAX_UPLOAD_MB = 20;

/** The limits on a course's estimated duration, matching the server (FR-07-15). */
export const MIN_DURATION_MINUTES = 30;
export const MAX_DURATION_MINUTES = 24 * 60;

/** The course lists on Upload and the review pages: current courses, or archived ones (FR-06-14). */
export type CourseView = 'CURRENT' | 'ARCHIVED';

/**
 * The review pages keep the Archived list in the address as ?view=archived. Leaving a course's address
 * (/sop/review/5) builds the page afresh, and this is how the fresh page knows to stay on Archived.
 */
export function courseViewQuery(view: CourseView): Params {
  return view === 'ARCHIVED' ? { view: 'archived' } : {};
}

/** The list a review page opens on: Archived when the address says ?view=archived, otherwise Current. */
export function courseViewFrom(query: ParamMap | undefined): CourseView {
  return query?.get('view') === 'archived' ? 'ARCHIVED' : 'CURRENT';
}

/** Navigation state that stops a review page opening the newest course: one just failed to open. */
export const NO_AUTO_OPEN = { autoOpen: false };

/**
 * Whether a review page opened with no course in its address should open the newest course in its list,
 * so it never starts on an empty page. Not straight after a course failed to open, so it can't loop.
 * Call it while the router builds the page (a field initializer): by ngOnInit the navigation has finished.
 */
export function autoOpenAllowed(router: Router): boolean {
  return router.getCurrentNavigation()?.extras.state?.['autoOpen'] !== false;
}

/** Asked before restoring an archived course (FR-06-16, decision D3). */
export const RESTORE_QUESTION =
  'Restore this course? It becomes Live again. Staff who were withdrawn are not re-enrolled; assign it to them again if needed.';

/** "Cash Handling · v2 draft (5 modules)", for the course pickers (FR-07-16). */
export function courseOptionLabel(doc: SopDocument): string {
  return `${doc.title} · v${doc.versionNo}${doc.courseStatus === 'DRAFT' ? ' draft' : ''} (${doc.moduleCount} modules)`;
}

/** "v2 draft": shown in its own column in the course pickers' list, so a long title never hides it (VR-11). */
export function courseOptionVersion(doc: SopDocument): string {
  return `v${doc.versionNo}${doc.courseStatus === 'DRAFT' ? ' draft' : ''}`;
}

/** "5 modules", under the version in the course pickers' list. */
export function courseOptionModules(doc: SopDocument): string {
  return `${doc.moduleCount} ${doc.moduleCount === 1 ? 'module' : 'modules'}`;
}

/**
 * Each option in a course picker's list is this tall: up to two lines of the course name, with the version beside it.
 * The list assumes every option is the same height, so styles.scss (.course-picker-dropdown) uses the same number.
 */
export const COURSE_OPTION_HEIGHT_PX = 52;

/** Whether a quiz change or a rejection starts a new draft version: staff are taking this live course (FR-07-16). */
export function startsDraft(doc: SopDocument | undefined): boolean {
  return !!doc && doc.courseStatus === 'LIVE' && doc.editable && doc.enrolledCount > 0 && !doc.draftId;
}

/** Asked before a change that starts a new draft version. */
export function draftQuestion(doc: SopDocument): { nzTitle: string; nzContent: string } {
  const next = doc.versionNo + 1;
  return {
    nzTitle: `Start draft v${next}?`,
    nzContent: `Staff are taking this course, so this change goes into a new draft, v${next}. They keep using v${doc.versionNo} `
      + 'until you publish the draft. Text edits, the pass mark and the duration still change this version directly.'
  };
}

/** Every module's material and quiz is approved, so a draft can be published (P2-19). */
/** The fewest questions a quiz may have once approved, and the fewest an attempt presents (FR-08-14, FR-08-15). */
export const MIN_QUIZ_QUESTIONS = 5;

export function courseReady(modules: SopModule[]): boolean {
  return modules.length > 0 && modules.every(m => m.reviewStatus === 'APPROVED' && m.quiz.length >= MIN_QUIZ_QUESTIONS
    && m.quiz.every(q => q.reviewStatus === 'APPROVED'));
}

/** Minutes as "45 min", "1 h" or "1 h 30 min"; "-" when unknown. */
export function formatDuration(minutes: number | null | undefined): string {
  if (minutes == null || !Number.isFinite(minutes) || minutes <= 0) return '-';
  const hours = Math.floor(minutes / 60);
  const rest = Math.round(minutes % 60);
  if (!hours) return `${rest} min`;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}
