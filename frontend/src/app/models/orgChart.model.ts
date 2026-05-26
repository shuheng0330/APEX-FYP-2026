import { Role } from "./role.model";
import { RoleDto, Staff } from "./staff.model";

export interface OrgChart {
  id: number;
  name: string;
  isRoot: boolean;
  deleted: boolean;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface OrgChartGraphDto {
  id: string;
  name?: string;
  data?: OrgChartGraphNodeDto;
  children?: OrgChartGraphDto[];
}

export interface OrgChartGraphNodeDto {
  type: 'D' | 'P';
  departmentNode?: OrgChartDepartmentNodeDto;
  personNode?: OrgChartPersonNodeDto;
}

export interface OrgChartDepartmentNodeDto {
  departmentName: string;
  roles?: Record<number, Role>;
  staffs?: Record<string, Staff>;
  roleStaffNumberMap?: Record<number, number>;
}

export interface OrgChartPersonNodeDto {
  staffId: string;
  departmentId: number;
  roleId: number;
  roleName: string;
  staffName: string;
  staffEmail: string;
  profileUrl: string;
}
