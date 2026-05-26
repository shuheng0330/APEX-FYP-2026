import {Staff} from './staff.model';

export interface StaffLearningMaterial{
  enrollmentId?: number;
  staffId: string;
  materialId: number;
  enrolledAt?: string;
  completedAt?: string;
  progress?: number;
  isCompleted?: boolean;
  enrolledBy?: string;
  staffDto?: Staff
}
