import { Competency } from "./competency.model";
import { JobScope } from "./jobScope.model";
import { OrgChart } from "./orgChart.model"
import { RoleCompetencyDto } from "./role-compotency.model";
import { Staff } from "./staff.model";

export interface Role {
    id?: number;
    name: string;
    description: string;
    visible: boolean;
    deleted: boolean;
    createdBy: string;
    createdAt: string;
    updatedBy: string;
    updatedAt: string;
    orgChart: OrgChart;
}

export interface RoleOverview {
    orgChartId: number;
    orgChartName: string;
    orgChartDeleted: boolean;
    roleId: number;
    roleName: string;
    description?: string;
    visible: boolean;
    assignedJobScopes?: JobScope[];
}

export interface RoleEdit {
    orgChartId: number;
    roleId: number;
    roleName: string;
    visibility: boolean;
    description?: string;
    jobScopeList?: string[];
}

export interface RoleCreation {
    orgChartId: number;
    roleName: string;
    visibility: boolean;
    description?: string;
    jobScopeList?: string[];
}

export interface RoleJobScopeMap {
    roleId: number;
    assignedJobScopes: JobScope[];
}

export interface RoleDetailsData {
    orgChartId: number;
    orgChartName: string;
    roleId: number;
    roleName: string;
    roleDescription?: string;
    visible: boolean;
    jobScopes?: JobScope[];
    competencies?: RoleCompetencyDto[];
    staffs?: Staff[];
    totalWeightage: number;
    compTags?: Record<number, string[]>;
}

export interface CreateRoleResponse {
    message: string;
    createdRole: Role;
}

export interface UpdateRoleResponse {
    message: string;
    updatedRole: Role;
}