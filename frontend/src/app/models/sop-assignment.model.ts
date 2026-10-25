export type SopVisibility = 'PRIVATE' | 'OPEN';
export type SopAssignmentMode = 'PORTAL' | 'IMMEDIATE';

/** An SOP course as listed on Assign Training. */
export interface AssignableCourse {
  id: number;
  title: string;
  version?: string;
  departmentTag?: string;
  ownerName?: string;
  /** Uploaded by the signed-in user. */
  mine: boolean;
  moduleCount: number;
  approvedModules: number;
  approvedQuizzes: number;
  /** Every module and quiz approved, so it can be assigned. */
  ready: boolean;
  visibility: SopVisibility;
  enrolledCount: number;
  /** The course's own pass mark; an assignment may raise it but not lower it (FR-09-03). */
  passMark: number;
  /** The estimated duration that counts, in minutes (FR-07-15). */
  durationMinutes: number;
  /** False while the course still counts the default duration. */
  durationSet: boolean;
  recommendedDepartmentIds: number[];
  recommendedRoleIds: number[];
}

export interface AudienceGroup {
  id: number;
  name: string;
  /** For a job role, the department it belongs to. */
  departmentName?: string;
  staffCount: number;
}

export interface AudiencePerson {
  id: string;
  name: string;
  email: string;
  roleId?: number;
  roleName?: string;
  departmentId?: number;
  departmentName?: string;
}

export interface Audience {
  departments: AudienceGroup[];
  roles: AudienceGroup[];
  staff: AudiencePerson[];
}

export interface AssignmentRequest {
  sopDocumentId: number;
  everyone: boolean;
  departmentIds: number[];
  roleIds: number[];
  staffIds: string[];
  mode: SopAssignmentMode;
  mandatory: boolean;
  /** yyyy-MM-dd */
  effectiveDate: string;
  /** yyyy-MM-dd */
  deadline: string;
  daysToComplete: number;
  passMark: number;
}

export type AssignmentOutcome = 'NEW' | 'UPDATE' | 'COMPLETED';

export interface AssignmentPreview {
  newCount: number;
  updateCount: number;
  completedCount: number;
  staff: { staffId: string; name: string; roleName?: string; departmentName?: string; outcome: AssignmentOutcome }[];
}

export interface Assignment {
  id: number;
  sopDocumentId: number;
  courseTitle: string;
  audience: string;
  staffCount: number;
  mode: SopAssignmentMode;
  mandatory: boolean;
  effectiveDate: string;
  deadline: string;
  daysToComplete: number;
  passMark: number;
  assignedByName: string;
  createdAt: string;
}

export interface AssignmentResult {
  assignment: Assignment;
  assigned: number;
  updated: number;
  skippedCompleted: number;
}
