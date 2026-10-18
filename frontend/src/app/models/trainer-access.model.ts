/** A staff member on the Super Admin's Trainer Access page (FR-20-01). */
export interface TrainerAccessStaff {
  staffId: string;
  name: string;
  email: string;
  department?: string | null;
  jobRole?: string | null;
  /** Has Trainer access through their job role's permissions. */
  viaRole: boolean;
  /** Has an individual grant in force today. */
  viaGrant: boolean;
  /** The grant's last day (yyyy-MM-dd), or null for no end date; only set while the grant is in force. */
  grantEndDate?: string | null;
  grantedByName?: string | null;
  grantedAt?: string | null;
  /** Courses this person owns; revoking their access hands these to someone else (FR-20-07). */
  ownedCourses: number;
}

/** Granting, changing or revoking Trainer access; every change needs a reason. */
export interface TrainerAccessRequest {
  /** The last day the access works (yyyy-MM-dd); null for no end date. */
  endDate?: string | null;
  reason: string;
  /** Revoking only: who takes over the person's courses when they would lose Trainer access (FR-20-07). */
  transferToId?: string | null;
}

/** One entry of the training audit log (NFR-007). */
export interface TrainingAuditEntry {
  id: number;
  action: string;
  /** Who acted; null when a scheduled job did. */
  actorName?: string | null;
  reason?: string | null;
  detail?: string | null;
  createdAt: string;
}

export interface TrainerOwnedCourse {
  id: number;
  title: string;
  version?: string | null;
}

/** Whether a course's owner can still look after it (FR-20-06). Anything but OK may be transferred. */
export type CourseOwnerStatus = 'OK' | 'NO_TRAINER_ACCESS' | 'OWNER_LEFT' | 'NO_OWNER';

/** A course and its owner, on the Course owners tab (FR-20-06). */
export interface CourseOwner {
  courseId: number;
  title: string;
  version?: string | null;
  /** Null when the course has no owner. */
  ownerId?: string | null;
  ownerName?: string | null;
  status: CourseOwnerStatus;
  /** True when the owner account is abandoned, so the course may be transferred. */
  needsNewOwner: boolean;
  enrolledCount: number;
  createdAt?: string | null;
}

/** Giving an abandoned course to someone with Trainer access; a reason is required. */
export interface CourseTransferRequest {
  newOwnerId: string;
  reason: string;
}
