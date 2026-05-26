import { CareerPathway } from "./careerPathway.model";
import { Role } from "./role.model";

export interface Staff {
    id: string;
    name?: string;
    email: string;
    role?: Role;
    careerPathway?: CareerPathway;
    manager?: Staff;
    accountStatus: string;
    isDeleted: boolean;
}

export interface StaffOverviewData {
    staffId: string;
    staffName?: string;
    staffEmail: string;
    orgChartId?: number;
    orgChartName?: string;
    orgChartDeleted?: boolean;
    roleId?: number;
    roleName?: string;
    roleDeleted?: boolean;
    careerPathwayId?: number;
    careerPathwayName?: string;
    careerPathwayDeleted?: boolean;
    managerId?: string;
    managerName?: string;
    managerEmail?: string;
    managerDeleted?: boolean;
    accountStatus: boolean;
}

export interface StaffEdit {
    staffId: string;
    email: string;
    name?: string;
    roleId?: number;
    managerId?: string;
    careerPathwayId?: number;
    accountStatus: boolean;
}

export interface StaffCreation {
    email: string;
    name?: string;
    roleId?: number;
    careerPathwayId?: number;
    managerId?: string;
}

export interface AddStaffRequiredData {
    roles: RoleDto[];
    careerPathways: CareerPathwayDto[];
}

export interface RoleDto {
    orgChartId: number;
    orgChartName: string;
    roleId: number;
    roleName: string;
}

export interface CareerPathwayDto {
    orgChartId: number;
    orgChartName: string;
    careerPathwayId: number;
    careerPathwayName: string;
    childrenRoleIds?: number[];
}

export interface StaffProfile {
    staffId: string;
    staff: Staff;
    about?: string;
    contactNumber?: string;
    profilePicturePath?: string;
    createdBy?: string;
    createdAt: string;
    updatedBy?: string;
    updatedAt: string;
}

export interface StaffSelfDeclaredSkill {
    id: number;
    staff: Staff;
    skill: string;
    description?: string;
    proficiency: SkillProficiency;
    createdBy?: string;
    createdAt: string;
    updatedBy?: string;
    updatedAt: string;
}

export interface CreateStaffSelfDeclaredSkillRequest {
    id?: number;
    staffId: string;
    skill: string;
    description?: string;
    proficiency: string;
}

export enum SkillProficiency {
    BEGINNER = 'BEGINNER',
    INTERMEDIATE = 'INTERMEDIATE',
    ADVANCED = 'ADVANCED'
}

export interface StaffCert {
    id: number;
    staff: Staff;
    certName: string;
    fileName: string;
    description?: string;
    certPath: string;
    createdBy?: string;
    createdAt: string;
    updatedBy?: string;
    updatedAt: string;
}