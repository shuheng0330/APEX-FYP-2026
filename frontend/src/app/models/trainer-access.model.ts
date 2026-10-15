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
  /** Courses this person owns, which must be transferred before they lose Trainer access (FR-20-07). */
  ownedCourses: number;
}

/** Granting, changing or revoking Trainer access; every change needs a reason. */
export interface TrainerAccessRequest {
  /** The last day the access works (yyyy-MM-dd); null for no end date. */
  endDate?: string | null;
  reason: string;
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
