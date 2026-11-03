import { SopAssignmentMode } from './sop-assignment.model';
import { LearningModuleState } from './sop-learning.model';

/**
 * COMPLETED means certified: every module's quiz passed. RETRAINING: a failed quiz not passed yet. WITHDRAWN: the
 * course was archived before they finished (FR-06-15).
 */
export type ScoreboardStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'RETRAINING' | 'COMPLETED' | 'WITHDRAWN';

/** One staff member's enrolment in one course (FR-10-01). */
export interface ScoreboardRecord {
  enrollmentId: number;
  staffId: string;
  staffName: string;
  jobRole?: string | null;
  department?: string | null;
  sopDocumentId: number;
  courseTitle: string;
  /** The course was archived; its records stay here (FR-06-14). */
  courseArchived: boolean;
  versionNo: number;
  /** A newer version replaced this one; its records stay here (FR-07-17). */
  courseSuperseded: boolean;
  moduleCount: number;
  modulesPassed: number;
  status: ScoreboardStatus;
  overdue: boolean;
  mandatory: boolean;
  mode?: SopAssignmentMode | null;
  selfEnrolled: boolean;
  /** Left the company: listed, but not counted in the figures (FR-10-12). */
  leftCompany: boolean;
  /** The viewer may open the person's whole training record: HR, the Super Admin, or their manager (FR-18-01). */
  canOpenRecord: boolean;
  passMark: number;
  /** Final score once certified, otherwise the average of the modules passed so far. */
  score?: number | null;
  finalScore?: number | null;
  attempts: number;
  /** Failed quiz attempts, each sending a module back for retraining (FR-10-05). */
  retrainingAttempts: number;
  lastAttemptAt?: string | null;
  dueDate?: string | null;
  assignedAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface Scoreboard {
  courses: { id: number; title: string; archived: boolean; versionNo: number; superseded: boolean }[];
  departments: string[];
  records: ScoreboardRecord[];
  /** ALL courses, the viewer's OWN courses, their TEAM's training, or OWN_AND_TEAM (FR-10-10). */
  scope: 'ALL' | 'OWN' | 'TEAM' | 'OWN_AND_TEAM';
  generatedAt: string;
}

export interface EnrollmentAttempt {
  score: number;
  correct: number;
  total: number;
  passed: boolean;
  attemptedAt: string;
}

export interface EnrollmentModuleProgress {
  moduleId: number;
  moduleOrder: number;
  title: string;
  state: LearningModuleState;
  passedScore?: number | null;
  attempts: EnrollmentAttempt[];
}

/** One enrolment, module by module (FR-10-02). */
export interface EnrollmentProgress {
  record: ScoreboardRecord;
  modules: EnrollmentModuleProgress[];
}
