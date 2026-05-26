import { Role } from "./role.model";
import { Competency } from "./competency.model";

export interface RoleCompetency {
  id: {
    roleId: number;
    competencyId: number;
  };
  role: Role;
  competency: Competency;
  weightage: number;
  isDeleted: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
  rating?: number;
  error?: string;
}

export interface RoleCompetencyDto {
  id: {
    roleId: number;
    competencyId: number;
  };
  roleDto: Role;
  competency: Competency;
  weightage: number;
  isDeleted: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface CompetencyAssignmentOverviewData {
  orgChartId: number;
  orgChartName: string;
  orgChartDeleted: boolean;
  roleId: number;
  roleName: string;
  roleDeleted: boolean;
  competencyAssignments: CompetencyAssignment[]
  totalWeightage: number;
  compTags?: Record<number, string[]>;
}

export interface CompetencyAssignment {
  competencyId: number;
  competencyName?: string;
  deleted: boolean;
  weightage: number;
  competencyDescription?: string;
}

export interface RoleCompetencyCreation {
  roleId: number;
  competencyAssignment: CompetencyAssignment[];
}

