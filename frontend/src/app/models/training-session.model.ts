export type TrainingSessionKind = 'TRAINING' | 'STAFF_SHARING';
export type TrainingDelivery = 'PHYSICAL' | 'HYBRID' | 'ONLINE';
export type TrainingType = 'PRODUCT' | 'SKILL_BASED' | 'OTHER';
export type TrainingSessionStatus = 'SCHEDULED' | 'CANCELLED' | 'ARCHIVED';
/** Worked out on the server from the Malaysia clock. */
export type TrainingSessionTiming = 'UPCOMING' | 'IN_PROGRESS' | 'ENDED';
/** A person's place on a session (NFR-010). */
export type TrainingRegistrationStatus = 'REGISTERED' | 'WAITLISTED' | 'OFFERED' | 'CANCELLED';
export type TrainingInvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'NO_RESPONSE';

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
  /** The signed-in user may see who is on it: they scheduled or run it, or are HR or the Super Admin. */
  canSeePeople: boolean;
  /** The signed-in user's own place on the session (FR-13-04). */
  myStatus?: TrainingRegistrationStatus;
  /** Their place in the waitlist, from 1. */
  myWaitlistPosition?: number;
  /** Until when a freed seat is held for them (FR-13-07). */
  myOfferExpiresAt?: string;
  /** Their invitation to this mandatory session (FR-12-14). */
  myInvitation?: TrainingInvitationStatus;
  /** They can register now; otherwise registerBlocker says why not. */
  canRegister: boolean;
  registerBlocker?: string;
  /** Registering now puts them on the waitlist (FR-13-02). */
  wouldWaitlist: boolean;
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

/** The HRDC registration form, filled in from the staff record (FR-13-03). */
export interface TrainingRegistrationForm {
  name: string;
  email: string;
  departmentName?: string;
  roleName?: string;
  /** TBM's branches by region. */
  branches: Record<string, string[]>;
}

/** What a staff member sends to register; the HRDC details only for sessions that ask for them. */
export interface TrainingRegistrationRequest {
  idNumber?: string;
  phone?: string;
  branch?: string;
  pdpaConsent?: boolean;
}

/** One person on a session, as its trainer and HR see them. */
export interface TrainingPerson {
  staffId: string;
  name: string;
  roleName?: string;
  departmentName?: string;
  status: TrainingRegistrationStatus | 'INVITED' | 'DECLINED' | 'NO_RESPONSE';
  registrationId?: number;
  waitlistPosition?: number;
  offerExpiresAt?: string;
  registeredAt?: string;
  note?: string;
  addedByName?: string;
  /** HRDC details, for the session's trainer and HR only; the IC or passport number shows its last 4 characters. */
  idNumber?: string;
  phone?: string;
  branch?: string;
}

/** Someone who can run or present a session: Trainer access or HR (FR-12-05, FR-15-05). */
export interface TrainingTrainer {
  id: string;
  name: string;
  roleName?: string;
  departmentName?: string;
}
