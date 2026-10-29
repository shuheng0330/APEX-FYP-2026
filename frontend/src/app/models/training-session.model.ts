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
  /** They run it or are HR and it has started, so they can open its attendance (FR-14-01). */
  canRecordAttendance: boolean;
  /** When its attendance was submitted; until then it is awaiting attendance (FR-14-09, FR-14-10). */
  attendanceSubmittedAt?: string;
  /** Their own attendance, once it is submitted (FR-14-04). */
  myAttendance?: TrainingAttendanceStatus;
  myQuizScore?: number;
  /**
   * FR-15-06, after a Product Training they attended: REQUEST (they may ask for Trainer access to present a staff
   * sharing session), REQUESTED, or CAN_PRESENT (they have Trainer access or are HR).
   */
  sharingInvite?: 'REQUEST' | 'REQUESTED' | 'CAN_PRESENT';
  trainerAccessRequestedAt?: string;
}

/**
 * The Staff Sharing page (FR-15-01 to FR-15-03): a year's figures and presenters, company-wide, and the sessions the
 * signed-in user may see.
 */
export interface TrainingSharingBoard {
  year: number;
  years: number[];
  sessionsHeld: number;
  presenters: number;
  /** Every attendance, repeat visits included. */
  totalAttendances: number;
  /** Different staff reached, still with the company: never above the headcount. */
  staffReached: number;
  headcount: number;
  reachPercent?: number;
  averageAttendance?: number;
  averageQuizScore?: number;
  staffWhoShared: SharingPresenter[];
  sessions: SharingSessionHeld[];
  upcoming: TrainingSession[];
}

export interface SharingPresenter {
  staffId: string;
  name: string;
  roleName?: string;
  departmentName?: string;
  sessions: number;
  attendances: number;
  staffReached: number;
  averageQuizScore?: number;
  /** yyyy-MM-dd */
  lastShared?: string;
  leftCompany: boolean;
}

export interface SharingSessionHeld {
  id: number;
  title: string;
  date: string;
  startTime: string;
  endTime: string;
  presenterName?: string;
  attended: number;
  onList: number;
}

export type TrainingAttendanceStatus = 'ATTENDED' | 'ABSENT';

/** A session's attendance, as its trainer and HR record it (FR-14). */
export interface TrainingAttendanceSheet {
  sessionId: number;
  /** May record now without a reason: from the start until 7 days after, before it is submitted. */
  canRecord: boolean;
  /** Only HR can change it now, giving a reason that is logged. */
  hrOnly: boolean;
  /** The signed-in user is HR and may make that change. */
  canChangeWithReason: boolean;
  opensAt: string;
  trainerUntil: string;
  submitted: boolean;
  submittedAt?: string;
  submittedByName?: string;
  /** Everyone is marked (FR-14-09). */
  canSubmit: boolean;
  attended: number;
  absent: number;
  notRecorded: number;
  rows: TrainingAttendanceRow[];
  evidence: TrainingEvidence[];
}

export interface TrainingAttendanceRow {
  staffId: string;
  name: string;
  roleName?: string;
  departmentName?: string;
  /** From the HRDC form, in full, for whoever records the attendance: to check each person as they are marked. */
  idNumber?: string;
  status: TrainingAttendanceStatus | 'NOT_RECORDED';
  quizScore?: number;
  walkIn: boolean;
  method?: 'MANUAL' | 'BATCH';
  recordedByName?: string;
  recordedAt?: string;
}

/** A photo or PDF attached as evidence of the session (FR-14-08). */
export interface TrainingEvidence {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName?: string;
  uploadedAt: string;
}

/** Marks for some of the people on a session; a null status clears the mark. */
export interface TrainingAttendanceEntry {
  staffId: string;
  status: TrainingAttendanceStatus | null;
  quizScore: number | null;
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
  /** Also save this set-up as a reusable template, under this name (FR-12-08). */
  templateName?: string | null;
  /** The template the form was filled from, counted once the session is scheduled (FR-12-09). */
  templateId?: number | null;
}

/** A saved session set-up (FR-12-08 to FR-12-10): a trainer sees their own; HR sees everyone's. */
export interface TrainingTemplate {
  id: number;
  name: string;
  createdByName?: string;
  createdAt: string;
  timesUsed: number;
  lastUsedAt?: string;
  mine: boolean;
  canDelete: boolean;
  trainerName?: string;
  /** The form as saved; its date and cut-off are left for the trainer to choose. */
  setup: TrainingSessionRequest;
  /** What was left out because it no longer exists or is no longer allowed. */
  leftOut: string[];
}

/** What an uploaded HRDC attendance file did (FR-14-07). */
export interface TrainingBatchResult {
  rows: number;
  matched: number;
  attended: number;
  absent: number;
  /** Matched, with Attended left empty, so not changed. */
  leftBlank: number;
  changed: number;
  /** "Row 5, Ali Bakar: not on this session" */
  unmatched: string[];
  sheet: TrainingAttendanceSheet;
}

/** A session's report (FR-12-07). */
export interface TrainingSessionReport {
  sessionId: number;
  title: string;
  kind: TrainingSessionKind;
  delivery: TrainingDelivery;
  trainingType?: TrainingType;
  date: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  venue?: string;
  trainerName?: string;
  externalTrainer: boolean;
  mandatory: boolean;
  status: TrainingSessionStatus;
  timing: TrainingSessionTiming;
  cancelReason?: string;
  capacity: number;
  registered: number;
  waitlisted: number;
  walkIns: number;
  attended: number;
  absent: number;
  notRecorded: number;
  /** Attended, as a share of everyone on the list; absent when nobody is on it. */
  attendanceRate?: number;
  trainingHours: number;
  averageQuizScore?: number;
  submitted: boolean;
  submittedAt?: string;
  submittedByName?: string;
  people: TrainingReportPerson[];
  evidence: TrainingEvidence[];
  generatedAt: string;
  generatedByName?: string;
}

export interface TrainingReportPerson {
  name: string;
  email?: string;
  roleName?: string;
  departmentName?: string;
  /** From the HRDC form, for whoever records the attendance. */
  branch?: string;
  idNumber?: string;
  walkIn: boolean;
  status: TrainingAttendanceStatus | 'NOT_RECORDED';
  quizScore?: number;
  method?: 'MANUAL' | 'BATCH';
  recordedByName?: string;
  recordedAt?: string;
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
  /** HRDC details, for the session's trainer and HR only, with the IC or passport number in full. */
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
