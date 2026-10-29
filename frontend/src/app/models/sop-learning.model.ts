import { QuizDifficulty, QuizQuestionType } from './sop.model';
import { SopAssignmentMode } from './sop-assignment.model';
import { CourseCategoryChip } from './course-category.model';

/** WITHDRAWN: the course was archived before they finished it; their progress is kept (FR-06-15). */
export type SopEnrollmentStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'WITHDRAWN';

/**
 * A module's state for the learner: LOCKED until the previous module is passed, AVAILABLE to
 * study, RETRAINING after a failed quiz (practice mode), PASSED, or UNAVAILABLE while the
 * trainer is updating it.
 */
export type LearningModuleState = 'LOCKED' | 'AVAILABLE' | 'RETRAINING' | 'PASSED' | 'UNAVAILABLE';

/** A course on the learner's portal: one they are enrolled in, or an Open course they can join. */
export interface LearningCourse {
  sopDocumentId: number;
  title: string;
  version?: string | null;
  /** The primary category (FR-19-05), with its colour and icon... */
  category: string;
  categoryColor?: string;
  categoryIcon?: string;
  /** ...and every category the course carries, for the filters (FR-19-06). */
  categories?: CourseCategoryChip[];
  moduleCount: number;
  /** Null for an Open course the learner has not joined. */
  enrollmentId?: number | null;
  status?: SopEnrollmentStatus | null;
  mode?: SopAssignmentMode | null;
  mandatory: boolean;
  selfEnrolled: boolean;
  effectiveDate?: string | null;
  /** When the learner must finish by; null when there is no deadline. */
  dueDate?: string | null;
  deadline?: string | null;
  daysToComplete?: number | null;
  passMark: number;
  modulesPassed: number;
  retraining: boolean;
  overdue: boolean;
  /** Whether it can be worked on: false until the effective date, and once withdrawn or archived. */
  available: boolean;
  /** The course was archived (FR-06-14): results and certificates are kept, but it can't be taken. */
  courseArchived?: boolean;
  /** The course version this enrolment is for (FR-18-05). */
  versionNo: number;
  /** A newer version replaced this one (FR-07-17): results are kept, but it can't be taken. */
  courseSuperseded?: boolean;
  finalScore?: number | null;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface LearningHome {
  courses: LearningCourse[];
  openCourses: LearningCourse[];
}

export interface LearningModule {
  id: number;
  moduleOrder: number;
  title: string;
  state: LearningModuleState;
  questionCount: number;
  /** Questions each attempt presents (FR-08-15). */
  quizSize: number;
  /** Correct answers needed to pass, for example 4 of 5 (FR-11-12). */
  passCorrect: number;
  /** Tries left today, of 3 (FR-11-04). */
  triesLeftToday: number;
  /** The last attempt failed and the material hasn't been opened since. */
  reviewMaterialFirst: boolean;
  attempts: number;
  failedAttempts: number;
  bestScore?: number | null;
  passedScore?: number | null;
  passedAt?: string | null;
}

export interface SopCertificate {
  certificateNo: string;
  learnerName: string;
  courseTitle: string;
  version?: string | null;
  /** The course version the certificate is for (FR-18-05). */
  versionNo: number;
  finalScore?: number | null;
  passMark: number;
  completedAt: string;
}

export interface LearningCourseDetail {
  course: LearningCourse;
  modules: LearningModule[];
  learnerName: string;
  /** Present once the course is completed. */
  certificate?: SopCertificate | null;
  feedbackRating?: number | null;
  feedbackComment?: string | null;
}

export interface LearningMaterial {
  moduleId: number;
  moduleOrder: number;
  title: string;
  content: string;
}

/** A quiz question as the learner sees it: never includes the answer. */
export interface LearningQuestion {
  id: number;
  type: QuizQuestionType;
  question: string;
  options: string[];
  difficulty?: QuizDifficulty | null;
}

export interface QuestionResult {
  questionId: number;
  correct: boolean;
  givenAnswer?: string | null;
  /** Only sent once the module is passed. */
  correctAnswer?: string | null;
  explanation?: string | null;
}

export interface QuizResult {
  score: number;
  correct: number;
  total: number;
  passed: boolean;
  passMark: number;
  /** Correct answers this attempt needed (FR-11-12). */
  passCorrect: number;
  /** Tries left today (FR-11-04). */
  triesLeftToday: number;
  attempts: number;
  moduleState: LearningModuleState;
  courseCompleted: boolean;
  finalScore?: number | null;
  nextModuleId?: number | null;
  questions: QuestionResult[];
}

export interface FeedbackRequest {
  rating: number;
  comment?: string;
}
