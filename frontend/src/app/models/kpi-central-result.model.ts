import { KpiItem, KpiLevel, KpiPeriodContext } from './kpi-plan.model';

export type CentralResultLevel = Extract<KpiLevel, 'COMPANY' | 'DEPARTMENT'>;
export type CentralResultStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED';
export type ReviewFrequency = 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';

export interface CentralResultOption {
  planVersionId: number;
  versionNumber: number;
  level: CentralResultLevel;
  departmentId: number | null;
  departmentName: string | null;
  currentVersion: boolean;
}

export interface CentralResultCheckpoint {
  id: number;
  reviewFrequency: ReviewFrequency;
  sequenceNumber: number;
  startDate: string;
  endDate: string;
  selfAssessmentDeadline: string;
  superiorAssessmentDeadline: string;
}

export interface CentralResultEvidence {
  id: number;
  itemId: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  uploadedAt: string;
}

export interface CentralResultItem {
  id: number;
  kpi: KpiItem & { id: number };
  actualAchievement: string | null;
  point: number | null;
  comment: string | null;
  evidence: CentralResultEvidence[];
}

export interface CentralResult {
  id: number;
  reviewPeriodId: number;
  reviewPeriodName: string;
  reviewPeriodStatus: KpiPeriodContext['status'];
  checkpointId: number;
  reviewFrequency: ReviewFrequency;
  planVersionId: number;
  planVersionNumber: number;
  departmentId: number | null;
  departmentName: string | null;
  level: CentralResultLevel;
  editionNumber: number;
  status: CentralResultStatus;
  previousEditionId: number | null;
  workingCorrectionId: number | null;
  correctionReason: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  publishedAt: string | null;
  publishedBy: string | null;
  lockVersion: number;
  items: CentralResultItem[];
  publicationBlockers: string[];
  allowedActions: string[];
}

export interface CentralResultRequest {
  planVersionId: number;
  checkpointId: number;
  expectedLockVersion?: number;
  items: { kpiId: number; actualAchievement: string | null; point: number | null; comment: string | null }[];
}

export interface CentralEvidenceMutation {
  evidence: CentralResultEvidence;
  resultLockVersion: number;
}

export interface CompanyResultDelegate {
  id: number;
  reviewPeriodId: number;
  staffId: string;
  assignedBy: string;
  assignedAt: string;
  revokedBy: string | null;
  revokedAt: string | null;
}
