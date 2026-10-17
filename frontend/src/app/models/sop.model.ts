export type SopGenerationStatus = 'PENDING' | 'PARSING' | 'GENERATING' | 'COMPLETED' | 'FAILED';
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REGENERATED';
export type QuizQuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_IN_THE_BLANK';
export type QuizDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuizReviewAction = 'APPROVED' | 'REJECTED' | 'REGENERATED' | 'EDITED';

export type ContentBlockType = 'paragraph' | 'steps' | 'table' | 'warnings' | 'image' | 'video';

export interface ContentSection {
  type: ContentBlockType;
  heading: string;
  /** paragraph */
  body?: string;
  /** steps, warnings */
  items?: string[];
  /** table */
  headers?: string[];
  rows?: string[][];
  /** image, video — URL returned by POST /api/sop/media, or an external link */
  url?: string;
  caption?: string;
  /** image only: alt text for accessibility */
  alt?: string;
}

export interface UploadedMedia {
  url: string;
  filename: string;
  contentType: string;
  size: number;
  kind: 'image' | 'video';
}

export interface KeyTerm {
  term: string;
  definition: string;
}

export interface RichContent {
  summary: string;
  learningObjectives: string[];
  tools: string[];
  sections: ContentSection[];
  keyTerms: KeyTerm[];
  /** 'offline' when built from the SOP text because the AI was unavailable. */
  source?: 'offline';
}

export interface SopDocument {
  id: number;
  title: string;
  version?: string;
  originalFilename?: string;
  departmentTag?: string;
  generationStatus: SopGenerationStatus;
  statusMessage?: string;
  moduleCount: number;
  createdAt: string;
  /** Trainer who uploaded the SOP, shown on each material (FR-07-07); the course's owner. */
  preparedByName?: string;
  /** Whether you may change this course: only its owner can (FR-07-08, FR-07-19). */
  editable: boolean;
  /** The course's own pass mark, as a percentage (FR-07-15). */
  passMark: number;
  /** The estimated duration that counts, in minutes: the trainer's, or the 1-hour default until they set one. */
  durationMinutes: number;
  /** Whether the trainer has set the estimated duration; until then they are reminded. */
  durationSet: boolean;
}

/** A course's own settings, set by its owner (FR-07-15). */
export interface SopCourseSettings {
  passMark: number;
  durationMinutes: number;
}

export interface SopQuizQuestion {
  id: number;
  sopModuleId: number;
  questionType: QuizQuestionType;
  questionText: string;
  options: string[];
  correctAnswer: string;
  explanation?: string;
  /** Null for questions generated before difficulty was recorded. */
  difficulty?: QuizDifficulty | null;
  reviewStatus: ReviewStatus;
  rejectionReason?: string;
}

export interface QuizReviewEvent {
  id: number;
  action: QuizReviewAction;
  /** The rejection reason, or what was edited or regenerated. */
  detail?: string | null;
  /** Null when the AI regenerated the quiz. */
  actorName?: string | null;
  createdAt: string;
}

export interface SopModule {
  id: number;
  sopDocumentId: number;
  moduleOrder: number;
  title: string;
  content: string;
  reviewStatus: ReviewStatus;
  rejectionReason?: string;
  quiz: SopQuizQuestion[];
}

export interface SopDetail {
  document: SopDocument;
  modules: SopModule[];
}
