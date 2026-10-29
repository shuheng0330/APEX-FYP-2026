/**
 * A staff member's training record (FR-18): every course and every session with submitted attendance, the totals,
 * and the SOP certificates.
 */
export interface TrainingRecord {
  staffId: string;
  name: string;
  email?: string;
  roleName?: string;
  departmentName?: string;
  leftCompany: boolean;
  /** FR-18-03 */
  completedItems: number;
  courseHours: number;
  sessionHours: number;
  totalHours: number;
  /** yyyy-MM-dd */
  lastCompleted?: string;
  mandatoryOutstanding: number;
  items: TrainingRecordItem[];
  certificates: TrainingCertificate[];
}

export type TrainingRecordStatus =
  'NOT_STARTED' | 'IN_PROGRESS' | 'OVERDUE' | 'COMPLETED' | 'WITHDRAWN' | 'ATTENDED' | 'ABSENT';

export interface TrainingRecordItem {
  type: 'COURSE' | 'SESSION';
  id: number;
  title: string;
  versionNo?: number;
  status: TrainingRecordStatus;
  mandatory: boolean;
  /** E-learning, Physical, Hybrid or Online. */
  delivery: string;
  /** Sessions: Training or Staff Sharing, and the training type. */
  kind?: string;
  trainingType?: string;
  dueDate?: string;
  completedOn?: string;
  /** A session's day. */
  heldOn?: string;
  score?: number;
  hours?: number;
  modules: { title: string; score?: number; passed: boolean; attempts: number }[];
}

/** FR-18-05 */
export interface TrainingCertificate {
  certificateNo: string;
  course: string;
  versionNo: number;
  issuedOn: string;
  finalScore?: number;
}
