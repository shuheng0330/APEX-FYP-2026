import {Competency} from './competency.model';
import {OrgChart} from './orgChart.model';

export interface LearningMaterial {
  materialId: number;
  title: string;
  description: string;
  learningOutcomes?: string[];
  departments: OrgChart[];
  departmentIds?: number[];
  competencyIds?: number[];
  competency: Competency[];
  materialType: string[];
  learningDocuments?: LearningDocument[];
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
  progress?: number;
  isUnenrolling?: boolean;

}

export interface LearningDocument {
  documentId: number;
  title: string;
  fileUrl: string;
  signedUrl: string;
  totalPages: number;
  totalDuration: number;
  learningMaterial: LearningMaterial;
  fileType: string;
}
