import { Competency } from "./competency.model";
import { JobScope } from "./jobScope.model";
import { OrgChart } from "./orgChart.model"
import { RoleCompetencyDto } from "./role-compotency.model";
import { Staff } from "./staff.model";

export interface Role {
    employeeLevelId?: number | null;
    employeeLevelName?: string;
    employeeLevelCode?: string;
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
    employeeLevelId?: number | null;
    employeeLevelName?: string;
    employeeLevelCode?: string;
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
    employeeLevelId: number;
    orgChartId: number;
    roleId: number;
    roleName: string;
    visibility: boolean;
    description?: string;
    jobScopeList?: string[];
}

export interface RoleCreation {
    employeeLevelId: number;
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
    employeeLevelId?: number | null;
    employeeLevelName?: string;
    employeeLevelCode?: string;
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

export interface EmployeeLevel {
    id: number;
    code: string;
    name: string;
    displayOrder: number;
    defaultCompanyKpiWeight: number;
    defaultDepartmentKpiWeight: number;
    defaultIndividualKpiWeight: number;
}
