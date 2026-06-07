export type SopGenerationStatus = 'PENDING' | 'PARSING' | 'GENERATING' | 'COMPLETED' | 'FAILED';
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REGENERATED';
export type QuizQuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_IN_THE_BLANK';

export interface ContentSection {
  type: 'paragraph' | 'steps' | 'table' | 'warnings';
  heading: string;
  body?: string;
  items?: string[];
  headers?: string[];
  rows?: string[][];
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
}

export interface SopQuizQuestion {
  id: number;
  sopModuleId: number;
  questionType: QuizQuestionType;
  questionText: string;
  options: string[];
  correctAnswer: string;
  explanation?: string;
  reviewStatus: ReviewStatus;
  rejectionReason?: string;
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
