export type TrainingSessionKind = 'TRAINING' | 'STAFF_SHARING';
export type TrainingDelivery = 'PHYSICAL' | 'HYBRID' | 'ONLINE';
export type TrainingType = 'PRODUCT' | 'SKILL_BASED' | 'OTHER';
export type TrainingSessionStatus = 'SCHEDULED' | 'CANCELLED' | 'ARCHIVED';
/** Worked out on the server from the Malaysia clock. */
export type TrainingSessionTiming = 'UPCOMING' | 'IN_PROGRESS' | 'ENDED';

/** A session on the Training Calendar (/api/trainings, FR-12). Every date and time is Malaysia time. */
export interface TrainingSession {
  id: number;
  title: string;
  description?: string;
  kind: TrainingSessionKind;
  delivery: TrainingDelivery;
  /** Physical and Hybrid training sessions only (FR-17-01). */
  trainingType?: TrainingType;
  /** yyyy-MM-dd */
  date: string;
  /** HH:mm:ss */
  startTime: string;
  endTime: string;
  durationMinutes: number;
  venue?: string;
  locationName?: string;
  latitude?: number;
  longitude?: number;
  capacity: number;
  /** Seats taken: registered, or held for someone offered a freed seat (FR-13-04). */
  booked: number;
  seatsLeft: number;
  waitlisted: number;
  mandatory: boolean;
  trainerId?: string;
  trainerName?: string;
  externalTrainer: boolean;
  /** The chosen audience; filled in only for whoever manages the session. */
  departmentIds: number[];
  roleIds: number[];
  staffIds: string[];
  /** "Everyone", or the chosen departments, job roles and people. */
  audience: string;
  audienceSize: number;
  /** Set when a mandatory session's audience is larger than its capacity (FR-12-15). */
  capacityWarning?: string;
  selfRegistration: boolean;
  registrationForm: boolean;
  registrationCutoff?: string;
  waitlistEnabled: boolean;
  status: TrainingSessionStatus;
  timing: TrainingSessionTiming;
  cancelReason?: string;
  cancelledAt?: string;
  archivedAt?: string;
  createdByName?: string;
  createdAt?: string;
  /** The signed-in user scheduled it or is HR. */
  canManage: boolean;
  /** Nobody has registered, been invited or had attendance recorded (FR-12-06). */
  canDelete: boolean;
  attendanceRecorded: boolean;
}

/** A session as scheduled or changed (FR-12-01 to FR-12-04, FR-12-12, FR-12-13, FR-17-01). */
export interface TrainingSessionRequest {
  title: string;
  description: string | null;
  kind: TrainingSessionKind;
  delivery: TrainingDelivery;
  trainingType: TrainingType | null;
  /** yyyy-MM-dd; null until chosen */
  date: string | null;
  /** HH:mm */
  startTime: string;
  endTime: string;
  venue: string | null;
  locationName: string | null;
  latitude: number | null;
  longitude: number | null;
  capacity: number | null;
  mandatory: boolean;
  /** A staff member with Trainer access, or HR... */
  trainerId: string | null;
  /** ...or an external trainer's name, for a training session. */
  trainerName: string | null;
  departmentIds: number[];
  roleIds: number[];
  staffIds: string[];
  selfRegistration: boolean;
  registrationForm: boolean;
  registrationCutoff: string | null;
  waitlistEnabled: boolean;
}

/** How many staff an audience covers, against the seats (FR-12-15). */
export interface TrainingAudienceCheck {
  audienceSize: number;
  capacity: number;
  warning?: string;
}

/** Someone who can run or present a session: Trainer access or HR (FR-12-05, FR-15-05). */
export interface TrainingTrainer {
  id: string;
  name: string;
  roleName?: string;
  departmentName?: string;
}
