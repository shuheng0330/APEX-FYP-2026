import { OrgChart } from './orgChart.model';
import { Competency } from './competency.model';
import { Staff } from './staff.model';

export interface TrainingProgram {
  trainingId: number;
  title: string;
  description: string;
  startDate: string;
  endDate: string;
  startTime: string;
  endTime: string;
  departments: OrgChart[];
  competencies: Competency[];
  departmentIds?: number[];
  competencyIds?: number[];
  venue: string;
  capacity: number;
  isPublic: boolean;
  importantNotes?: string[];
  roleIds?: number[];
  isMandatory: boolean;
  locationName: string;
  latitude: number;
  longitude: number;
  checkinRadius: number;
  registeredCount?: number;
}

export interface TrainingAttendance {
  attendanceId?: number;
  trainingId: number;
  staffId: string;
  checkInTime?: string;
  checkInLatitude: number;
  checkInLongitude: number;
  withinGeofence?: boolean;
}

export interface CheckInResponse {
  success: boolean;
  message: string;
  distance: number;
}
export interface TrainingRegistration {
  registrationId?: number;
  registeredAt?: string;
  staffId: string;
  training?: TrainingProgram;
}

export interface TrainingInvitation {
  invitationId: number;
  status?: string;
  reason?: string;
  staff: Staff;
  invitedAt?: string;
  respondAt?: string;
  trainingProgram: TrainingProgram;
  invitedBy?: Staff;
}

export interface TrainingInvitationUI extends TrainingInvitation {
  isUpdating?: boolean;
}

export interface UpdateInvitation {
  status?: string;
  reason?: string;
}

export interface BulkInvitation {
  trainingId: number;
  staffIds: string[];
}

export interface CascaderOption {
  label: string;
  value: number;
  isLeaf?: boolean;
  children?: CascaderChild[];
}

export interface CascaderChild {
  label: string;
  value: number;
  isLeaf?: boolean;
}

export interface StaffConflictDTO {
  staffId: string;
  trainingTitle: string;
  startTime: string; // HH:mm:ss
  endTime: string;   // HH:mm:ss
  startDate: string;
  endDate: string
  venue: string;
}
